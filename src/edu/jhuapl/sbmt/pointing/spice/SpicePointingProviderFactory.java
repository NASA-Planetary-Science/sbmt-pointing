package edu.jhuapl.sbmt.pointing.spice;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import com.google.common.collect.ImmutableMap;

import crucible.mantle.spice.SpiceEnvironment;
import crucible.mantle.spice.SpiceEnvironmentBuilder;
import crucible.mantle.spice.kernel.tk.sclk.SCLKKernel;

public class SpicePointingProviderFactory
{

    public static SpicePointingProviderFactory of()
    {
        return null;
    }

    public static void main(String[] args)
    {
        try
        {
            String metaKernelFileName = "/path/to/my/metakernel.tm";
            String targetBodyName = "RYUGU";
            String instrumentFrameName = "HAYABUSA2_ONC-T";
            String spacecraftIdString = "-130";

            Path metaKernelPath = Paths.get(metaKernelFileName);
            SpiceEnvironmentBuilder builder = new SpiceEnvironmentBuilder();

            KernelProviderFromLocalMetakernel kernelProvider = new KernelProviderFromLocalMetakernel(metaKernelPath);
            List<File> kernels = kernelProvider.get();

            for (File kernel : kernels)
            {
                builder.load(kernel.getName(), kernel);
            }

            SpiceEnvironment spiceEnv = builder.build();

            ImmutableMap<Integer, SCLKKernel> scKernels = spiceEnv.getSclkKernels();
            spiceEnv.getEphemerisSources();
//            SpicePointingProviderFactory factory = SpicePointingProviderFactory.of();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

}
