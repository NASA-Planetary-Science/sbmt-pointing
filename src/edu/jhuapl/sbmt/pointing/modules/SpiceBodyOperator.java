package edu.jhuapl.sbmt.pointing.modules;

import java.io.IOException;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

import com.beust.jcommander.internal.Lists;
import com.google.common.base.Preconditions;

import vtk.vtkMatrix4x4;
import vtk.vtkTransform;

import edu.jhuapl.sbmt.common.client.SmallBodyModel;
import edu.jhuapl.sbmt.pipeline.operator.BasePipelineOperator;
import edu.jhuapl.sbmt.pointing.InstrumentPointing;
import edu.jhuapl.sbmt.pointing.spice.SpicePointingProvider;

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
//	private List<String> frameNames;
	private SpicePointingProvider pointingProvider;

	public SpiceBodyOperator(String centerBodyName, double time)
	{
		this.centerBodyName = centerBodyName;
		this.time = time;
		smallBodyModels = Lists.newArrayList();
		pointingProviders = Lists.newArrayList();
//		this.frameNames = frameNames;
	}

	public void setTime(double time)
	{
		this.time = time;
	}

	@Override
	public void processData() throws IOException, Exception
	{
		outputs.clear();
		smallBodyModels.clear();
		pointingProviders.clear();
		for (int i=0; i< inputs.size(); i++)
		{
			smallBodyModels.add(inputs.get(i).getLeft());
			pointingProviders.add(inputs.get(i).getRight());
		}
		for (SmallBodyModel smallBodyModel : smallBodyModels)
		{
			String bodyName = smallBodyModel.getConfig().getAuthor().toString().split("-")[1].toUpperCase();
			vtkTransform transform = new vtkTransform();
			if (bodyName.equals(centerBodyName))
			{
				outputs.add(smallBodyModel);
				continue;
			}
			pointingProvider = pointingProviders.get(0);
			//shift the body to the proper location at this time
			double[] bodyPos = getBodyPosition(bodyName);
			smallBodyModel.getSmallBodyActor().SetPosition(new double[] {0,0,0});
			smallBodyModel.getSmallBodyActor().SetOrientation(new double[] {0,0,0});
			RotationMatrixIJK rotationTransform = getBodyOrientation(bodyName);
			vtkMatrix4x4 fullMatrix = getTransformationMatrix(rotationTransform, bodyPos);
			transform.SetMatrix(fullMatrix);
			transform.Update();


			smallBodyModel.transformBody(transform);
			outputs.add(smallBodyModel);
		}
	}

	private double[] getBodyPosition(String bodyName)
	{
		Preconditions.checkNotNull(time);
		Preconditions.checkNotNull(pointingProvider);
//		System.out.println("SpiceBodyOperator: getBodyPosition: time " + time);
		InstrumentPointing pointing = pointingProvider.provide(time);
		EphemerisID body = new SimpleEphemerisID(bodyName.toUpperCase());
//		System.out.println("SpiceBodyOperator: getBodyPosition: " + new Vector3D(new double[] { pointing.getPosition(body).getI(),
//				pointing.getPosition(body).getJ(),
//				pointing.getPosition(body).getK()
//
//		}));

		return new double[] { pointing.getPosition(body).getI(),
				pointing.getPosition(body).getJ(),
				pointing.getPosition(body).getK()



		};
	}

	private RotationMatrixIJK getBodyOrientation(String bodyName)
	{
		Preconditions.checkNotNull(time);
		Preconditions.checkNotNull(pointingProvider);
		FrameID body = new SimpleFrameID("IAU_" + bodyName);
		FrameID centerBody = new SimpleFrameID("IAU_" + centerBodyName);
		FrameTransformFunction frameTransformFunction = pointingProvider.getEphemerisProvider().createFrameTransformFunction(centerBody, body, Coverage.ALL_TIME);
		RotationMatrixIJK transform = frameTransformFunction.getTransform(time);
//		System.out.println("SpiceBodyOperator: getBodyOrientation: transform " + transform);
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