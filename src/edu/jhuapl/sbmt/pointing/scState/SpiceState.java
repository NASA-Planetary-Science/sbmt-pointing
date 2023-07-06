/**
 *
 */
package edu.jhuapl.sbmt.pointing.scState;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;

import com.google.common.base.Preconditions;

import edu.jhuapl.sbmt.core.pointing.InstrumentPointing;
import edu.jhuapl.sbmt.core.util.TimeUtil;
import edu.jhuapl.sbmt.pointing.spice.SpicePointingProvider;
import edu.jhuapl.sbmt.stateHistory.model.interfaces.State;

import crucible.core.math.vectorspace.UnwritableMatrixIJK;
import crucible.core.math.vectorspace.UnwritableVectorIJK;
import crucible.core.mechanics.EphemerisID;
import crucible.core.mechanics.providers.lockable.LockableFrameLinkEvaluationException;
import crucible.core.mechanics.utilities.SimpleEphemerisID;

/**
 * @author steelrj1
 *
 */
public class SpiceState implements State
{
	private InstrumentPointing pointing;

    /**
     * State time in UTC
     */
    private String utc;

    /**
     * State time in ephemeris time
     */
    private double ephemerisTime;

    private SpicePointingProvider pointingProvider;

	/**
	 *
	 */
	public SpiceState(SpicePointingProvider pointingProvider)
	{
		this.pointingProvider = pointingProvider;
	}

	@Override
	public double getEphemerisTime()
	{
		return ephemerisTime;
	}

	@Override
	public String getUtc()
	{
		return utc;
	}

	public double[] getInstrumentLookDirection(String instrumentName)
	{
		Preconditions.checkNotNull(ephemerisTime);
		InstrumentPointing pointing = pointingProvider.provide(instrumentName, ephemerisTime);
		try {
			UnwritableVectorIJK boresight = pointing.getBoresight().createNegated();
			return new double[] {
					boresight.getI(), boresight.getJ(), boresight.getK()
			};
		}
		catch (LockableFrameLinkEvaluationException lflee)
		{
			return new double[] {0.0, 0.0, 0.0};
		}
	}

	public UnwritableVectorIJK getFrustum(String instrumentName, int index)
	{
		Preconditions.checkNotNull(ephemerisTime);
		InstrumentPointing pointing = pointingProvider.provide(instrumentName, ephemerisTime);
		try {
			return pointing.getFrustum().get(index);
		}
		catch (LockableFrameLinkEvaluationException le) {
			return new UnwritableVectorIJK(0, 0, 0);
		}

	}

	@Override
	public double[] getSpacecraftPosition()
	{
		Preconditions.checkNotNull(ephemerisTime);
		Preconditions.checkNotNull(pointing);
		return new double[] { pointing.getScPosition().getI(),
							  pointing.getScPosition().getJ(),
							  pointing.getScPosition().getK()
		};
	}

	@Override
	public double[] getSpacecraftVelocity()
	{
		Preconditions.checkNotNull(ephemerisTime);
		Preconditions.checkNotNull(pointing);
		return new double[] { pointing.getScVelocity().getI(),
							  pointing.getScVelocity().getJ(),
							  pointing.getScVelocity().getK()
		};
	}

	@Override
	public double[] getEarthPosition()
	{
		Preconditions.checkNotNull(ephemerisTime);
		Preconditions.checkNotNull(pointing);
		EphemerisID earth = new SimpleEphemerisID("EARTH");
		return new double[] { pointing.getPosition(earth).getI(),
	  			  pointing.getPosition(earth).getJ(),
	  			  pointing.getPosition(earth).getK()

		};
	}

	@Override
	public double[] getSunPosition()
	{
		Preconditions.checkNotNull(ephemerisTime);
		Preconditions.checkNotNull(pointing);
		EphemerisID sun = new SimpleEphemerisID("SUN");
		return new double[] { pointing.getPosition(sun).getI(),
	  			  pointing.getPosition(sun).getJ(),
	  			  pointing.getPosition(sun).getK()

		};
	}

