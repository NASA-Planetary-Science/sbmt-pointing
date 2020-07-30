package edu.jhuapl.sbmt.pointing.spice;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import edu.jhuapl.sbmt.pointing.InstrumentPointing;
import edu.jhuapl.sbmt.pointing.PointingProvider;

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
import crucible.mantle.spice.kernel.tk.sclk.EncodedSCLKConverter;
import crucible.mantle.spice.kernel.tk.sclk.SCLKKernel;
import crucible.mantle.spice.kernelpool.UnwritableKernelPool;
import nom.tam.fits.Fits;
import nom.tam.fits.HeaderCard;

/**
 * Implementation of {@link PointingProvider} that extracts pointing information
 * directly from SPICE kernels/metakernels.
 *
 * @author James Peachey
 */
public abstract class SpicePointingProvider implements PointingProvider
{
    protected static final TimeSystems DefaultTimeSystems = TimeSystems.builder().build();
    protected static final EphemerisID SunEphemerisId = new SimpleEphemerisID("SUN");

    /**
     * Construct a new {@link SpicePointingProvider} by creating a standard
     * {@link SpiceEnvironment} that includes all the kernels identified in the
     * specified collection of metakernel file paths, and then binding the
     * specified body, spacecraft, and instrument ephemeris and frame
     * identifiers.
     * <p>
     * This factory method is the simplest to use, but also the least general,
     * because it handles the setup of the SPICE environment internally. It uses
     * the standard implementation of {@link TimeSystems} to handle
     * (non-spacecraft-clock) time conversions.
     *
     * @param mkPaths {@link Iterable} of paths identifying SPICE metakernel
     *            (mk) files to load
     * @param bodyId the body's {@link EphemerisID}
     * @param bodyFrame the body's @{link FrameID}
     * @param scId the spacecraft's {@link EphemerisID}
     * @param scFrame spacecraft's (@link FrameID}
     * @param sclkIdCode the spacecraft clock kernel identification code
     * @param instrumentFrame the instrument's {@link FrameID}, used to compute
     *            the field-of-view quantities
     * @return the {@link SpicePointingProvider}
     * @throws Exception if any arguments are null, or if any part of the
     *             provisioning process for building up the spice runtime
     *             environment throws an exception.
     */
    public static SpicePointingProvider of( //
            Iterable<Path> mkPaths, //
            EphemerisID bodyId, //
            FrameID bodyFrame, //
            EphemerisID scId, //
            FrameID scFrame, //
            int sclkIdCode, //
            FrameID instrumentFrame) throws Exception
    {
        Preconditions.checkNotNull(mkPaths);
        Preconditions.checkNotNull(bodyId);
        Preconditions.checkNotNull(bodyFrame);
        Preconditions.checkNotNull(scId);
        Preconditions.checkNotNull(scFrame);
        Preconditions.checkNotNull(instrumentFrame);

        SpiceEnvironmentBuilder builder = new SpiceEnvironmentBuilder();
        for (Path path : mkPaths)
        {
            loadAllKernels(builder, path);
        }

        // Bind body, spacecraft, sun ephemerides.
        builder.bindEphemerisID(bodyId.getName(), bodyId);
        builder.bindEphemerisID(scId.getName(), scId);
        builder.bindEphemerisID(SunEphemerisId.getName(), SunEphemerisId);

        // Bind body, spacecraft, instrument frames.
        builder.bindFrameID(bodyFrame.getName(), bodyFrame);
        builder.bindFrameID(scFrame.getName(), scFrame);
        builder.bindFrameID(instrumentFrame.getName(), instrumentFrame);

        return of(builder.build(), bodyId, bodyFrame, scId, scFrame, sclkIdCode, instrumentFrame);
    }

