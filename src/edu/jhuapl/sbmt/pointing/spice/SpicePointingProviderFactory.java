package edu.jhuapl.sbmt.pointing.spice;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.List;
import java.util.Map.Entry;

import com.google.common.collect.ImmutableMap;

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
import crucible.core.time.TimeSystems;
import crucible.core.time.UTCEpoch;
import crucible.mantle.spice.SpiceEnvironment;
import crucible.mantle.spice.SpiceEnvironmentBuilder;
import crucible.mantle.spice.kernel.tk.sclk.SCLKKernel;
import nom.tam.fits.Fits;
import nom.tam.fits.HeaderCard;

public class SpicePointingProviderFactory
{

    public static SpicePointingProviderFactory of()
    {
        return new SpicePointingProviderFactory();
    }

    protected SpicePointingProviderFactory()
    {
        super();
    }

    public void loadAllKernels(SpiceEnvironmentBuilder builder, Path path) throws Exception
    {
        KernelProviderFromLocalMetakernel kernelProvider = new KernelProviderFromLocalMetakernel(path);
        List<File> kernels = kernelProvider.get();

        for (File kernel : kernels)
        {
//            try {
            builder.load(kernel.getName(), kernel);
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
        }

    }

    protected UTCEpoch getUTC(String utcString) throws ParseException
    {
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

    public static void main(String[] args)
    {
        try
        {
            SpicePointingProviderFactory factory = SpicePointingProviderFactory.of();

            String utcString;
            try (Fits fits = new Fits("/Users/peachjm1/Downloads/impact001.fits"))
            {

                HeaderCard utcCard = fits.getHDU(0).getHeader().findCard("COR_UTC");
                utcString = utcCard.getValue();
            }
            UTCEpoch utcEpoch = factory.getUTC(utcString);

            TimeSystems timeSystems = TimeSystems.builder().build();
            TSEpoch tsEpoch = timeSystems.getUTC().getTSEpoch(utcEpoch);

            System.out.println("time is " + tsEpoch);

            SpiceEnvironmentBuilder builder = new SpiceEnvironmentBuilder();

            Path userHome = Paths.get(System.getProperty("user.home"));
            Path[] paths = new Path[] { //
                    userHome.resolve("dart/SPICE/generic/mk/generic.mk"), //
                    userHome.resolve("dart/SPICE/dra/mk/dra_1.mk"), //
                    userHome.resolve("dart/SPICE/dra/mk/dra_2.mk"), //
                    userHome.resolve("dart/SPICE/dra/mk/dra_3.mk"), //
            };
            for (Path path : paths)
            {
                factory.loadAllKernels(builder, path);
            }

            EphemerisID sun = new SimpleEphemerisID("SUN");
            EphemerisID body = new SimpleEphemerisID("DIDYMOS");
//            EphemerisID spacecraft = new SimpleEphemerisID("DART");
            EphemerisID spacecraft = new SimpleEphemerisID("DART_SPACECRAFT");

            builder.bindEphemerisID(sun.getName(), sun);
            builder.bindEphemerisID(body.getName(), body);
            builder.bindEphemerisID(spacecraft.getName(), spacecraft);

            FrameID bodyFrame = new SimpleFrameID("DIDYMOS_SYSTEM_BARYCENTER");
//            FrameID spacecraftFrame = new SimpleFrameID("DART");
//            FrameID instrumentFrame = new SimpleFrameID("DRACO");

            builder.bindFrameID(bodyFrame.getName(), bodyFrame);
//            builder.bindFrameID(spacecraftFrame.getName(), spacecraftFrame);
//            builder.bindFrameID(instrumentFrame.getName(), instrumentFrame);

            SpiceEnvironment spiceEnv = builder.build();

            for (Entry<Integer, SCLKKernel> spud : spiceEnv.getSclkKernels().entrySet()) {
                System.err.println("sclk " + spud.getKey() + " -> " + spud.getValue());
            }
            ImmutableMap<FrameID, EphemerisID> centerMap = spiceEnv.getFrameCenterMap();

            ImmutableMap.Builder<FrameID, EphemerisID> mapBuilder = ImmutableMap.builder();
            mapBuilder.putAll(centerMap);
            mapBuilder.put(bodyFrame, body);

            centerMap = mapBuilder.build();

            AberratedEphemerisProvider ephemerisProvider = AberratedEphemerisProvider.createTripleIteration( //
                    new LockableEphemerisProvider(spiceEnv.getEphemerisSources(), spiceEnv.getFrameSources()), //
                    centerMap //
            );

            AberratedStateVectorFunction bodyFromSc = ephemerisProvider.createAberratedStateVectorFunction(body, spacecraft, bodyFrame, Coverage.ALL_TIME, AberrationCorrection.LT_S);
            AberratedStateVectorFunction sunFromBody = ephemerisProvider.createAberratedStateVectorFunction(sun, body, bodyFrame, Coverage.ALL_TIME, AberrationCorrection.LT_S);

            StateVectorFunction scFromBody = StateVectorFunctions.negate(bodyFromSc);

            SCLKKernel clockKernel = spiceEnv.getSclkKernels().values().iterator().next();

            double timeOnSc = clockKernel.convertToEncodedSclk(timeSystems.getTDB().getTime(tsEpoch));

            double timeLightLeftBody = timeOnSc - bodyFromSc.getLightTime(timeOnSc);
            StateVector bodyFromScState = scFromBody.getState(timeOnSc);

            StateVector sunFromBodyState = sunFromBody.getState(timeLightLeftBody);

        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

}
