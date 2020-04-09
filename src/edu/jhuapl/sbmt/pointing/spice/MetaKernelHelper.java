package edu.jhuapl.sbmt.pointing.spice;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import crucible.mantle.spice.kernelpool.BasicKernelPool;
import crucible.mantle.spice.kernelpool.parser.ParseException;
import crucible.mantle.spice.kernelpool.parser.TextKernelParser;

/**
 *
 * This class and {@link KernelProviderFromLocalMetakernel} were copied from
 * crucible.crust.kernelmanager.remote. This class's whitespace was reformatted;
 * it was not otherwise changed during initial copy.
 */
class MetaKernelHelper
{
    List<File> read(Path localMetaKernel) throws IOException, ParseException
    {
        File mkFile = localMetaKernel.toFile();
        BasicKernelPool pool =
                new TextKernelParser().parse(new InputStreamReader(new FileInputStream(mkFile)));
        File rootDir = new File(pool.getStrings("PATH_VALUES").get(0));
        List<String> fnames = pool.getStrings("KERNELS_TO_LOAD");
        List<File> localKernels = new ArrayList<File>();
        Pattern rp = Pattern.compile("^\\$ROOT/");
        for (String fn : fnames)
        {
            // Replace $ROOT with the root directory name
            // Do this by stripping off $ROOT
            String strippedName = rp.matcher(fn).replaceFirst("");
            localKernels.add(new File(rootDir, strippedName));
        }
        return localKernels;
    }

    void write(Path localMetaKernel, List<File> kernels) throws IOException
    {

        File kernelRoot = findBiggestRootDir(kernels).toFile();

        File mkFile = localMetaKernel.toFile();
        PrintWriter pw = new PrintWriter(new FileOutputStream(mkFile));
        pw.println("\\begindata");
        pw.println("");
        pw.println("   PATH_SYMBOLS = ( 'ROOT' )");
        pw.println("   PATH_VALUES  = ( '" + kernelRoot.getAbsolutePath() + "' )");
        pw.println("");
        pw.println("\\begintext");

        pw.println("\\begindata");
        pw.println("");
        pw.println("   KERNELS_TO_LOAD = (");
        int N = kernels.size();
        for (int i = 0; i < N; i++)
        {
            File f = kernels.get(i);
            String pathWithRootDirRemoved =
                    removeRootDirFromKernelFilename(kernelRoot, f.getAbsolutePath());
            String comma = ",";
            if (i == N - 1)
            {
                comma = "";
            }
            pw.println("                  '$ROOT" + pathWithRootDirRemoved + "'" + comma);
        }
        pw.println("   )");
        pw.println("");
        pw.println("\\begintext");

        pw.close();
    }

    /**
     * find the largest path prefix that all the kernels have in common
     *
     * @param kernels
     * @return
     */
    private Path findBiggestRootDir(List<File> kernels)
    {

        Path defaultCommonPath = Paths.get("");

        if (kernels.size() == 0)
        {
            return defaultCommonPath;
        }

        Path prefixPath = kernels.get(0).toPath();
        do
        {
            prefixPath = prefixPath.getParent();
            if (prefixPath == null)
            {
                return defaultCommonPath;
            }
        }
        while (!isCommonParent(prefixPath, kernels));

        return prefixPath;
    }

    private boolean isCommonParent(Path prefixPath, List<File> kernels)
    {
        for (File f : kernels)
        {
            if (!f.toPath().startsWith(prefixPath))
            {
                return false;
            }
        }
        return true;
    }

    private String removeRootDirFromKernelFilename(File kernelRoot, String fullPathFilename)
    {
        String rootDir = kernelRoot.getAbsolutePath();
        Pattern rootDirPattern = Pattern.compile("^" + Pattern.quote(rootDir));
        String kernelFileBelowRootDir = rootDirPattern.matcher(fullPathFilename).replaceAll("");
        return kernelFileBelowRootDir;
    }
}