    /**
     * Construct a new {@link SpicePointingProvider} from the specified
     * {@link SpiceEnvironment}.
     * <p>
     * This factory method is harder to use, but more flexible/general in that
     * it is COMPLETELY up to the caller to set up the SPICE environment. This
     * factory method does not even bind any body, spacecraft or instrument
     * ephemerides or frames. However, it still uses the standard implementation
     * of {@link TimeSystems} to handle time conversions.
     *
     * @param spiceEnv {@link SpiceEnvironment} with all metakernels/kernels
     *            loaded
     * @param bodyId the body's {@link EphemerisID}
     * @param bodyFrame the body's @{link FrameID}
     * @param scId the spacecraft's {@link EphemerisID}
     * @param scFrame spacecraft's (@link FrameID}
     * @param sclkIdCode the spacecraft clock kernel identification code
     * @param instrumentFrame the instrument's {@link FrameID}, used to compute
     *            the field-of-view quantities
     * @return the {@link SpicePointingProvider}
     * @throws Exception if any arguments are null, or if any part of the
     *             provisioning process for building up the spice runtime
     *             environment throws an exception.
     */
    public static SpicePointingProvider of( //
            SpiceEnvironment spiceEnv, //
            EphemerisID bodyId, //
            FrameID bodyFrame, //
            EphemerisID scId, //
            FrameID scFrame, //
            int sclkIdCode, //
            FrameID instrumentFrame) throws Exception
    {
        Preconditions.checkNotNull(spiceEnv);
        Preconditions.checkNotNull(bodyId);
        Preconditions.checkNotNull(bodyFrame);
        Preconditions.checkNotNull(scId);
        Preconditions.checkNotNull(scFrame);
        Preconditions.checkNotNull(instrumentFrame);

        AberratedEphemerisProvider ephProvider = spiceEnv.createTripleAberratedProvider();

        ImmutableMap<Integer, SCLKKernel> sclkKernels = spiceEnv.getSclkKernels();
        SCLKKernel sclkKernel;
        if (sclkKernels.containsKey(sclkIdCode))
        {
            sclkKernel = sclkKernels.get(sclkIdCode);
        }
        else
        {
            System.err.println("Invalid SCLK kernel identifier " + sclkIdCode);
            System.err.println("Available SCLK kernel identifier codes:");
            for (Integer id : sclkKernels.keySet())
            {
                System.err.println(id);
            }
            throw new IllegalArgumentException("Invalid SCLK kernel identifier " + sclkIdCode);
        }

        UnwritableKernelPool kernelPool = spiceEnv.getPool();

        return of( //
                DefaultTimeSystems, //
                kernelPool, //
                ephProvider, //
                sclkKernel, //
                bodyId, //
                bodyFrame, //
                scId, //
                scFrame, //
                instrumentFrame, //
                scId.getName() + " pointing at " + bodyId.getName());
    }

