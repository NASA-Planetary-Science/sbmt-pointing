package edu.jhuapl.sbmt.pointing.spice;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import edu.jhuapl.sbmt.pointing.Frustum;
import edu.jhuapl.sbmt.pointing.Pointing;
import edu.jhuapl.sbmt.pointing.PointingProvider;

import crucible.core.math.vectorspace.UnwritableVectorIJK;
import crucible.core.mechanics.Coverage;
import crucible.core.mechanics.EphemerisID;
import crucible.core.mechanics.FrameID;
import crucible.core.mechanics.StateVector;
import crucible.core.mechanics.StateVectorFunction;
import crucible.core.mechanics.StateVectorFunctions;
import crucible.core.mechanics.providers.aberrated.AberratedEphemerisProvider;
import crucible.core.mechanics.providers.aberrated.AberratedStateVectorFunction;
import crucible.core.mechanics.providers.aberrated.AberrationCorrection;
import crucible.core.mechanics.providers.lockable.LockableEphemerisProvider;
import crucible.core.mechanics.utilities.SimpleEphemerisID;
import crucible.core.mechanics.utilities.SimpleFrameID;
import crucible.core.time.TSEpoch;
import crucible.core.time.TSRange;
import crucible.core.time.TimeSystems;
import crucible.core.time.UTCEpoch;
import crucible.mantle.spice.SpiceEnvironment;
import crucible.mantle.spice.SpiceEnvironmentBuilder;
import crucible.mantle.spice.kernel.tk.sclk.EncodedSCLKConverter;
import crucible.mantle.spice.kernel.tk.sclk.SCLKKernel;
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
            int sclkIdCode, //
            FrameID instrumentFrame) throws Exception
    {
        Preconditions.checkNotNull(mkPaths);
        Preconditions.checkNotNull(bodyId);
        Preconditions.checkNotNull(bodyFrame);
        Preconditions.checkNotNull(scId);
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

        // Bind body and instrument frames.
        builder.bindFrameID(bodyFrame.getName(), bodyFrame);
        builder.bindFrameID(instrumentFrame.getName(), instrumentFrame);

        return of(builder.build(), bodyId, bodyFrame, scId, sclkIdCode, instrumentFrame);
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
            int sclkIdCode, //
            FrameID instrumentFrame) throws Exception
    {
        Preconditions.checkNotNull(spiceEnv);
        Preconditions.checkNotNull(bodyId);
        Preconditions.checkNotNull(bodyFrame);
        Preconditions.checkNotNull(scId);
        Preconditions.checkNotNull(instrumentFrame);

        // Not sure this step is necessary if the kernel set is complete, but in
        // DART test case it was necessary.
        ImmutableMap<FrameID, EphemerisID> centerMap = spiceEnv.getFrameCenterMap();

        ImmutableMap.Builder<FrameID, EphemerisID> mapBuilder = ImmutableMap.builder();
        mapBuilder.putAll(centerMap);
        mapBuilder.put(bodyFrame, bodyId);

        centerMap = mapBuilder.build();

        AberratedEphemerisProvider ephProvider = AberratedEphemerisProvider.createTripleIteration( //
                new LockableEphemerisProvider(spiceEnv.getEphemerisSources(), spiceEnv.getFrameSources()), //
                centerMap //
        );

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

        return of( //
                ephProvider, //
                DefaultTimeSystems, //
                sclkKernel, //
                bodyId, //
                bodyFrame, //
                scId, //
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
     * @param ephProvider the ephemeris and frame provider to be used to to
     *            perform the pointing calculations from the underlying SPICE
     *            kernels
     * @param timeSystems the {@link TimeSystems} implementation to use when
     *            converting {@link TSEpoch} times into TDB times
     * @param sclkConverter the clock kernel to use for converting TDB times to
     *            SCLK times
     * @param bodyId the body's {@link EphemerisID}
     * @param bodyFrame the body's @{link FrameID}
     * @param scId the spacecraft's {@link EphemerisID}
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
            AberratedEphemerisProvider ephProvider, //
            TimeSystems timeSystems, //
            SCLKKernel sclkConverter, //
            EphemerisID bodyId, //
            FrameID bodyFrame, //
            EphemerisID scId, //
            FrameID instrumentFrame, //
            String toString)
    {
        Preconditions.checkNotNull(timeSystems);
        Preconditions.checkNotNull(ephProvider);
        Preconditions.checkNotNull(sclkConverter);
        Preconditions.checkNotNull(bodyId);
        Preconditions.checkNotNull(bodyFrame);
        Preconditions.checkNotNull(scId);
        Preconditions.checkNotNull(instrumentFrame);
        Preconditions.checkNotNull(toString);

        return new SpicePointingProvider() {

            @Override
            public TimeSystems getTimeSystems()
            {
                return timeSystems;
            }

            @Override
            protected EncodedSCLKConverter getScClock()
            {
                return sclkConverter;
            }

            @Override
            protected AberratedEphemerisProvider getEphemerisProvider()
            {
                return ephProvider;
            }

            @Override
            protected EphemerisID getBodyId()
            {
                return bodyId;
            }

            @Override
            protected EphemerisID getScId()
            {
                return scId;
            }

            @Override
            protected FrameID getBodyFrame()
            {
                return bodyFrame;
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
    public Pointing provide(TSEpoch time)
    {
        // Convert specified time to spacecraft clock time.
        double tdb = getTimeSystems().getTDB().getTime(time);
        double sclkTime = getScClock().convertToEncodedSclk(tdb);

        // Get the provider and all information needed to compute the pointing.
        AberratedEphemerisProvider ephProvider = getEphemerisProvider();
        EphemerisID body = getBodyId();
        EphemerisID spacecraft = getScId();
        EphemerisID sun = getSunId();
        FrameID bodyFrame = getBodyFrame();

        // Get objects that can perform the necessary computations for this
        // spacecraft and body (and sun position).
        AberratedStateVectorFunction bodyFromSc = ephProvider.createAberratedStateVectorFunction(body, spacecraft, bodyFrame, Coverage.ALL_TIME, AberrationCorrection.LT_S);
        AberratedStateVectorFunction sunFromBody = ephProvider.createAberratedStateVectorFunction(sun, body, bodyFrame, Coverage.ALL_TIME, AberrationCorrection.LT_S);

        // Need spacecraft-from-body as well as the body-from-spacecraft frame
        // calculations.
        StateVectorFunction scFromBody = StateVectorFunctions.negate(bodyFromSc);

        // Get position of body relative to spacecraft.
        StateVector bodyFromScState = scFromBody.getState(sclkTime);

        // Get sun position relative to body at the time the light left the
        // body, not the time the light arrived at the spacecraft.
        double timeLightLeftBody = sclkTime - bodyFromSc.getLightTime(sclkTime);
        StateVector sunFromBodyState = sunFromBody.getState(timeLightLeftBody);

        // TODO still need to figure out how to get these out of
        // crucible.mantle.spice.
        UnwritableVectorIJK boreSight = new UnwritableVectorIJK(1., 0., 0.);
        UnwritableVectorIJK upDir = new UnwritableVectorIJK(0., 0., 1.);
        Frustum frustum = new Frustum();

        return new Pointing(bodyFromScState.getPosition(), sunFromBodyState.getPosition(), boreSight, upDir, frustum, new TSRange(time, time));
    }

    public abstract TimeSystems getTimeSystems();

    protected abstract EncodedSCLKConverter getScClock();

    protected abstract AberratedEphemerisProvider getEphemerisProvider();

    protected abstract EphemerisID getBodyId();

    protected abstract EphemerisID getScId();

    protected EphemerisID getSunId()
    {
        return SunEphemerisId;
    }

    protected abstract FrameID getBodyFrame();

    protected abstract FrameID getInstrumentFrameId();

    public static UTCEpoch getUTC(String utcString) throws ParseException
    {
        // TODO uncomment and finish generalizing this to handle the following
        // formats, where the fields are delimited by anything except an int or
        // a dot:
        // YYYY DOY HH MM SS (5 ints)
        // YYYY DOY HH MM SS.S* (4 ints + 1 double)
        // YYYY MM DD HH MM SS (6 ints)
        // YYYY MM DD HH MM SS.S* (5 ints + 1 double)
        // YYYYMMDDHHMMSS (13 , no delimiter giving the standard calendar time)
        // YYYYMMDDHHMMSS.S* (12 ints + 1 double giving the standard calendar
        // time)
        // YYYYDOYHHMMSS (12 ints giving the DOY time)
        // YYYYDOYHHMMSS.S* (11 ints + 1 double giving the DOY time)
//        utcString += ".0";
//        System.err.println("input utc string was " + utcString);
//        String[] utcFields = utcString.split("\\b");
//
//        List<Integer> intFields = new ArrayList<>();
//        int decimalIndex = -1;
//        for (int index = 0; index < utcFields.length; ++index)
//        {
//            if (utcFields[index].equals("."))
//            {
//                if (decimalIndex == -1)
//                {
//                    decimalIndex = index;
//                }
//                else
//                {
//                    throw new ParseException("More than one decimal point found in string " + utcString, index);
//                }
//            }
//            else if (utcFields[index].matches("\\d+"))
//            {
//                intFields.add(Integer.parseInt(utcFields[index]));
//            }
//        }
//
//        double seconds;
//        if (decimalIndex == utcString.length() - 1)
//        {
//            // String ended with decimal point. Treat this the same as no decimal point.
//            seconds = intFields.get(intFields.size() - 1)
//        }
//        else if (decimalIndex == utcString.length() - 1)
//        {
//            // Last two integers are the int and the fractional part of seconds.
//        }
//        else if (decimalIndex != -1)
//        {
//            // Decimal
//        }

        if (utcString.length() == 13)
        {
            // yyyydddhhmmss
            int y = Integer.parseInt(utcString.substring(0, 4));
            int doy = Integer.parseInt(utcString.substring(4, 7));
            int hr = Integer.parseInt(utcString.substring(7, 9));
            int mn = Integer.parseInt(utcString.substring(9, 11));
            int s = Integer.parseInt(utcString.substring(11, 13));
            return new UTCEpoch(y, doy, hr, mn, s);
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
            return new UTCEpoch(y, m, d, h, mn, s);

        }
        else if (utcString.length() == 19)
        {
            // yyyy.mm.dd.hh.mm.ss
            int y = Integer.parseInt(utcString.substring(0, 4));
            int m = Integer.parseInt(utcString.substring(5, 7));
            int d = Integer.parseInt(utcString.substring(8, 10));
            int h = Integer.parseInt(utcString.substring(11, 13));
            int mn = Integer.parseInt(utcString.substring(14, 16));
            int s = Integer.parseInt(utcString.substring(17, 19));
            return new UTCEpoch(y, m, d, h, mn, s);

        }
        else
        {
            throw new ParseException("Can't parse string as time: " + utcString, 0);
        }
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
            FrameID instrumentFrame = new SimpleFrameID("DART_DRACO");

            // Where does one get this kind of info?
            int sclkIdCode = -120065803;

            SpicePointingProvider provider = SpicePointingProvider.of(ImmutableList.copyOf(mkPaths), bodyId, bodyFrame, scId, sclkIdCode, instrumentFrame);

            System.err.println(provider.provide(DefaultTimeSystems.getUTC().getTSEpoch(utcEpoch)));
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

    }
}
