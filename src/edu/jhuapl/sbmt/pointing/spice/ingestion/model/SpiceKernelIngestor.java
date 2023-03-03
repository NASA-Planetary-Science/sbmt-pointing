package edu.jhuapl.sbmt.pointing.spice.ingestion.model;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import edu.jhuapl.saavtk.util.FileUtil;
import edu.jhuapl.sbmt.pointing.spice.KernelProviderFromLocalMetakernel;

public class SpiceKernelIngestor
{
	private String customDataFolder;

	public SpiceKernelIngestor(String customDataFolderName)
	{
		this.customDataFolder = customDataFolderName;
	}

	public String ingestMetaKernelToCache(String metakernalFilename, SpiceKernelLoadStatusListener listener) throws StateHistoryIOException, IOException
	{
		String directoryNameForKernel = FilenameUtils.getBaseName(metakernalFilename);
		String kernelFilename = FilenameUtils.getName(metakernalFilename);
        boolean alreadyExists = checkIfExists(directoryNameForKernel);
        if (alreadyExists)
        {
        	throw new StateHistoryIOException("This metakernel already exists in the cache; please choose another or rename the metakernel file");
        }

        //create a new folder for this metakernel, and copy the MK and associated kernels to that directory
        File newDirectoryBase = new File(customDataFolder, "loadedKernels");
        File newDirectory = new File(newDirectoryBase, directoryNameForKernel);
        boolean directoryMade = newDirectory.mkdirs();
        if (!directoryMade)
        	throw new StateHistoryIOException("Problem saving kernel information; unable to make new directory in cache");

        File destinationFilePath = new File(newDirectory, kernelFilename);
        FileUtil.copyFile(new File(metakernalFilename), destinationFilePath);
		KernelProviderFromLocalMetakernel provider = KernelProviderFromLocalMetakernel.of(Paths.get(metakernalFilename));
        List<File> list = provider.get();
        double numberOfFiles = list.size();
        double currentFile = 0;
        for (File file : list)
        {
        	currentFile++;
        	if (listener != null) listener.percentageLoaded((int)(100*(currentFile/numberOfFiles)));
        	if (!(new File(newDirectory, file.getParentFile().getName()).exists()))
        	{
        		FileUtils.copyDirectoryToDirectory(file.getParentFile(), newDirectory);
        	}
        }
        if (listener != null) listener.percentageLoaded(0);
        //update the PATH_VALUES line in the metakernel to match the new directory
        File metaKernelCopy = new File(newDirectory, kernelFilename);
        Path path = Paths.get(metaKernelCopy.getAbsolutePath());
        List<String> lines = Files.readAllLines(path);
        int lineNumber = -1;
        for (String line : lines)
        {
        	if (!line.contains("PATH_VALUES")) continue;
        	lineNumber = lines.indexOf(line);
        	break;
        }
        lines.set(lineNumber, "PATH_VALUES = ( \'" + newDirectory + File.separator + "\' )");

        Files.write(path, lines);

        return metaKernelCopy.getAbsolutePath();
	}

	private boolean checkIfExists(String folderName)
	{
		return new File(customDataFolder + File.pathSeparator + "loadedKernels", folderName).exists();
	}

	public File getLoadedKernelsDirectory()
	{
		return new File(customDataFolder, "loadedKernels");
	}

	public static void main(String[] args) throws StateHistoryIOException, IOException
	{
		SpiceKernelIngestor ingestor = new SpiceKernelIngestor("/Users/steelrj1/.sbmt-apl/custom-data/Experimental/Phobos (with MEGANE)");
		String kernelNameLoaded = ingestor.ingestMetaKernelToCache("/Users/steelrj1/megane/spice3/meganeLBp.mk", new SpiceKernelLoadStatusListener() {

			@Override
			public void percentageLoaded(double percentage) {
				// TODO Auto-generated method stub

			}
		});
		System.out.println("SpiceKernelIngestor: main: loaded kernels for " + kernelNameLoaded);
	}
}