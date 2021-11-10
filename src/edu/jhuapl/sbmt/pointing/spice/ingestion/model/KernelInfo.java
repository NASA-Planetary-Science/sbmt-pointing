package edu.jhuapl.sbmt.pointing.spice.ingestion.model;

import java.util.List;

public class KernelInfo
{
	String kernelName;
	String kernelDirectory;
	List<String> kernelListing;

	public KernelInfo(String name, String directory, List<String> listing)
	{
		this.kernelName = name;
		this.kernelDirectory = directory;
		this.kernelListing = listing;
	}

	public String getKernelName()
	{
		return kernelName;
	}

	public String getKernelDirectory()
	{
		return kernelDirectory;
	}

	public List<String> getKernelListing()
	{
		return kernelListing;
	}
}