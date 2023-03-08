package edu.jhuapl.sbmt.pointing;

import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

import com.beust.jcommander.internal.Lists;

import edu.jhuapl.saavtk.model.IPositionOrientationManager;
import edu.jhuapl.sbmt.common.client.SmallBodyModel;
import edu.jhuapl.sbmt.pointing.modules.SpiceBodyOperator;
import edu.jhuapl.sbmt.pointing.modules.SpiceReaderPublisher;
import edu.jhuapl.sbmt.pointing.spice.SpiceInfo;
import edu.jhuapl.sbmt.pointing.spice.SpicePointingProvider;
import edu.jhuapl.sbmt.util.pipeline.publisher.IPipelinePublisher;
import edu.jhuapl.sbmt.util.pipeline.publisher.Just;
import edu.jhuapl.sbmt.util.pipeline.publisher.Publishers;
import edu.jhuapl.sbmt.util.pipeline.subscriber.Sink;

public class PositionOrientationManager implements IPositionOrientationManager<SmallBodyModel>
{
	IPointingProvider pointingProvider;
	List<SmallBodyModel> updatedBodies;
	List<SmallBodyModel> models;
	SpiceBodyOperator spiceBodyOperator;
	SpiceReaderPublisher pointingProviders;
	IPipelinePublisher<Pair<SmallBodyModel, SpicePointingProvider>> spiceBodyObjects;
	String mkFilename;
	SpiceInfo spiceInfo;
	String instName;
	String centerBodyName;

	public PositionOrientationManager(List<SmallBodyModel> models, String mkFilename, SpiceInfo spiceInfo, String instName, String centerBodyName, double startTime)
	{
		this.models = List.copyOf(models);
		this.mkFilename = mkFilename;
		this.spiceInfo = spiceInfo;
		this.instName = instName;
		this.centerBodyName = centerBodyName;

		initialize(startTime);
	}

	private void initialize(double time)
	{
		updatedBodies = Lists.newArrayList();
		pointingProviders = new SpiceReaderPublisher(mkFilename, spiceInfo, instName);
		spiceBodyObjects = Publishers.formPair(Just.of(models), pointingProviders);
//		List<String> frameNames = List.of(spiceInfo.getBodyFrameName(), spiceInfo.getInstrumentNamesToBind()[1]);
		spiceBodyOperator = new SpiceBodyOperator(centerBodyName, time);
		try
		{
			run(time, models);
		}
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private IPipelinePublisher<SmallBodyModel> of(double time)
	{
		//*********************************
		//Use SPICE to position the bodies
		//*********************************
		spiceBodyOperator.setTime(time);
		return spiceBodyObjects
			.operate(spiceBodyOperator)
			.subscribe(Sink.of(updatedBodies));
	}

	public void run(double time) throws Exception
	{
		initialize(time);
	}


	private void run(double time, List<SmallBodyModel> models) throws Exception
	{
		updatedBodies.clear();
		spiceBodyObjects = Publishers.formPair(Just.of(models), pointingProviders);
		of(time).run();
	}

	public List<SmallBodyModel> getUpdatedBodies()
	{
		return updatedBodies;
	}
}