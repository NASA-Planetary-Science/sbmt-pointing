package edu.jhuapl.sbmt.pointing.scState;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;

import edu.jhuapl.sbmt.core.util.TimeUtil;
import edu.jhuapl.sbmt.pointing.StateHistoryUtil;
import edu.jhuapl.sbmt.stateHistory.model.interfaces.State;

import crucible.core.math.vectorspace.UnwritableVectorIJK;

/**
 * Spacecraft state object represented by a set of comma separated values
 * @author steelrj1
 *
 */
public class CsvState implements State
{
    /**
     * State time in UTC
     */
    private String utc;
    /**
     * State time in ephemeris time
     */
    private double ephemerisTime;

    /**
     * Spacecraft position, in km, in body fixed frame
     */
    private double[] spacecraftPosition;

    /**
     * Spacecraft velocity vector, in km/s, in body fixed frame
     */
    private double[] spacecraftVelocity;

    /**
     * Earth position vector, in km, in body fixed frame
     */
    private double[] earthPosition;

    /**
     * Sun position vector, in km, in body fixed frame
     */
    private double[] sunPosition;

    /**
     * Spacecraft X-Axis vector, in km, in body fixed frame
     */
    private double[] spacecraftXAxis;

    /**
     * Spacecraft Y-Axis vector, in km, in body fixed frame
     */
    private double[] spacecraftYAxis;

    /**
     * Spacecraft Z-Axis vector, in km, in body fixed frame
     */
    private double[] spacecraftZAxis;

    /**
     * Writes the binary data to a CSV file and sets the data to the correct variables
     * @param utc
     * @param sunPosX
     * @param sunPosY
     * @param sunPosZ
     * @param earthPosX
     * @param earthPosY
     * @param earthPosZ
     * @param spacecraftPosX
     * @param spacecraftPosY
     * @param spacecraftPosZ
     * @param spacecraftVelX
     * @param spacecraftVelY
     * @param spacecraftVelZ
     */
    public CsvState(String utc, double sunPosX, double sunPosY, double sunPosZ,
                                double earthPosX, double earthPosY, double earthPosZ,
                                double spacecraftPosX, double spacecraftPosY, double spacecraftPosZ,
                                double spacecraftVelX, double spacecraftVelY, double spacecraftVelZ)
    {
        this.utc = utc;
        ephemerisTime = TimeUtil.str2et(utc);
        sunPosition = new double[] {sunPosX, sunPosY, sunPosZ};
        earthPosition = new double[] {earthPosX, earthPosY, earthPosZ};
        spacecraftPosition = new double[] {spacecraftPosX, spacecraftPosY, spacecraftPosZ};
        spacecraftVelocity = new double[] {spacecraftVelX, spacecraftVelY, spacecraftVelZ};
        spacecraftXAxis = new double[] { 1.0, 0.0, 0.0 };
        spacecraftYAxis = new double[] { 0.0, 1.0, 0.0 };
        spacecraftZAxis = new double[] { 0.0, 0.0, 1.0 };
    }

