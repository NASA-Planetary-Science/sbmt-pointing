package edu.jhuapl.sbmt.pointing;

import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

import com.beust.jcommander.internal.Lists;

import edu.jhuapl.saavtk.model.IPositionOrientationManager;
import edu.jhuapl.sbmt.client.SmallBodyModel;
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
	SpiceBodyOperator spiceBodyOperator;
	SpiceReaderPublisher pointingProviders;
	IPipelinePublisher<Pair<SmallBodyModel, SpicePointingProvider>> spiceBodyObjects;

	public PositionOrientationManager(List<SmallBodyModel> models, String mkFilename, SpiceInfo spiceInfo, String instFrame, String centerBodyName, double startTime)
	{
		updatedBodies = Lists.newArrayList();
		System.out.println("PositionOrientationManager: PositionOrientationManager: number of models " + models.size());
		pointingProviders = new SpiceReaderPublisher(mkFilename, spiceInfo, instFrame);
		spiceBodyObjects = Publishers.formPair(Just.of(models), pointingProviders);
		System.out.println("PositionOrientationManager: PositionOrientationManager: spice body objects " + spiceBodyObjects.getOutputs().size());
		spiceBodyOperator = new SpiceBodyOperator(centerBodyName, startTime);
		try
		{
			run(startTime, models);
			System.out.println("PositionOrientationManager: PositionOrientationManager: updated bodies size " + updatedBodies.size());
		}
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
//		spiceBodyOperator.setTime(startTime);
//		spiceBodyObjects
//			.operate(spiceBodyOperator)
//			.subscribe(Sink.of(updatedBodies));

	}

	private IPipelinePublisher<SmallBodyModel> of(double time)
	{
		//*********************************
		//Use SPICE to position the bodies
		//*********************************
//		pointingProviders = new SpiceReaderPublisher(mkPath, activeSpiceInfo, instFrame);
//		spiceBodyObjects = Publishers.formPair(vtkReader, pointingProviders);
//		spiceBodyOperator = new SpiceBodyOperator(centerBodyName, time);
		spiceBodyOperator.setTime(time);
		return spiceBodyObjects
			.operate(spiceBodyOperator)
			.subscribe(Sink.of(updatedBodies));
	}

	public void run(double time, List<SmallBodyModel> models) throws Exception
	{
		updatedBodies.clear();
		spiceBodyObjects = Publishers.formPair(Just.of(models), pointingProviders);
		System.out.println("PositionOrientationManager: run: spice body objects " + spiceBodyObjects.getOutputs().size());
		((SpiceBodyOperator)spiceBodyOperator).setTime(time);
		of(time).run();
	}

	public List<SmallBodyModel> getUpdatedBodies()
	{
		return updatedBodies;
	}

}
