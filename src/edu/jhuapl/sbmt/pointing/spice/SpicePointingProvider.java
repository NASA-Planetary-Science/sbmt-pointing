package edu.jhuapl.sbmt.pointing.spice;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import edu.jhuapl.sbmt.pointing.InstrumentPointing;

import crucible.core.math.CrucibleMath;
import crucible.core.math.vectorspace.UnwritableVectorIJK;
import crucible.core.math.vectorspace.VectorIJK;
import crucible.core.mechanics.Coverage;
import crucible.core.mechanics.EphemerisID;
import crucible.core.mechanics.FrameID;
import crucible.core.mechanics.StateVector;
import crucible.core.mechanics.StateVectorFunction;
import crucible.core.mechanics.StateVectorFunctions;
import crucible.core.mechanics.providers.aberrated.AberratedEphemerisProvider;
import crucible.core.mechanics.providers.aberrated.AberratedStateVectorFunction;
import crucible.core.mechanics.providers.aberrated.AberrationCorrection;
import crucible.core.mechanics.utilities.SimpleEphemerisID;
import crucible.core.mechanics.utilities.SimpleFrameID;
import crucible.core.time.TSEpoch;
import crucible.core.time.TSRange;
import crucible.core.time.TimeSystems;
import crucible.core.time.UTCEpoch;
import crucible.crust.math.cones.Cones;
import crucible.crust.math.cones.PolygonalCone;
import crucible.mantle.spice.SpiceEnvironment;
import crucible.mantle.spice.SpiceEnvironmentBuilder;
import crucible.mantle.spice.adapters.AdapterInstantiationException;
import crucible.mantle.spice.kernel.KernelInstantiationException;
import crucible.mantle.spice.kernelpool.UnwritableKernelPool;
import nom.tam.fits.Fits;
import nom.tam.fits.HeaderCard;

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

    protected static final TimeSystems DefaultTimeSystems = TimeSystems.builder().build();

    public static class Builder
    {
        private final SpiceEnvironmentBuilder builder;
        private final TimeSystems timeSystems;
        private final FrameID centerFrameId;
        private final EphemerisID scId;
        private final FrameID scFrameId;

        protected Builder(SpiceEnvironmentBuilder builder, TimeSystems timeSystems, FrameID centerFrameId, EphemerisID scId, FrameID scFrameId)
        {
            super();

            this.builder = builder;
            this.timeSystems = timeSystems;
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
                public TimeSystems getTimeSystems()
                {
                    return timeSystems;
                }

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

        return new Builder(builder, DefaultTimeSystems, centerFrameId, scId, scFrameId);
    }

    protected SpicePointingProvider()
    {
        super();
    }

    public InstrumentPointing provide(FrameID instFrame, EphemerisID bodyId, TSEpoch time)
    {
        Preconditions.checkNotNull(instFrame);
        Preconditions.checkNotNull(time);

        int instCode = getKernelValue(Integer.class, "FRAME_" + instFrame.getName());

        // Convert specified time to spacecraft clock time.
        double tdb = getTimeSystems().getTDB().getTime(time);

        // Get the provider and all information needed to compute the pointing.
        AberratedEphemerisProvider ephProvider = getEphemerisProvider();
        FrameID bodyFrame = getCenterFrame();
        EphemerisID spacecraft = getScId();
        EphemerisID sun = getSunId();

        // Get objects that can perform the necessary computations for this
        // spacecraft and body (and sun position).
        AberratedStateVectorFunction bodyFromSc = ephProvider.createAberratedStateVectorFunction(bodyId, spacecraft, bodyFrame, Coverage.ALL_TIME, AberrationCorrection.LT_S);
        AberratedStateVectorFunction sunFromBody = ephProvider.createAberratedStateVectorFunction(sun, bodyId, bodyFrame, Coverage.ALL_TIME, AberrationCorrection.LT_S);

        // Need spacecraft-from-body as well as the body-from-spacecraft frame
        // calculations.
        StateVectorFunction scFromBody = StateVectorFunctions.negate(bodyFromSc);

        // Get position of body relative to spacecraft.
        StateVector bodyFromScState = scFromBody.getState(tdb);

        // Get sun position relative to body at the time the light left the
        // body, not the time the light arrived at the spacecraft.
        double timeLightLeftBody = tdb - bodyFromSc.getLightTime(tdb);
        StateVector sunFromBodyState = sunFromBody.getState(timeLightLeftBody);

        // TODO: need to find out the frame for FOV values and handle any
        // transformations appropriately.
        // For now, assume boresight and frustum are defined in the instrument
        // frame.

        UnwritableVectorIJK boresight = getBoresight(instCode);

        PolygonalCone frustum = getFrustum(instFrame, instCode, boresight);

        // Extract the corners from the frustum and re-order them to match SBMT.
        // This mapping is based on getFov.c, a function from the previous C/C++
        // INFO file generating code. Its comments state:
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
        // However, this may not be general. The SPICE documentation states that
        // polygon-shaped FOV corners are returned either in clockwise or
        // counterclockwise order. There appears to be no way of telling which
        // is the case from a SPICE kernel. For that matter, there's no
        // guarantee which quadrant has the first corner.
        List<UnwritableVectorIJK> corners = frustum.getCorners();
        corners = ImmutableList.of(corners.get(1), corners.get(0), corners.get(2), corners.get(3));

        UnwritableVectorIJK vertex = frustum.getVertex();
        UnwritableVectorIJK upDir = VectorIJK.cross(boresight, VectorIJK.cross(vertex, boresight));

        return new InstrumentPointing(bodyFromScState.getPosition(), sunFromBodyState.getPosition(), boresight, upDir, corners, new TSRange(time, time));
    }

    public abstract TimeSystems getTimeSystems();

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

    public static UTCEpoch getUTC(String utcString) throws ParseException
    {
        Preconditions.checkNotNull(utcString);

        UTCEpoch result;

        String[] fields = utcString.split("[^\\d\\.]");
        if (fields.length == 5 || fields.length == 6)
        {
            // UTC fields separated by non-numeric characters, either
            // yyyy-doy... or yyyy-mm-dd.
            // Parse all but the last field as integers.
            List<Integer> integers = new ArrayList<>();
            for (int index = 0; index < fields.length - 1; ++index)
            {
                integers.add(Integer.parseInt(fields[index]));
            }

            // Parse the last field as a double (seconds).
            double sec = 0.;
            if (fields.length > integers.size())
            {
                sec = Double.parseDouble(fields[integers.size()]);
            }

            if (integers.size() == 4)
            {
                result = new UTCEpoch(integers.get(0), integers.get(1), integers.get(2), integers.get(3), sec);
            }
            else if (integers.size() == 5)
            {
                result = new UTCEpoch(integers.get(0), integers.get(1), integers.get(2), integers.get(3), integers.get(4), sec);
            }
            else
            {
                throw new AssertionError("fields was either 5 or 6 elements, so array must be 4 or 5");
            }
        }
        else if (utcString.length() == 13)
        {
            // yyyydddhhmmss
            int y = Integer.parseInt(utcString.substring(0, 4));
            int doy = Integer.parseInt(utcString.substring(4, 7));
            int hr = Integer.parseInt(utcString.substring(7, 9));
            int mn = Integer.parseInt(utcString.substring(9, 11));
            int s = Integer.parseInt(utcString.substring(11, 13));
            result = new UTCEpoch(y, doy, hr, mn, s);
        }
        else if (utcString.length() == 14)
        {
            // yyyymmddhhmmss
            int y = Integer.parseInt(utcString.substring(0, 4));
            int m = Integer.parseInt(utcString.substring(4, 6));
            int d = Integer.parseInt(utcString.substring(6, 8));
            int h = Integer.parseInt(utcString.substring(8, 10));
            int mn = Integer.parseInt(utcString.substring(10, 12));
            int s = Integer.parseInt(utcString.substring(12, 14));
            result = new UTCEpoch(y, m, d, h, mn, s);
        }
        else
        {
            throw new ParseException("Can't parse string as time: " + utcString, 0);
        }

        return result;
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

    public static void main(String[] args)
    {
        try
        {
            String utcString;
            try (Fits fits = new Fits("/Users/peachjm1/Downloads/impact001.fits"))
            {

                HeaderCard utcCard = fits.getHDU(0).getHeader().findCard("COR_UTC");
                utcString = utcCard.getValue();
            }

            UTCEpoch utcEpoch = SpicePointingProvider.getUTC(utcString);
            System.out.println("UTC epoch is " + utcEpoch);

            Path userHome = Paths.get(System.getProperty("user.home"));
            List<Path> mkPaths = ImmutableList.of( //
                    userHome.resolve("dart/SPICE/generic/mk/generic.mk"), //
                    userHome.resolve("dart/SPICE/dra/mk/dra_1.mk"), //
                    userHome.resolve("dart/SPICE/dra/mk/dra_2.mk"), //
                    userHome.resolve("dart/SPICE/dra/mk/dra_3.mk") //
            );

            String bodyName = "DIDYMOS";
            String scName = "DART_SPACECRAFT";

            String centerFrameName = "DIDYMOS_SYSTEM_BARYCENTER";
            String scFrameName = "DART_SPACECRAFT";
            String instFrameName = "DART_DRACO";

            SpicePointingProvider.Builder builder = SpicePointingProvider.builder(mkPaths, centerFrameName, scName, scFrameName);

            EphemerisID bodyId = builder.bindEphemeris(bodyName);

            FrameID instFrame = builder.bindFrame(instFrameName);

            SpicePointingProvider provider = builder.build();

            System.err.println(provider.provide(instFrame, bodyId, DefaultTimeSystems.getUTC().getTSEpoch(utcEpoch)));
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

    }
}