    /**
     * Construct a new {@link SpicePointingProvider} from the specified lower
     * level SPICE abstractions and body, spacecraft and instrument information.
     * <p>
     * This factory method is harder to use, but the most flexible/general in
     * that it is COMPLETELY up to the caller to set up the low level SPICE
     * ephemeris and frame providers, spacecraft clock kernels, and time
     * systems.
     *
     * @param timeSystems the {@link TimeSystems} implementation to use when
     *            converting {@link TSEpoch} times into TDB times
     * @param kernelPool TODO
     * @param ephProvider the ephemeris and frame provider to be used to to
     *            perform the pointing calculations from the underlying SPICE
     *            kernels
     * @param sclkConverter the clock kernel to use for converting TDB times to
     *            SCLK times
     * @param bodyId the body's {@link EphemerisID}
     * @param bodyFrame the body's @{link FrameID}
     * @param scId the spacecraft's {@link EphemerisID}
     * @param scFrame spacecraft's (@link FrameID}
     * @param instrumentFrame the instrument's {@link FrameID}, used to compute
     *            the field-of-view quantities
     * @param toString (decorative) the string returned by the pointing
     *            provider's {@link #toString()} method
     *
     * @return the {@link SpicePointingProvider}
     * @throws Exception if any arguments are null, or if any part of the
     *             provisioning process for building up the spice runtime
     *             environment throws an exception.
     */
    public static SpicePointingProvider of( //
            TimeSystems timeSystems, //
            UnwritableKernelPool kernelPool, //
            AberratedEphemerisProvider ephProvider, //
            SCLKKernel sclkConverter, //
            EphemerisID bodyId, //
            FrameID bodyFrame, //
            EphemerisID scId, //
            FrameID scFrame, //
            FrameID instrumentFrame, //
            String toString)
    {
        Preconditions.checkNotNull(timeSystems);
        Preconditions.checkNotNull(kernelPool);
        Preconditions.checkNotNull(ephProvider);
        Preconditions.checkNotNull(sclkConverter);
        Preconditions.checkNotNull(bodyId);
        Preconditions.checkNotNull(bodyFrame);
        Preconditions.checkNotNull(scId);
        Preconditions.checkNotNull(scFrame);
        Preconditions.checkNotNull(instrumentFrame);
        Preconditions.checkNotNull(toString);

        return new SpicePointingProvider() {

            @Override
            public TimeSystems getTimeSystems()
            {
                return timeSystems;
            }

            @Override
            protected AberratedEphemerisProvider getEphemerisProvider()
            {
                return ephProvider;
            }

            @Override
            protected UnwritableKernelPool getKernelPool()
            {
                return kernelPool;
            }

            @Override
            protected EphemerisID getBodyId()
            {
                return bodyId;
            }

            @Override
            protected FrameID getBodyFrame()
            {
                return bodyFrame;
            }

            @Override
            protected EphemerisID getScId()
            {
                return scId;
            }

            protected FrameID getScFrame()
            {
                return scFrame;
            }

            @Override
            protected EncodedSCLKConverter getScClock()
            {
                return sclkConverter;
            }

            @Override
            protected FrameID getInstrumentFrameId()
            {
                return instrumentFrame;
            }

            @Override
            public String toString()
            {
                return toString;
            }
        };
    }

    protected SpicePointingProvider()
    {
        super();
    }

    @Override
    public InstrumentPointing provide(TSEpoch time)
    {
        // Convert specified time to spacecraft clock time.
        double tdb = getTimeSystems().getTDB().getTime(time);

        // Get the provider and all information needed to compute the pointing.
        AberratedEphemerisProvider ephProvider = getEphemerisProvider();
        EphemerisID body = getBodyId();
        FrameID bodyFrame = getBodyFrame();
        EphemerisID spacecraft = getScId();
        EphemerisID sun = getSunId();

        // Get objects that can perform the necessary computations for this
        // spacecraft and body (and sun position).
        AberratedStateVectorFunction bodyFromSc = ephProvider.createAberratedStateVectorFunction(body, spacecraft, bodyFrame, Coverage.ALL_TIME, AberrationCorrection.LT_S);
        AberratedStateVectorFunction sunFromBody = ephProvider.createAberratedStateVectorFunction(sun, body, bodyFrame, Coverage.ALL_TIME, AberrationCorrection.LT_S);

        // Need spacecraft-from-body as well as the body-from-spacecraft frame
        // calculations.
        StateVectorFunction scFromBody = StateVectorFunctions.negate(bodyFromSc);

        // Get position of body relative to spacecraft.
        StateVector bodyFromScState = scFromBody.getState(tdb);

        // Get sun position relative to body at the time the light left the
        // body, not the time the light arrived at the spacecraft.
        double timeLightLeftBody = tdb - bodyFromSc.getLightTime(tdb);
        StateVector sunFromBodyState = sunFromBody.getState(timeLightLeftBody);

        int instCode = getKernelValue(Integer.class, "FRAME_" + getInstrumentFrameId().getName());

        // TODO: need to find out the frame for FOV values and handle any
        // transformations appropriately.
        // For now, assume boresight and frustum are defined in the instrument
        // frame.

        UnwritableVectorIJK boresight = getBoresight(instCode);

        PolygonalCone frustum = getFrustum(instCode, boresight);

        UnwritableVectorIJK vertex = frustum.getVertex();
        UnwritableVectorIJK upDir = VectorIJK.cross(boresight, VectorIJK.cross(vertex, boresight));

        return new InstrumentPointing(bodyFromScState.getPosition(), sunFromBodyState.getPosition(), boresight, upDir, frustum.getCorners(), new TSRange(time, time));
    }

