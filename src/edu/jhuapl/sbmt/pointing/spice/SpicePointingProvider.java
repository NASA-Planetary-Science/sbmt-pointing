package edu.jhuapl.sbmt.pointing.spice;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import crucible.core.math.CrucibleMath;
import crucible.core.math.vectorspace.UnwritableVectorIJK;
import crucible.core.math.vectorspace.VectorIJK;
import crucible.core.mechanics.EphemerisID;
import crucible.core.mechanics.FrameID;
import crucible.core.mechanics.providers.aberrated.AberratedEphemerisProvider;
import crucible.core.mechanics.utilities.SimpleEphemerisID;
import crucible.core.mechanics.utilities.SimpleFrameID;
import crucible.crust.math.cones.Cones;
import crucible.crust.math.cones.PolygonalCone;
import crucible.mantle.spice.SpiceEnvironment;
import crucible.mantle.spice.SpiceEnvironmentBuilder;
import crucible.mantle.spice.adapters.AdapterInstantiationException;
import crucible.mantle.spice.kernel.KernelInstantiationException;
import crucible.mantle.spice.kernelpool.UnwritableKernelPool;

/**
 * Implementation of {@link PointingProvider} that extracts pointing information
 * directly from SPICE kernels/metakernels.
 *
 * @author James Peachey
 */
public abstract class SpicePointingProvider
{
    private static final Map<String, EphemerisID> EphemerisIds = new HashMap<>();
    private static final Map<String, FrameID> FrameIds = new HashMap<>();

    public static EphemerisID getEphemerisId(String name)
    {
        Preconditions.checkNotNull(name);

        EphemerisID result = EphemerisIds.get(name);
        if (result == null)
        {
            result = new SimpleEphemerisID(name);
            EphemerisIds.put(name, result);
        }

        return result;
    }

    public static FrameID getFrameId(String name)
    {
        Preconditions.checkNotNull(name);

        FrameID result = FrameIds.get(name);
        if (result == null)
        {
            result = new SimpleFrameID(name);
            FrameIds.put(name, result);
        }

        return result;
    }

    public static final EphemerisID EarthEphemerisId = getEphemerisId("EARTH");
    public static final EphemerisID SunEphemerisId = getEphemerisId("SUN");
    public static final FrameID J2000 = getFrameId("J2000");

    public static class Builder
    {
        private final SpiceEnvironmentBuilder builder;
        private final FrameID centerFrameId;
        private final EphemerisID scId;
        private final FrameID scFrameId;

        protected Builder(SpiceEnvironmentBuilder builder, FrameID centerFrameId, EphemerisID scId, FrameID scFrameId)
        {
            super();

            this.builder = builder;
            this.centerFrameId = centerFrameId;
            this.scId = scId;
            this.scFrameId = scFrameId;
        }

        public EphemerisID bindEphemeris(String name)
        {
            EphemerisID result = getEphemerisId(name);

            builder.bindEphemerisID(name, result);

            return result;
        }

        public FrameID bindFrame(String name)
        {
            FrameID result = getFrameId(name);
            builder.bindFrameID(name, result);

            return result;
        }

        public SpiceEnvironmentBuilder getSpiceEnvBuilder()
        {
            return builder;
        }

        public SpicePointingProvider build() throws AdapterInstantiationException
        {
            SpiceEnvironment spiceEnv = builder.build();

            AberratedEphemerisProvider ephProvider = spiceEnv.createSingleAberratedProvider();

            UnwritableKernelPool kernelPool = spiceEnv.getPool();

            return new SpicePointingProvider() {

                @Override
                public AberratedEphemerisProvider getEphemerisProvider()
                {
                    return ephProvider;
                }

                @Override
                public UnwritableKernelPool getKernelPool()
                {
                    return kernelPool;
                }

                @Override
                public FrameID getCenterFrame()
                {
                    return centerFrameId;
                }

                @Override
                public EphemerisID getScId()
                {
                    return scId;
                }

                @Override
                public FrameID getScFrame()
                {
                    return scFrameId;
                }

            };
        }
    }