	@Override
	public double[][] getSpacecraftAxes()
	{
		Preconditions.checkNotNull(ephemerisTime);
		Preconditions.checkNotNull(pointing);
		double[][] scAxes = new double[3][3];
		try {
			UnwritableMatrixIJK scRotation = pointing.getScRotation();
			scAxes[0] = new double[] { scRotation.getColumn(0).getI(), scRotation.getColumn(0).getJ(), scRotation.getColumn(0).getK()};
			scAxes[1] = new double[] { scRotation.getColumn(1).getI(), scRotation.getColumn(1).getJ(), scRotation.getColumn(1).getK()};
			scAxes[2] = new double[] { scRotation.getColumn(2).getI(), scRotation.getColumn(2).getJ(), scRotation.getColumn(2).getK()};
			return scAxes;
		}
		catch (LockableFrameLinkEvaluationException lflee)
		{
			return new double[][] {{1.0, 0.0, 0.0}, {0.0, 1.0, 0.0}, {0.0, 0.0, 1.0}};
		}
	}


	@Override
	public double[] getSpacecraftXAxis()
	{
		Preconditions.checkNotNull(ephemerisTime);
		Preconditions.checkNotNull(pointing);
		double[] xAxis = new double[3];
		try {
			xAxis[0] = pointing.getScRotation().getColumn(0).getI();
			xAxis[1] = pointing.getScRotation().getColumn(0).getJ();
			xAxis[2] = pointing.getScRotation().getColumn(0).getK();
			return xAxis;
		}
		catch (LockableFrameLinkEvaluationException lflee)
		{
			return new double[] {1.0, 0.0, 0.0};
		}
	}

	@Override
	public double[] getSpacecraftYAxis()
	{
		Preconditions.checkNotNull(ephemerisTime);
		Preconditions.checkNotNull(pointing);
		double[] yAxis = new double[3];
		try {
			yAxis[0] = pointing.getScRotation().getColumn(1).getI();
			yAxis[1] = pointing.getScRotation().getColumn(1).getJ();
			yAxis[2] = pointing.getScRotation().getColumn(1).getK();
			return yAxis;
		}
		catch (LockableFrameLinkEvaluationException lflee)
		{
			return new double[] {0.0, 1.0, 0.0};
		}
	}

	@Override
	public double[] getSpacecraftZAxis()
	{
		Preconditions.checkNotNull(ephemerisTime);
		Preconditions.checkNotNull(pointing);
		double[] zAxis = new double[3];
		try {
			zAxis[0] = pointing.getScRotation().getColumn(2).getI();
			zAxis[1] = pointing.getScRotation().getColumn(2).getJ();
			zAxis[2] = pointing.getScRotation().getColumn(2).getK();
			return zAxis;
		}
		catch (LockableFrameLinkEvaluationException lflee)
		{
			return new double[] {0.0, 0.0, 1.0};
		}
	}

	/**
	 * @param ephemerisTime the ephemerisTime to set
	 */
	public void setEphemerisTime(double ephemerisTime)
	{
		this.ephemerisTime = ephemerisTime;
		this.utc = TimeUtil.et2str(ephemerisTime);
		this.pointing = pointingProvider.provide(ephemerisTime);
	}

	/**
	 * @param currentInstrumentName the currentInstrumentName to set
	 */
	public void setCurrentInstrumentName(String currentInstrumentName)
	{
		pointingProvider.setCurrentInstrumentName(currentInstrumentName);
	}

	/**
     * Writes the state object out to a comma separated value line in the file (appending to it)
     * @param path
     */
    public void writeToCSV(String path)
    {
        try
        {
            File file = new File(path);
            FileWriter in = new FileWriter(file, true);

            in.append(utc);
            in.append(',');
            in.append(Double.toString(getSunPosition()[0]));
            in.append(',');
            in.append(Double.toString(getSunPosition()[1]));
            in.append(',');
            in.append(Double.toString(getSunPosition()[2]));
            in.append(',');
            in.append(Double.toString(getEarthPosition()[0]));
            in.append(',');
            in.append(Double.toString(getEarthPosition()[1]));
            in.append(',');
            in.append(Double.toString(getEarthPosition()[2]));
            in.append(',');
            in.append(Double.toString(getSpacecraftPosition()[0]));
            in.append(',');
            in.append(Double.toString(getSpacecraftPosition()[1]));
            in.append(',');
            in.append(Double.toString(getSpacecraftPosition()[2]));
            in.append(',');
            in.append(Double.toString(getSpacecraftVelocity()[0]));
            in.append(',');
            in.append(Double.toString(getSpacecraftVelocity()[1]));
            in.append(',');
            in.append(Double.toString(getSpacecraftVelocity()[2]));
            in.append('\n');

            in.flush();
            in.close();
        }
        catch (FileNotFoundException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (IOException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

	@Override
	public String toString()
	{
		return "SPICE State:  SC Position: " + getSpacecraftPosition()[0] + "," + getSpacecraftPosition()[1] + "," + getSpacecraftPosition()[2];
	}
}