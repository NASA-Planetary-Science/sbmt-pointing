package edu.jhuapl.sbmt.pointing.modules;

import java.nio.file.Path;
import java.nio.file.Paths;

import com.google.common.collect.ImmutableList;

import edu.jhuapl.sbmt.pointing.spice.SpiceInfo;
import edu.jhuapl.sbmt.pointing.spice.SpicePointingProvider;
import edu.jhuapl.sbmt.util.pipeline.publisher.BasePipelinePublisher;

public class SpiceReaderPublisher extends BasePipelinePublisher<SpicePointingProvider>
{
	private SpicePointingProvider pointingProvider;
	private SpiceInfo spiceInfo;
	private String mkFilename;
	private String instFrame;

	public SpiceReaderPublisher(String mkFilename, SpiceInfo spiceInfo, String instFrame)
	{
		this.spiceInfo = spiceInfo;
		this.mkFilename = mkFilename;
		this.instFrame = instFrame;
		try
		{
			loadPointing();
			outputs.add(pointingProvider);
			if (spiceInfo.getBodyNamesToBind().length == 0) return;
			for (String name : spiceInfo.getBodyNamesToBind()) outputs.add(pointingProvider);
		}
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void loadPointing() throws Exception
	{
		Path mkPath = Paths.get(mkFilename);
		SpicePointingProvider.Builder builder =
				SpicePointingProvider.builder(ImmutableList.copyOf(new Path[] {mkPath}), spiceInfo.getBodyName(),
						spiceInfo.getBodyFrameName(), spiceInfo.getScId(), spiceInfo.getScFrameName());

		for (String bodyNameToBind : spiceInfo.getBodyNamesToBind()) builder.bindEphemeris(bodyNameToBind);
		for (String instrumentFrameToBind : spiceInfo.getInstrumentFrameNamesToBind())
		{
			builder.bindFrame(instrumentFrameToBind);
		}

        pointingProvider = builder.build();
        pointingProvider.setCurrentInstFrameName(instFrame);
	}
}