    public static Builder builder(Iterable<Path> mkPaths, String centerFrameName, String scName, String scFrameName) throws KernelInstantiationException, IOException
    {
        Preconditions.checkNotNull(mkPaths);

        // Start by creating a builder and adding all the kernels referenced in
        // the metakernels.
        SpiceEnvironmentBuilder builder = new SpiceEnvironmentBuilder();
        for (Path path : mkPaths)
        {
            loadAllKernels(builder, path);
        }

        // Bind frame center.
        FrameID centerFrameId = getFrameId(centerFrameName);
        builder.bindFrameID(centerFrameName, centerFrameId);

        // Bind spacecraft ephemeris and frame.
        EphemerisID scId = getEphemerisId(scName);
        builder.bindEphemerisID(scName, scId);

        FrameID scFrameId = getFrameId(scFrameName);
        builder.bindFrameID(scFrameName, scFrameId);

        // Bind common celestial ephemerides.
        builder.bindEphemerisID(EarthEphemerisId.getName(), EarthEphemerisId);
        builder.bindEphemerisID(SunEphemerisId.getName(), SunEphemerisId);

        return new Builder(builder, centerFrameId, scId, scFrameId);
    }

    protected SpicePointingProvider()
    {
        super();
    }

    public SpiceInstrumentPointing provide(FrameID instFrame, EphemerisID bodyId, double time)
    {
        Preconditions.checkNotNull(instFrame);
        Preconditions.checkNotNull(time);

        int instCode = getKernelValue(Integer.class, "FRAME_" + instFrame.getName());

        // Get the provider and all information needed to compute the pointing.
        AberratedEphemerisProvider ephProvider = getEphemerisProvider();
        FrameID bodyFrame = getCenterFrame();
        EphemerisID spacecraft = getScId();

        // TODO: need to find out the frame for FOV values and handle any
        // transformations appropriately.
        // For now, assume boresight and frustum are defined in the instrument
        // frame.

        UnwritableVectorIJK boresight = getBoresight(instCode);

        PolygonalCone frustum = getFrustum(instFrame, instCode, boresight);

        // This is based on getFov.c, a function from the predecessor C/C++ INFO
        // file generating code. Its comments state:
        //
        // @formatter:off
        //swap the boundary corner vectors so they are in the correct order for SBMT
        //getfov returns them in the following order (quadrants): I, II, III, IV.
        //SBMT expects them in the following order (quadrants): II, I, III, IV.
        //So the vector index mapping is
        //SBMT   SPICE
        //  0       1
        //  1       0
        //  2       2
        //  3       3
        // @formatter:on
        //
        // Tried this, but discovered PolygonalCone must pick a different order.
        // To give the same results as the C/C++ code, going with this mapping,
        // which was determined by trial and error:
        // @formatter:off
        // SBMT   crucible/PolygonalCone
        //  0       0
        //  1       1
        //  2       3
        //  3       2
        // @formatter:on

        List<UnwritableVectorIJK> corners = frustum.getCorners();
        corners = ImmutableList.of(corners.get(0), corners.get(1), corners.get(3), corners.get(2));

        UnwritableVectorIJK vertex = frustum.getVertex();
        UnwritableVectorIJK upDir = VectorIJK.cross(boresight, VectorIJK.cross(vertex, boresight));

        return new SpiceInstrumentPointing(ephProvider, spacecraft, instFrame, bodyId, bodyFrame, boresight, upDir, corners, time);
    }

    public abstract AberratedEphemerisProvider getEphemerisProvider();

    public abstract UnwritableKernelPool getKernelPool();

    public abstract FrameID getCenterFrame();

    public abstract EphemerisID getScId();

    public abstract FrameID getScFrame();

    protected UnwritableVectorIJK getBoresight(Integer instCode)
    {
        return toVector(getKernelValues(Double.class, "INS" + instCode + "_BORESIGHT", 3));
    }