    public CsvState(int i, File path, int[] position)
    {
    	this(StateHistoryUtil.readString(i, path),
					StateHistoryUtil.readBinary(position[0], path), StateHistoryUtil.readBinary(position[1], path),
					StateHistoryUtil.readBinary(position[2], path), StateHistoryUtil.readBinary(position[3], path),
					StateHistoryUtil.readBinary(position[4], path), StateHistoryUtil.readBinary(position[5], path),
					StateHistoryUtil.readBinary(position[6], path), StateHistoryUtil.readBinary(position[7], path),
					StateHistoryUtil.readBinary(position[8], path), StateHistoryUtil.readBinary(position[9], path),
					StateHistoryUtil.readBinary(position[10], path), StateHistoryUtil.readBinary(position[11], path));
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
            in.append(Double.toString(sunPosition[0]));
            in.append(',');
            in.append(Double.toString(sunPosition[1]));
            in.append(',');
            in.append(Double.toString(sunPosition[2]));
            in.append(',');
            in.append(Double.toString(earthPosition[0]));
            in.append(',');
            in.append(Double.toString(earthPosition[1]));
            in.append(',');
            in.append(Double.toString(earthPosition[2]));
            in.append(',');
            in.append(Double.toString(spacecraftPosition[0]));
            in.append(',');
            in.append(Double.toString(spacecraftPosition[1]));
            in.append(',');
            in.append(Double.toString(spacecraftPosition[2]));
            in.append(',');
            in.append(Double.toString(spacecraftVelocity[0]));
            in.append(',');
            in.append(Double.toString(spacecraftVelocity[1]));
            in.append(',');
            in.append(Double.toString(spacecraftVelocity[2]));
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

    /**
     * Reads in a csv line and adds the data to the correct variables
     * @param line
     */
    public CsvState(String line)
    {
        // initial values
        spacecraftPosition = new double[] { 0.0, 0.0, 0.0 };
        spacecraftVelocity = new double[] { 0.0, 0.0, 0.0 };
        earthPosition = new double[] { 0.0, 0.0, 0.0 };
        sunPosition = new double[] { 0.0, 0.0, 0.0 };
        spacecraftXAxis = new double[] { 1.0, 0.0, 0.0 };
        spacecraftYAxis = new double[] { 0.0, 1.0, 0.0 };
        spacecraftZAxis = new double[] { 0.0, 0.0, 1.0 };

        String[] parts = line.split(",");
        int ntokens = parts.length;

        if (ntokens > 0)
        {
            utc = parts[0].trim();
            ephemerisTime = TimeUtil.str2et(utc);
        }

        if (ntokens > 3)
        {
            sunPosition = new double[]
            {
                Double.parseDouble(parts[1]),
                Double.parseDouble(parts[2]),
                Double.parseDouble(parts[3])
            };
        }

        if (ntokens > 6)
        {
            earthPosition = new double[]
            {
                Double.parseDouble(parts[4]),
                Double.parseDouble(parts[5]),
                Double.parseDouble(parts[6])
            };
        }

        if (ntokens > 9)
        {
            spacecraftPosition = new double[]
            {
                Double.parseDouble(parts[7]),
                Double.parseDouble(parts[8]),
                Double.parseDouble(parts[9])
            };
        }

        if (ntokens > 12)
        {
            spacecraftVelocity = new double[]
            {
                Double.parseDouble(parts[10]),
                Double.parseDouble(parts[11]),
                Double.parseDouble(parts[12])
            };
        }
    }

    @Override
    public double getEphemerisTime()
    {
        return ephemerisTime;
    }

    public void setEphemerisTime(double ephemerisTime)
	{
		this.ephemerisTime = ephemerisTime;
	}

    @Override
    public String getUtc()
    {
        return utc;
    }

//    @Override
//    public int getImageNumber()
//    {
//        // TODO Auto-generated method stub
//        return 0;
//    }
//
//    @Override
//    public int getFrameNumber()
//    {
//        // TODO Auto-generated method stub
//        return 0;
//    }
//
//    @Override
//    public double getViewingAngle()
//    {
//        // TODO Auto-generated method stub
//        return 0;
//    }
//
//    @Override
//    public double getRollAngle()
//    {
//        // TODO Auto-generated method stub
//        return 0;
//    }
//
//    @Override
//    public double getSpacecraftAltitude()
//    {
//        // TODO Auto-generated method stub
//        return 0;
//    }
//
//    @Override
//    public double[] getSurfaceIntercept()
//    {
//        // TODO Auto-generated method stub
//        return null;
//    }
//
//    @Override
//    public double[] getSurfaceInterceptLatLon()
//    {
//        // TODO Auto-generated method stub
//        return null;
//    }
//
//    @Override
//    public double getCrossTrackPixelSpacingKm()
//    {
//        // TODO Auto-generated method stub
//        return 0;
//    }
//
//    @Override
//    public double getAlongTrackPixelSpacing()
//    {
//        // TODO Auto-generated method stub
//        return 0;
//    }
//
//    @Override
//    public double getSolarIncidenceAngle()
//    {
//        // TODO Auto-generated method stub
//        return 0;
//    }
//
//    @Override
//    public double getEmissionAngle()
//    {
//        // TODO Auto-generated method stub
//        return 0;
//    }
//
//    @Override
//    public double getLocalSolarTime()
//    {
//        // TODO Auto-generated method stub
//        return 0;
//    }
//
//    @Override
//    public double[] getSubSolarPointLatLon()
//    {
//        // TODO Auto-generated method stub
//        return null;
//    }
//
//    @Override
//    public float getFrameScore()
//    {
//        // TODO Auto-generated method stub
//        return 0;
//    }

    @Override
    public double[] getSpacecraftPosition()
    {
        return spacecraftPosition;
    }

    @Override
    public double[] getSpacecraftVelocity()
    {
        return spacecraftVelocity;
    }

    @Override
    public double[] getSunPosition()
    {
        return sunPosition;
    }

    @Override
    public double[] getEarthPosition()
    {
        return earthPosition;
    }

    @Override
    public double[][] getSpacecraftAxes()
    {
        return new double[][]{spacecraftXAxis, spacecraftYAxis, spacecraftZAxis};
    }

    @Override
    public double[] getSpacecraftXAxis()
    {
        return spacecraftXAxis;
    }

    @Override
    public double[] getSpacecraftYAxis()
    {
        return spacecraftYAxis;
    }

    @Override
    public double[] getSpacecraftZAxis()
    {
        return spacecraftZAxis;
    }

	@Override
	public double[] getInstrumentLookDirection(String instrumentName)
	{
//		System.out.println("CsvState: getInstrumentLookDirection: ");
		return new double[]{0.0, 0.0, 1.0};
	}

	public UnwritableVectorIJK getFrustum(String instrumentName, int index)
	{
		return new UnwritableVectorIJK(new double[] {0,0,1});
	}

	@Override
	public String toString()
	{
		return "CSV State:  SC Position: " + getSpacecraftPosition()[0] + "," + getSpacecraftPosition()[1] + "," + getSpacecraftPosition()[2];
	}

}
