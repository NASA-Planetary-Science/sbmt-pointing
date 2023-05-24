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
 * This class was created by modifying the class
 * crucible.crust.kernelmanager.remote.MetaKernelHelper.
 */
class MetaKernelHelper
{
    /**
     * Reads the meta kernel file and produces a list of kernel files to load. Uses
     * PATH_SYMBOLS and PATH_VALUES lists from the input metakernel to replace
     * variables used in the kernel file listing.
     */
    List<File> read(Path localMetaKernel) throws IOException, ParseException
    {

        File mkFile = localMetaKernel.toFile();

        BasicKernelPool pool =
                new TextKernelParser().parse(new InputStreamReader(new FileInputStream(mkFile)));

        List<Pattern> pathSymbols = new ArrayList<>();
        for (String symbol : pool.getStrings("PATH_SYMBOLS"))
        {
            pathSymbols.add(Pattern.compile("\\$" + symbol + "\\b"));
        }

        List<Path> pathValues = new ArrayList<>();
        for (String pathString : pool.getStrings("PATH_VALUES"))
        {
            pathValues.add(localMetaKernel.getParent().resolve(pathString));
        }

        if (pathSymbols.size() != pathValues.size())
        {
            throw new IOException("Mismatch between PATH_SYMBOLS and PATH_VALUES in file " + mkFile);
        }

        List<String> fnames = pool.getStrings("KERNELS_TO_LOAD");
        List<File> localKernels = new ArrayList<>();
        for (String fn : fnames)
        {
            String localKernelFileName = fn;
            for (int index = 0; index < pathSymbols.size(); ++index)
            {
                Pattern rp = pathSymbols.get(index);
                String replaceString = pathValues.get(index).toString();
                replaceString = replaceString.replaceAll("\\\\+", File.separator + File.separator + File.separator + File.separator);
                localKernelFileName = rp.matcher(localKernelFileName).replaceAll(replaceString);
            }
            localKernels.add(new File(localKernelFileName));
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
