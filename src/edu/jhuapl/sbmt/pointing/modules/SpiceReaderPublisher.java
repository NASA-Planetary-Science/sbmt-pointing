package edu.jhuapl.sbmt.pointing.modules;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.google.common.collect.ImmutableList;

import edu.jhuapl.sbmt.pipeline.publisher.BasePipelinePublisher;
import edu.jhuapl.sbmt.pointing.spice.SpiceInfo;
import edu.jhuapl.sbmt.pointing.spice.SpicePointingProvider;

import crucible.mantle.spice.adapters.AdapterInstantiationException;
import crucible.mantle.spice.kernel.KernelInstantiationException;

public class SpiceReaderPublisher extends BasePipelinePublisher<SpicePointingProvider>
{
	private SpicePointingProvider pointingProvider;
	private SpiceInfo spiceInfo;
	private String mkFilename;
	private String instName;

	public SpiceReaderPublisher(String mkFilename, SpiceInfo spiceInfo) throws KernelInstantiationException, IOException, AdapterInstantiationException, FileNotFoundException
	{
		this(mkFilename, spiceInfo, "");
	}

	public SpiceReaderPublisher(String mkFilename, SpiceInfo spiceInfo, String instName) throws KernelInstantiationException, IOException, AdapterInstantiationException, FileNotFoundException
	{
		this.spiceInfo = spiceInfo;
		this.mkFilename = mkFilename;
		this.instName = instName;
		loadPointing();
		outputs.add(pointingProvider);
		if (spiceInfo.getBodyNamesToBind().length == 0) return;
		for (String name : spiceInfo.getBodyNamesToBind()) outputs.add(pointingProvider);

	}

	private void loadPointing() throws KernelInstantiationException, IOException, AdapterInstantiationException, FileNotFoundException
	{
		Path mkPath = Paths.get(mkFilename);
		SpicePointingProvider.Builder builder =
				SpicePointingProvider.builder(ImmutableList.copyOf(new Path[] {mkPath}), spiceInfo.getBodyName(),
						spiceInfo.getBodyFrameName(), spiceInfo.getScId(), spiceInfo.getScFrameName());

		for (String bodyNameToBind : spiceInfo.getBodyNamesToBind()) builder.bindEphemeris(bodyNameToBind);
		for (String bodyFrameToBind : spiceInfo.getBodyFramesToBind()) builder.bindFrame(bodyFrameToBind);
		for (String instrumentNameToBind : spiceInfo.getInstrumentNamesToBind())
		{
			builder.includeInstrument(instrumentNameToBind);
		}
		for (String instrumentFrameNameToBind : spiceInfo.getInstrumentFrameNamesToBind())
		{
			builder.includeFirstInstrumentsWithFrame(instrumentFrameNameToBind);
		}

        pointingProvider = builder.build();
        if (instName.equals("")) instName = pointingProvider.getInstrumentNames()[0];
        pointingProvider.setCurrentInstrumentName(instName);
        if (spiceInfo.getInstrumentNamesToBind().length == 0)
        {
        	pointingProvider.setCurrentInstrumentName(pointingProvider.getInstrumentNames()[0]);
        }
	}
}