    /**
     * As described at
     * https://naif.jpl.nasa.gov/pub/naif/toolkit_docs/C/cspice/getfov_c.html
     *
     * @param instFrame TODO
     * @param instCode
     *
     * @return
     */
    protected PolygonalCone getFrustum(FrameID instFrame, int instCode, UnwritableVectorIJK boresight)
    {
        final String instPrefix = "INS" + instCode + "_";

        String shape = getKernelValue(String.class, instPrefix + "FOV_SHAPE");

        String classSpec = getKernelValue(String.class, instPrefix + "FOV_CLASS_SPEC", false);

        boolean corners = classSpec != null ? classSpec.equals("CORNERS") : true;

        PolygonalCone result;
        if (corners)
        {
            Preconditions.checkArgument(shape.equals("RECTANGLE"), "Unsupported FOV shape " + shape + " for instrument frame " + instFrame.getName());

            throw new UnsupportedOperationException("TODO code this case up");
        }
        else if (classSpec.equals("ANGLES"))
        {
            UnwritableVectorIJK refVector = toVector(getKernelValues(Double.class, instPrefix + "FOV_REF_VECTOR", 3));
            double refAngle = getKernelValue(Double.class, instPrefix + "FOV_REF_ANGLE");
            double crossAngle = getKernelValue(Double.class, instPrefix + "FOV_CROSS_ANGLE");

            // TODO also need to read/check units, convert as needed.
            refAngle *= CrucibleMath.PI / 180.;
            crossAngle *= CrucibleMath.PI / 180.;

            result = Cones.createRectangularCone(refVector, boresight, crossAngle, refAngle);
        }
        else
        {
            throw new IllegalArgumentException( //
                    "Illegal value in SPICE kernel; FOV_CLASS_SPEC must be either \"CORNERS\" or \"ANGLES\" for instrument frame " + //
                            instFrame.getName());
        }

        return result;
    }

    protected <T> T getKernelValue(Class<T> valueType, String keyName)
    {
        return getKernelValue(valueType, keyName, true);
    }

    protected <T> T getKernelValue(Class<T> valueType, String keyName, boolean errorIfNull)
    {
        return valueType.cast(getKernelValues(valueType, keyName, 1, errorIfNull).get(0));
    }

    protected <E> List<E> getKernelValues(Class<?> valueType, String keyName, int expectedSize)
    {
        return getKernelValues(valueType, keyName, expectedSize, true);
    }

    /**
     *
     * @param <E>
     * @param valueType
     * @param keyName
     * @param expectedSize checked only if values are found
     * @param errorIfNull throw exception if value is null
     * @return the values; will be null iff values are missing or values have
     *         the wrong type
     */
    protected <E> List<E> getKernelValues(Class<?> valueType, String keyName, int expectedSize, boolean errorIfNull)
    {
        List<?> list;
        if (Double.class == valueType)
        {
            list = getKernelPool().getDoubles(keyName);
        }
        else if (Integer.class == valueType)
        {
            list = getKernelPool().getIntegers(keyName);
        }
        else if (String.class == valueType)
        {
            list = getKernelPool().getStrings(keyName);
        }
        else
        {
            throw new AssertionError("Cannot get invalid kernel value type " + valueType + " (key was " + keyName + ")");
        }

        if (list == null && errorIfNull)
        {
            if (getKernelPool().getKeywords().contains(keyName))
            {
                throw new IllegalArgumentException("SPICE kernel does not have values of type " + valueType + " for key " + keyName);
            }
            else
            {
                throw new IllegalArgumentException("SPICE kernel is missing values for key " + keyName);
            }
        }
        else if (list.size() != expectedSize)
        {
            throw new IllegalArgumentException("SPICE kernel has " + list.size() + " kernel values, not expected number " + expectedSize + " for key " + keyName);
        }

        // Unchecked cast is safe; if list doesn't hold what it should an
        // exception would have been thrown above.
        @SuppressWarnings("unchecked")
        List<E> result = (List<E>) list;

        return result;
    }

    protected UnwritableVectorIJK toVector(List<Double> tmpList)
    {
        return new UnwritableVectorIJK(tmpList.get(0), tmpList.get(1), tmpList.get(2));
    }

    protected static EphemerisID getSunId()
    {
        return SunEphemerisId;
    }

    public static void loadAllKernels(SpiceEnvironmentBuilder builder, Path path) throws KernelInstantiationException, IOException
    {
        KernelProviderFromLocalMetakernel kernelProvider = new KernelProviderFromLocalMetakernel(path);
        List<File> kernels = kernelProvider.get();

        for (File kernel : kernels)
        {
            builder.load(kernel.getName(), kernel);
        }

    }

}