    public abstract TimeSystems getTimeSystems();

    protected abstract AberratedEphemerisProvider getEphemerisProvider();

    protected abstract UnwritableKernelPool getKernelPool();

    protected abstract EphemerisID getBodyId();

    protected abstract FrameID getBodyFrame();

    protected abstract EphemerisID getScId();

    protected abstract FrameID getScFrame();

    protected abstract EncodedSCLKConverter getScClock();

    protected abstract FrameID getInstrumentFrameId();

    protected UnwritableVectorIJK getBoresight(Integer instCode)
    {
        return toVector(getKernelValues(Double.class, "INS" + instCode + "_BORESIGHT", 3));
    }

    /**
     * As described at
     * https://naif.jpl.nasa.gov/pub/naif/toolkit_docs/C/cspice/getfov_c.html
     *
     * @param instCode
     * @return
     */
    protected PolygonalCone getFrustum(int instCode, UnwritableVectorIJK boresight)
    {
        final String instPrefix = "INS" + instCode + "_";

        String shape = getKernelValue(String.class, instPrefix + "FOV_SHAPE");

        String classSpec = getKernelValue(String.class, instPrefix + "FOV_CLASS_SPEC", false);

        boolean corners = classSpec != null ? classSpec.equals("CORNERS") : true;

        PolygonalCone result;
        if (corners)
        {
            Preconditions.checkArgument(shape.equals("RECTANGLE"), "Unsupported FOV shape " + shape + " for instrument frame " + getInstrumentFrameId().getName());

            throw new UnsupportedOperationException("TODO code this case up");
        }
        else if (classSpec.equals("ANGLES"))
        {
            UnwritableVectorIJK refVector = toVector(getKernelValues(Double.class, instPrefix + "FOV_REF_VECTOR", 3));
            double refAngle = getKernelValue(Double.class, instPrefix + "FOV_REF_ANGLE");
            double crossAngle = getKernelValue(Double.class, instPrefix + "FOV_CROSS_ANGLE");
            // TODO also need to read/check units, convert as needed.

            result = Cones.createRectangularCone(refVector, boresight, crossAngle, refAngle);
        }
        else
        {
            throw new IllegalArgumentException( //
                    "Illegal value in SPICE kernel; FOV_CLASS_SPEC must be either \"CORNERS\" or \"ANGLES\" for instrument frame " + //
                            getInstrumentFrameId().getName());
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

    public static void loadAllKernels(SpiceEnvironmentBuilder builder, Path path) throws Exception
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
            Path[] mkPaths = new Path[] { //
                    userHome.resolve("dart/SPICE/generic/mk/generic.mk"), //
                    userHome.resolve("dart/SPICE/dra/mk/dra_1.mk"), //
                    userHome.resolve("dart/SPICE/dra/mk/dra_2.mk"), //
                    userHome.resolve("dart/SPICE/dra/mk/dra_3.mk"), //
            };

            EphemerisID bodyId = new SimpleEphemerisID("DIDYMOS");
            EphemerisID scId = new SimpleEphemerisID("DART_SPACECRAFT");

            FrameID bodyFrame = new SimpleFrameID("DIDYMOS_SYSTEM_BARYCENTER");
            FrameID scFrame = new SimpleFrameID("DART_SPACECRAFT");
            FrameID instrumentFrame = new SimpleFrameID("DART_DRACO");

            // Where does one get this kind of info?
            int sclkIdCode = -120065803;

            SpicePointingProvider provider = SpicePointingProvider.of(ImmutableList.copyOf(mkPaths), bodyId, bodyFrame, scId, scFrame, sclkIdCode, instrumentFrame);

            System.err.println(provider.provide(DefaultTimeSystems.getUTC().getTSEpoch(utcEpoch)));
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

    }
}
