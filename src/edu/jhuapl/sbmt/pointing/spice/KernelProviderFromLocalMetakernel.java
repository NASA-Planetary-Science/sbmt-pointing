package edu.jhuapl.sbmt.pointing.spice;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import picante.exceptions.PicanteRuntimeException;
import picante.spice.kernelpool.parser.ParseException;

/**
 * This class and {@link MetaKernelHelper} were copied from
 * crucible.crust.kernelmanager.remote. The crucible version
 * implements the Provider interface defined by Guice. Rather than importing
 * Guice to solve this problem, the interface was dropped in the copied version;
 * it was not otherwise changed during initial copy.
 * <p>
 * Gets the list of kernels from a local metakernel. In other words, these
 * kernels are already copied to your local machine and are accessible already
 * as files instead of URLs.
 */
//public class KernelProviderFromLocalMetakernel implements Provider<List<File>> {
public class KernelProviderFromLocalMetakernel
{
    public static KernelProviderFromLocalMetakernel of(Path localMetaKernel)
    {
        return new KernelProviderFromLocalMetakernel(localMetaKernel);
    }
    private final Path localMetaKernel;

    // public KernelProviderFromLocalMetakernel(Path localMetaKernel)
    protected KernelProviderFromLocalMetakernel(Path localMetaKernel)
    {
        super();
        this.localMetaKernel = localMetaKernel;
    }

    // @Override
    // public List<File> get()
    public List<File> get()
    {
        try
        {
            return new MetaKernelHelper().read(localMetaKernel);
        }
        catch (IOException e)
        {
            e.printStackTrace();
            throw new PicanteRuntimeException("Error reading kernel list from expected place: "
                    + e.getMessage(), e);
        }
        catch (ParseException e)
        {
            e.printStackTrace();
            throw new PicanteRuntimeException("Error parsing text kernel: " + e.getMessage(), e);
        }
    }

}
