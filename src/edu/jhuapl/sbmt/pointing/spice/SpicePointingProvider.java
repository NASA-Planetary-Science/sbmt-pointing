package edu.jhuapl.sbmt.pointing.spice;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.tuple.Triple;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import edu.jhuapl.sbmt.pointing.InstrumentPointing;
import edu.jhuapl.sbmt.pointing.IPointingProvider;

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
 * Provider of {@link InstrumentPointing} vectors from SPICE kernels. Each
 * provider is tied to a specific combination of target-and-spacecraft. One
 * {@link SpicePointingProvider} can be used for multiple instruments on the
 * same spacecraft (see the {@link #provide(String, FrameID, double)} method. FOV
 * quantities are extracted in a manner consistent with the function of NAIF's
 * getFov_c method, as described at:
 * <p>
 * https://naif.jpl.nasa.gov/pub/naif/toolkit_docs/C/cspice/getfov_c.html
 * <p>
 * A {@link Builder} class is provided for ease of creation. In addition, both
 * this class and the builder are designed to be extensible.
 *
 * @author James Peachey
 */
public abstract class SpicePointingProvider implements IPointingProvider
{
    private static final Map<String, EphemerisID> EphemerisIds = new HashMap<>();
    private static final Map<String, FrameID> FrameIds = new HashMap<>();
    private static final Map<Triple<Double, FrameID, FrameID>, SpiceInstrumentPointing> previousPointings = new HashMap<Triple<Double, FrameID, FrameID>, SpiceInstrumentPointing>();
    private String currentInstName;

    /**
     * Utility method for obtaining an {@link EphemerisID} for the specified
     * spacecraft/body. Presumably the name will be
     * NAIF-assigned/NAIF-compliant, but the only real requirement is that the
     * loaded kernels be aware of and have the necessary data associated with
     * this name.
     *
     * @param name spacecraft/body ID string
     * @return the {@link EphemerisID}
     */
    public static EphemerisID getEphemerisId(String name)
    {
        Preconditions.checkNotNull(name);

        EphemerisID result = EphemerisIds.get(name);
        if (result == null)
        {
            // TODO see if the name can retrieve a CelestialBodies ephemeris id
            result = new SimpleEphemerisID(name);
            EphemerisIds.put(name, result);
        }

        return result;
    }

    /**
     * Utility method for obtaining a {@link FrameID} for the specified
     * spacecraft/body frame. Presumably the frame name will be
     * NAIF-assigned/NAIF-compliant, but the only real requirement is that the
     * loaded kernels be aware of this frame and have the neceessary data
     * loaded.
     *
     * @param name spacecraft/body frame ID string
     * @return the {@link FrameID}
     */
    public static FrameID getFrameId(String name)
    {
        Preconditions.checkNotNull(name);

        FrameID result = FrameIds.get(name);
        if (result == null)
        {
            // TODO see if the name can retrieve a CelestialFrames frame id
            result = new SimpleFrameID(name);
            FrameIds.put(name, result);
        }

        return result;
    }

    /**
     * Builder for outer class {@link SpicePointingProvider}. Must have a
     * {@link SpiceEnvironmentBuilder} and the necessary target/spacecraft
     * {@link EphemerisID} and {@link FrameID}s. Other {@link EphemerisID}s and
     * {@link FrameID}s may be provided prior to building.
     * <p>
     * This class is essentially just a wrapper for
     * {@link SpiceEnvironmentBuilder}. It is mainly provided to simplify the
     * most common use cases, so that callers may use it without
     * knowing/understanding much about {@link crucible.mantle.spice}
     * constructs. For users that do know those details and need to call other
     * methods, there is a getter for the underlying
     * {@link SpiceEnvironmentBuilder}.
     */
    public static class Builder
    {
        private final SpiceEnvironmentBuilder builder;
        private final EphemerisID targetId;
        private final FrameID targetFrame;
        private final EphemerisID scId;
        private final FrameID scFrame;
        private final Set<String> instrumentNames;

        protected Builder(SpiceEnvironmentBuilder builder, EphemerisID targetId, FrameID targetFrame, EphemerisID scId, FrameID scFrame)
        {
            super();

            this.builder = builder;
            this.targetId = targetId;
            this.targetFrame = targetFrame;
            this.scId = scId;
            this.scFrame = scFrame;
            this.instrumentNames = new LinkedHashSet<>();
            FrameIds.clear();
            EphemerisIds.clear();
        }

        /**
         * Bind the given body name in the SPICE environment. Users must call
         * this for any/all bodies (other than the target body and the
         * spacecraft) for which they will request position information.
         * <p>
         * This deviates from the frequent Builder-pattern convention by
         * returning the {@link EphemerisID} rather than a reference to the
         * builder object for chaining calls to {@link Builder} methods.
         *
         * @param name the name to bind
         * @return the {@link EphemerisID} that may be used for future
         *         references to this body
         */
        public EphemerisID bindEphemeris(String name)
        {
            EphemerisID result = getEphemerisId(name);
            builder.bindEphemerisID(name, result);

            return result;
        }

        /**
         * Bind the given frame identifier in the SPICE environment. Users must
         * call this for any/all frames (other than the target and spacecraft
         * frames) for which they will request position information.
         * <p>
         * This deviates from the frequent Builder-pattern convention by
         * returning the {@link FrameID} rather than a reference to the builder
         * object for chaining calls to {@link Builder} methods.
         *
         * @param name the name to bind
         * @return the {@link FrameID} that may be used for future references to
         *         this frame
         */
        public FrameID bindFrame(String name)
        {
            FrameID result = getFrameId(name);
            builder.bindFrameID(name, result);

            return result;
        }

        /**
         * Find the frame associated with the specified instrument name, and
         * bind that frame in the SPICE environment when the provider is built.
         *
         * @param instrumentName the name of the instrument whose frame to bind
         * @return the builder
         */
        public Builder addInstrumentFrame(String instrumentName)
        {
            // Can't actually look up the instrument frame name yet, so just
            // keep track of this instrument name. We'll look up and bind the
            // frame at build time.
            this.instrumentNames.add(instrumentName);
            return this;
        }

        /**
         * Return this {@link Builder}'s underlying
         * {@link SpiceEnvironmentBuilder}, which may be used prior to calling
         * {@link #build()} to customize the {@link SpiceEnvironment} the
         * {@link SpicePointingProvider} will use.
         *
         * @return the {@link SpiceEnvironmentBuilder}
         */
        public SpiceEnvironmentBuilder getSpiceEnvBuilder()
        {
            return builder;
        }

        /**
         * Use the underlying {@link SpiceEnvironmentBuilder} to create a
         * {@link SpiceEnvironment} and, in turn a single-iteration
         * {@link AberratedEphemerisProvider}. From that, create and return a
         * {@link SpicePointingProvider} that can furnish
         * {@link InstrumentPointing}s for the appropriate
         * spacecraft/target-body and associated instruments.
         *
         * @return the {@link SpicePointingProvider}
         * @throws AdapterInstantiationException if the {@link SpiceEnvironment}
         *             throws it
         */
        public SpicePointingProvider build() throws AdapterInstantiationException
        {
            Map<String, Integer> instrumentNameToInstrumentIdMap = new LinkedHashMap<>();
            Map<String, FrameID> instrumentNameToFrameIdMap = new LinkedHashMap<>();

            if (!instrumentNames.isEmpty())
            {
                // Build a temporary spice environment that will be used just to
                // find the instrument frame identifiers.
                SpiceEnvironment spiceEnv = builder.build();
                UnwritableKernelPool kernelPool = spiceEnv.getPool();

                // Get information on all instruments.
                extractInstrumentInfo(kernelPool, instrumentNameToInstrumentIdMap, instrumentNameToFrameIdMap);

                // Add frame identifiers for the requested instruments.
                for (String name : instrumentNames)
                {
                    FrameID frame = instrumentNameToFrameIdMap.get(name);
                    Preconditions.checkState(frame != null, "SPICE kernels do not include a frame for instrument " + name);

                    builder.bindFrameID(frame.getName(), frame);
                }

            }

            String[] instrumentNames = new String[this.instrumentNames.size()];
            this.instrumentNames.toArray(instrumentNames);

            // Now build a (possibly updated to include instrument frames) spice
            // environment.
            SpiceEnvironment spiceEnv = builder.build();

            UnwritableKernelPool kernelPool = spiceEnv.getPool();

            AberratedEphemerisProvider ephProvider = spiceEnv.createSingleAberratedProvider();

            SpicePointingProvider provider = new SpicePointingProvider() {

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
                public EphemerisID getTargetId()
                {
                    return targetId;
                }

                @Override
                public FrameID getTargetFrame()
                {
                    return targetFrame;
                }

                @Override
                public EphemerisID getScId()
                {
                    return scId;
                }

                @Override
                public FrameID getScFrameId()
                {
                    return scFrame;
                }

                @Override
                public String[] getInstrumentNames()
                {
                    // Return a defensive copy since arrays are mutable.
                    return instrumentNames.clone();
                }

                @Override
                protected int getInstrumentIdForInstrument(String instrumentName)
                {
                    Integer id = instrumentNameToInstrumentIdMap.get(instrumentName);
                    if (id == null)
                    {
                        throw new IllegalArgumentException("Cannot get identifier code for instrument named " + instrumentName);
                    }
                    return id.intValue();
                }

                @Override
                protected FrameID getFrameIdForInstrument(String instrumentName)
                {
                    return instrumentNameToFrameIdMap.get(instrumentName);
                }

            };

            if (instrumentNames.length > 0)
            {
                provider.setCurrentInstrumentName(instrumentNames[0]);
            }

            return provider;
        }

        protected void extractInstrumentInfo(UnwritableKernelPool kp, Map<String, Integer> nameToInstrumentIdMap, Map<String, FrameID> nameToFrameIdMap)
        {
            for (String k : kp.getKeywords())
            {
                String instrumentIdString = k.replaceFirst("^INS(.*)_NAME$", "$1");
                if (k.length() != instrumentIdString.length())
                {
                    // A match was made, so parse the integer ID code.
                    int instrumentId = Integer.parseInt(instrumentIdString);
                    List<String> matches = kp.getStrings(k);
                    if (matches == null || matches.size() != 1)
                    {
                        throw new IllegalArgumentException("Kernel pool does not have a unique instrument name associated with the keyword " + k);
                    }
                    String instrumentName = matches.get(0);
                    nameToInstrumentIdMap.put(instrumentName, Integer.valueOf(instrumentId));

                    // Also parse the frame name.
                    matches = kp.getStrings(String.format("INS%d_FOV_FRAME", instrumentId));
                    if (matches == null || matches.size() != 1)
                    {
                        throw new IllegalArgumentException("Kernel pool does not have an instrument frame name associated with the instrument ID " + instrumentId);
                    }
                    FrameID frameId = SpicePointingProvider.getFrameId(matches.get(0));
                    nameToFrameIdMap.put(instrumentName, frameId);
                }
            }
        }
    }

    /**
     * Create a {@link Builder} using the specified metakernel file paths to
     * create the {@link SpiceEnvironmentBuilder}, and the provided
     * body-and-frame identifiers to prepare the {@link SpicePointingProvider}.
     *
     * @param mkPaths collection of full paths to the metakernel files to load
     * @param targetName the target body that will be used as the origin for all
     *            pointing information
     * @param targetFrameName the target frame in which pointing information
     *            will be computed
     * @param scName the spacecraft whose pointing information will be provided
     * @param scFrameName the spacecraft frame whose pointing information will
     *            be provided
     * @return the {@link Builder}
     * @throws KernelInstantiationException if problems occur using the
     *             metakernels to create the {@link SpiceEnvironmentBuilder}
     * @throws IOException if an IOException is thrown while accessing the
     *             metakernel files
     */
    public static Builder builder(Iterable<Path> mkPaths, String targetName, String targetFrameName, String scName, String scFrameName) throws KernelInstantiationException, IOException
    {
        Preconditions.checkNotNull(mkPaths);

        // Start by creating a builder and adding all the kernels referenced in
        // the metakernels.
        SpiceEnvironmentBuilder builder = new SpiceEnvironmentBuilder();
        builder.setIgnoreFaultyFrames(true);
        for (Path path : mkPaths)
        {
            loadAllKernels(builder, path);
        }

        // Bind target body ephemeris and frame.
        EphemerisID targetId = getEphemerisId(targetName);
        builder.bindEphemerisID(targetName, targetId);

        FrameID targetFrame = getFrameId(targetFrameName);
        builder.bindFrameID(targetFrameName, targetFrame);

        // Bind spacecraft ephemeris and frame.
        EphemerisID scId = getEphemerisId(scName);
        builder.bindEphemerisID(scName, scId);

        FrameID scFrame = getFrameId(scFrameName);
        builder.bindFrameID(scFrameName, scFrame);
        return new Builder(builder, targetId, targetFrame, scId, scFrame);
    }

    protected SpicePointingProvider()
    {
        super();
    }

    @Override
    public InstrumentPointing provide(String instrumentName, double time)
    {
        Preconditions.checkNotNull(instrumentName, "Instrument name must be defined");

        FrameID frameId = getFrameIdForInstrument(instrumentName);
        Preconditions.checkArgument(frameId != null, "Cannot find a SPICE frame identifier for instrument name " + instrumentName);

        return provide(instrumentName, frameId, time);
    }

    /**
     * Provide an {@link InstrumentPointing} for the specified instrument and
     * moment in time.
     * @param instrumentName the name of the instrument
     * @param instrumentFrame the instrument whose pointing to compute in the
     *            body-fixed frame. The {@link FrameID} may be obtained from the
     *            {@link Builder}, or by using the {@link #getFrameId(String)}
     *            method.
     * @param time the Epoch Time at which to compute the pointing
     *
     * @return the {@link InstrumentPointing}
     */
    protected InstrumentPointing provide(String instrumentName, FrameID instrumentFrame, double time)
    {
        Preconditions.checkNotNull(instrumentFrame);
        if (previousPointings.get(Triple.of(time, instrumentFrame, getTargetFrame())) != null) return previousPointings.get(Triple.of(time, instrumentFrame, getTargetFrame()));

        int instCode = getInstrumentIdForInstrument(instrumentName);

        // Get the provider and all information needed to compute the pointing.
        AberratedEphemerisProvider ephProvider = getEphemerisProvider();

        EphemerisID targetId = getTargetId();
        FrameID targetFrame = getTargetFrame();

        EphemerisID scId = getScId();
        FrameID scFrame = getScFrameId();

        // Get FOV quantities in the instrument frame.
        UnwritableVectorIJK boresight = getBoresight(instCode);

        PolygonalCone frustum = getFrustum(instrumentFrame, instCode, boresight);

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
//        Logger.getAnonymousLogger().log(Level.INFO, "Returning pointing " + instrumentFrame + " at time " + TimeUtil.et2str(time));
        SpiceInstrumentPointing pointing = new SpiceInstrumentPointing(ephProvider, targetId, targetFrame, scId, scFrame, instrumentFrame, boresight, upDir, corners, time);
        previousPointings.put(Triple.of(time, instrumentFrame, targetFrame), pointing);
        return pointing;
    }

    /**
     * Return the {@link AberratedEphemerisProvider} used by this
     * {@link SpicePointingProvider}.
     *
     * @return the ephemeris provider
     */
    public abstract AberratedEphemerisProvider getEphemerisProvider();

    /**
     * Return the {@link UnwritableKernelPool} that may be used to obtain SPICE
     * information other than state/vector transforms.
     *
     * @return the kernel pool
     */
    public abstract UnwritableKernelPool getKernelPool();

    /**
     * Return the {@link EphemerisID} of the target, that is the center of the
     * body-fixed coordinates.
     *
     * @return the target ephemeris identifier
     */
    public abstract EphemerisID getTargetId();

    /**
     * Return the {@link FrameID} of the target frame, which is the frame in
     * which all pointing information is computed.
     *
     * @return the target frame identifier
     */
    public abstract FrameID getTargetFrame();

    /**
     * Return the {@link EphemerisID} of the spacecraft, whose pointing
     * information will be returned by the provider.
     *
     * @return the spacecraft identifier
     */
    public abstract EphemerisID getScId();

    /**
     * Return the {@link FrameID} of the spacecraft frame, used to compute the
     * pointing in the body-fixed target frame.
     *
     * @return the spacecraft frame identifier
     */
    public abstract FrameID getScFrameId();

    protected abstract int getInstrumentIdForInstrument(String instrumentName);

    protected abstract FrameID getFrameIdForInstrument(String instrumentName);

    /**
     * Return the boresight vector for the specified instrument code, in the
     * instrument frame. This is a time-independent quantity pulled from the
     * kernel pool.
     *
     * @param instCode the integer code identifying the instrument
     * @return the boresight vector
     */
    protected UnwritableVectorIJK getBoresight(Integer instCode)
    {
        return toVector(getKernelValues(Double.class, "INS" + instCode + "_BORESIGHT", 3));
    }

    /**
     * Return the frustum for the specified instrument integer code and frame
     * id, based on the boresight vector
     *
     * @return the frustum as a {@link PolygonalCone}
     */
    protected PolygonalCone getFrustum(FrameID instFrame, int instCode, UnwritableVectorIJK boresight)
    {
        final String instPrefix = "INS" + instCode + "_";

        String shape = getKernelValue(String.class, instPrefix + "FOV_SHAPE");

        String classSpec = getKernelValue(String.class, instPrefix + "FOV_CLASS_SPEC", true);

        boolean corners = classSpec != null ? classSpec.equals("CORNERS") : true;

        PolygonalCone result;
        if (corners)
        {
            Preconditions.checkArgument(shape.equals("RECTANGLE"), "Unsupported FOV shape " + shape + " for instrument frame " + instFrame.getName());

            throw new UnsupportedOperationException("TODO code up this case");
        }
        else if (classSpec.equals("ANGLES"))
        {
            UnwritableVectorIJK refVector = toVector(getKernelValues(Double.class, instPrefix + "FOV_REF_VECTOR", 3));
            double refAngle = getKernelValue(Double.class, instPrefix + "FOV_REF_ANGLE");
            double crossAngle = refAngle;
            if (getKernelPool().getStrings(instPrefix + "FOV_CROSS_ANGLE") != null)
            	crossAngle = getKernelValue(Double.class, instPrefix + "FOV_CROSS_ANGLE");

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

    /**
     * Get the value associated with the specified key from the kernel pool.
     * Throw exception if the value returned is null.
     *
     * @param <T> type of value returned
     * @param valueType type of value returned
     * @param keyName name (key) of the value
     * @return the associated value
     */
    protected <T> T getKernelValue(Class<T> valueType, String keyName)
    {
        return getKernelValue(valueType, keyName, true);
    }

    /**
     * Get the value associated with the specified key from the kernel pool.
     * Whether an exception is thrown for null values is controlled with the
     * erorIfNull parameter.
     *
     * @param <T> type of value returned
     * @param valueType type of value returned
     * @param keyName name (key) of the value
     * @param errorIfNull if true, exeception will be thrown for null values
     * @return the associated value, which may be null if errorIfNull is false
     */
    protected <T> T getKernelValue(Class<T> valueType, String keyName, boolean errorIfNull)
    {
        return valueType.cast(getKernelValues(valueType, keyName, 1, errorIfNull).get(0));
    }

    /**
     * Get a collection of values associated with a key from the kernel pool.
     * @param <T>
     * @param valueType
     * @param keyName
     * @param expectedSize
     * @return
     */
    protected <T> List<T> getKernelValues(Class<?> valueType, String keyName, int expectedSize)
    {
        return getKernelValues(valueType, keyName, expectedSize, true);
    }

    /**
     *
     * @param <T>
     * @param valueType
     * @param keyName
     * @param expectedSize checked only if values are found
     * @param errorIfNull throw exception if value is null
     * @return the values; will be null iff values are missing or values have
     *         the wrong type
     */
    protected <T> List<T> getKernelValues(Class<?> valueType, String keyName, int expectedSize, boolean errorIfNull)
    {
        List<?> list = new ArrayList<>();
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
                throw new IllegalArgumentException("SPICE kernel is missing values for key " + keyName + " " + getKernelPool().getKeywords());
            }
        }
        else if (list.size() != expectedSize)
        {
            throw new IllegalArgumentException("SPICE kernel has " + list.size() + " kernel values, not expected number " + expectedSize + " for key " + keyName);
        }

        // Unchecked cast is safe; if list doesn't hold what it should an
        // exception would have been thrown above.
        @SuppressWarnings("unchecked")
        List<T> result = (List<T>) list;

        return result;
    }

    protected UnwritableVectorIJK toVector(List<Double> tmpList)
    {
        return new UnwritableVectorIJK(tmpList.get(0), tmpList.get(1), tmpList.get(2));
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

    @Override
	public String getCurrentInstrumentName()
	{
		return currentInstName;
	}

    @Override
	public void setCurrentInstrumentName(String currentInstrumentName)
    {
	    if (getFrameIdForInstrument(currentInstrumentName) == null) {
	        throw new IllegalArgumentException("Cannot set the instrument name to unknown instrument " + currentInstName);
	    }

	    this.currentInstName = currentInstrumentName;
	}

}
