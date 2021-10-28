package edu.jhuapl.sbmt.pointing.modules;

import java.io.IOException;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

import com.beust.jcommander.internal.Lists;
import com.google.common.base.Preconditions;

import vtk.vtkMatrix4x4;
import vtk.vtkTransform;
import vtk.vtkTransformFilter;

import edu.jhuapl.sbmt.client.SmallBodyModel;
import edu.jhuapl.sbmt.pointing.InstrumentPointing;
import edu.jhuapl.sbmt.pointing.spice.SpicePointingProvider;
import edu.jhuapl.sbmt.util.TimeUtil;
import edu.jhuapl.sbmt.util.pipeline.operator.BasePipelineOperator;

import crucible.core.math.vectorspace.RotationMatrixIJK;
import crucible.core.mechanics.Coverage;
import crucible.core.mechanics.EphemerisID;
import crucible.core.mechanics.FrameID;
import crucible.core.mechanics.FrameTransformFunction;
import crucible.core.mechanics.utilities.SimpleEphemerisID;
import crucible.core.mechanics.utilities.SimpleFrameID;

public class SpiceBodyOperator extends BasePipelineOperator<Pair<SmallBodyModel, SpicePointingProvider>, SmallBodyModel>
{
	private String centerBodyName;
	private double time;
	private List<SmallBodyModel> smallBodyModels;
	private List<SpicePointingProvider> pointingProviders;


	public SpiceBodyOperator(String centerBodyName, double time)
	{
		this.centerBodyName = centerBodyName;
		this.time = time;
		System.out.println("SpiceBodyOperator: SpiceBodyOperator: time is " + TimeUtil.et2str(time));
		smallBodyModels = Lists.newArrayList();
		pointingProviders = Lists.newArrayList();
	}

	public void setTime(double time)
	{
		System.out.println("SpiceBodyOperator: setTime: setting time to " + TimeUtil.et2str(time));
		this.time = time;
	}

	@Override
	public void processData() throws IOException, Exception
	{
		outputs.clear();
		smallBodyModels.clear();
		pointingProviders.clear();
		System.out.println("SpiceBodyOperator: processData: number inputs " + inputs.size());
		for (int i=0; i< inputs.size(); i++)
		{
			smallBodyModels.add(inputs.get(i).getLeft());
			pointingProviders.add(inputs.get(i).getRight());
		}
		System.out.println("SpiceBodyOperator: processData: number sbm " + smallBodyModels.size());
		for (SmallBodyModel smallBodyModel : smallBodyModels)
		{
			vtkTransform transform = new vtkTransform();

			if (smallBodyModel.getModelName().equals(centerBodyName))
			{
//				RotationMatrixIJK rotationTransform = getBodyOrientation(smallBodyModel.getModelName());
//				vtkMatrix4x4 fullMatrix = getTransformationMatrix(rotationTransform, new double[] { 0, 0, 0 } );
//				transform.SetMatrix(fullMatrix);
//				transform.Update();
//
//				vtkTransformFilter transformFilter=new vtkTransformFilter();
//				transformFilter.SetInputData(smallBodyModel.getSmallBodyPolyData());
//				transformFilter.SetTransform(transform);
//				transformFilter.Update();
//				smallBodyModel.transformBody(transformFilter);
				outputs.add(smallBodyModel);
				continue;
			}

			//shift the body to the proper location at this time
			double[] bodyPos = getBodyPosition(smallBodyModel.getModelName());

//			System.out.println("SpiceBodyOperator: processData: body pos " + new Vector3D(bodyPos));
			RotationMatrixIJK rotationTransform = getBodyOrientation(smallBodyModel.getModelName());
			vtkMatrix4x4 fullMatrix = getTransformationMatrix(rotationTransform, bodyPos);
			transform.SetMatrix(fullMatrix);
			transform.Update();

			vtkTransformFilter transformFilter=new vtkTransformFilter();
			transformFilter.SetInputData(smallBodyModel.getSmallBodyPolyData());
			transformFilter.SetTransform(transform);
			transformFilter.Update();
			smallBodyModel.transformBody(transformFilter);
			outputs.add(smallBodyModel);
		}
	}

	private double[] getBodyPosition(String bodyName)
	{
		SpicePointingProvider pointingProvider  = pointingProviders.get(0);
		Preconditions.checkNotNull(time);
		Preconditions.checkNotNull(pointingProvider);
		String currentInstrumentFrameName = pointingProvider.getCurrentInstFrameName();
//		System.out.println("SpiceBodyOperator: getBodyPosition: current " + currentInstrumentFrameName);
		InstrumentPointing pointing = pointingProvider.provide(currentInstrumentFrameName, time);
		EphemerisID body = new SimpleEphemerisID(bodyName.toUpperCase());
		System.out.println("SpiceBodyOperator: getBodyPosition: time is " + TimeUtil.et2str(time));
//		System.out.println("SpiceBodyOperator: getBodyPosition: body name " + bodyName.toUpperCase());
		return new double[] { pointing.getPosition(body).getI(),
				pointing.getPosition(body).getJ(),
				pointing.getPosition(body).getK()

		};
	}

	private RotationMatrixIJK getBodyOrientation(String bodyName)
	{
		SpicePointingProvider pointingProvider  = pointingProviders.get(0);
//		System.out.println("SpiceBodyOperator: getBodyOrientation: time is " + TimeUtil.et2str(time));
		Preconditions.checkNotNull(time);
		Preconditions.checkNotNull(pointingProvider);
		FrameID body = new SimpleFrameID("120065803_FIXED");
		FrameTransformFunction frameTransformFunction = pointingProvider.getEphemerisProvider().createFrameTransformFunction(new SimpleFrameID("920065803_FIXED"), body, Coverage.ALL_TIME);
		RotationMatrixIJK transform = frameTransformFunction.getTransform(time);
		return transform;
	}

	private vtkMatrix4x4 getTransformationMatrix(RotationMatrixIJK rotationTransform, double[] bodyPos)
	{
		vtkMatrix4x4 fullMatrix = new vtkMatrix4x4();
		fullMatrix.Identity();
		fullMatrix.SetElement(0, 0, rotationTransform.get(0, 0));
		fullMatrix.SetElement(1, 0, rotationTransform.get(0, 1));
		fullMatrix.SetElement(2, 0, rotationTransform.get(0, 2));
		fullMatrix.SetElement(3, 0, 0);
		fullMatrix.SetElement(0, 1, rotationTransform.get(1, 0));
		fullMatrix.SetElement(1, 1, rotationTransform.get(1, 1));
		fullMatrix.SetElement(2, 1, rotationTransform.get(1, 2));
		fullMatrix.SetElement(3, 1, 0);
		fullMatrix.SetElement(0, 2, rotationTransform.get(2, 0));
		fullMatrix.SetElement(1, 2, rotationTransform.get(2, 1));
		fullMatrix.SetElement(2, 2, rotationTransform.get(2, 2));
		fullMatrix.SetElement(3, 2, 0);
		fullMatrix.SetElement(0, 3, bodyPos[0]);
		fullMatrix.SetElement(1, 3, bodyPos[1]);
		fullMatrix.SetElement(2, 3, bodyPos[2]);
		fullMatrix.SetElement(3, 3, 1);
		return fullMatrix;
	}
}