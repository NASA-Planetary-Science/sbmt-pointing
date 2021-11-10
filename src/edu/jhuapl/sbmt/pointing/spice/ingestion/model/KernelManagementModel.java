package edu.jhuapl.sbmt.pointing.spice.ingestion.model;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;

import glum.item.BaseItemManager;

public class KernelManagementModel extends BaseItemManager<KernelInfo>
{
	private String loadedKernelsDirectory;

	public KernelManagementModel(String loadedKernelsDirectory) throws IOException
	{
		this.loadedKernelsDirectory = loadedKernelsDirectory;
		refreshModel();
	}

	public void refreshModel() throws IOException
	{
		File[] loadedKernelSets = new File(loadedKernelsDirectory).listFiles();
		List<KernelInfo> allSets = new ArrayList<KernelInfo>();
		if (loadedKernelSets == null)
		{
			setAllItems(allSets);
			return;
		}
		for (File directory : loadedKernelSets)
		{
			List<String> allFiles = Files.walk(Paths.get(directory.toURI())).filter(Files::isRegularFile).map(f -> f.toString()).collect(Collectors.toList());
			allSets.add(new KernelInfo(directory.getName(), directory.getAbsolutePath(), allFiles));
		}
		setAllItems(allSets);
	}

	public void deleteKernelSet(String directoryName, boolean deleteFromDisk)
	{
		List<KernelInfo> allItems = new ArrayList<KernelInfo>(getAllItems());
		boolean anyRemoved = allItems.removeIf(entry -> {

        	return entry.getKernelDirectory().equals(directoryName);
        });
		if (anyRemoved && deleteFromDisk)
		{
			File directory = new File(directoryName);
			try
			{
				FileUtils.deleteDirectory(directory);
			}
			catch (IOException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		setAllItems(allItems);
	}
}