package edu.jhuapl.sbmt.pointing.io;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.StringTokenizer;

import edu.jhuapl.saavtk.util.MathUtil;
import edu.jhuapl.sbmt.core.io.BasicFileReader;


public class InfoFileReader extends BasicFileReader implements PointingFileReader
{
    public static final float DEFAULT_PDS_NA = -1.e32f;
    public static final String FRUSTUM1 = "FRUSTUM1";
    public static final String FRUSTUM2 = "FRUSTUM2";
    public static final String FRUSTUM3 = "FRUSTUM3";
    public static final String FRUSTUM4 = "FRUSTUM4";
    public static final String BORESIGHT_DIRECTION = "BORESIGHT_DIRECTION";
    public static final String UP_DIRECTION = "UP_DIRECTION";
    public static final String NUMBER_EXPOSURES = "NUMBER_EXPOSURES";
    public static final String START_TIME = "START_TIME";
    public static final String STOP_TIME = "STOP_TIME";
    public static final String SPACECRAFT_POSITION = "SPACECRAFT_POSITION";
    public static final String SUN_POSITION_LT = "SUN_POSITION_LT";
    public static final String TARGET_PIXEL_COORD = "TARGET_PIXEL_COORD";
    public static final String TARGET_ROTATION = "TARGET_ROTATION";
    public static final String TARGET_ZOOM_FACTOR = "TARGET_ZOOM_FACTOR";
    public static final String APPLY_ADJUSTMENTS = "APPLY_ADJUSTMENTS";
    public static final String DISPLAY_RANGE = "DISPLAY_RANGE";
    public static final String OFFLIMB_DISPLAY_RANGE = "OFFLIMB_DISPLAY_RANGE";

    public static final String SUMFILENAMES = "SumfileNames";
    public static final String INFOFILENAMES = "InfofileNames";

    boolean pad;
    String startTime;
    String stopTime;
    double[] spacecraftPosition=new double[3];
    double[] sunPosition=new double[3];
    double[] frustum1=new double[3];
    double[] frustum2=new double[3];
    double[] frustum3=new double[3];
    double[] frustum4=new double[3];
    double[] boresightDirection=new double[3];
    double[] upVector=new double[3];
    double[] targetPixelCoordinates=new double[3];
    boolean applyFrameAdjustments;
    double rotationOffset, zoomFactor;
    float pds_na;

    public InfoFileReader(String filename)
    {
        super(filename);
        this.pds_na=DEFAULT_PDS_NA;
    }

    public InfoFileReader(String filename, float pds_na)
    {
        super(filename);
        this.pds_na = pds_na;
    }

    @Override
    public void read()
    {
        try
        {
            if (getFileName() == null || getFileName().endsWith("null"))
                throw new FileNotFoundException(getFileName());

            boolean offset = true;

            BufferedReader in = new BufferedReader(new InputStreamReader(
                    new FileInputStream(getFileName())));

            String str;
            while ((str = in.readLine()) != null)
            {
                StringTokenizer st = new StringTokenizer(str);
                while (st.hasMoreTokens())
                {
                    String token = st.nextToken();
                    if (token == null)
                        continue;

                    if (START_TIME.equals(token))
                    {
                        st.nextToken();
                        startTime = st.nextToken();
                    }
                    if (STOP_TIME.equals(token))
                    {
                        st.nextToken();
                        stopTime = st.nextToken();
                    }
                    // eventually, we should parse the number of exposures from
                    // the INFO file, for now it is hard-coded -turnerj1
                    // if (NUMBER_EXPOSURES.equals(token))
                    // {
                    // numberExposures = Integer.parseInt(st.nextToken());
                    // if (numberExposures > 1)
                    // {
                    // spacecraftPosition = new double[numberExposures][3];
                    // frustum1 = new double[numberExposures][3];
                    // frustum2 = new double[numberExposures][3];
                    // frustum3 = new double[numberExposures][3];
                    // frustum4 = new double[numberExposures][3];
                    // sunVector = new double[numberExposures][3];
                    // boresightDirection = new double[numberExposures][3];
                    // upVector = new double[numberExposures][3];
                    // frusta = new Frustum[numberExposures];
                    // footprint = new vtkPolyData[numberExposures];
                    // footprintCreated = new boolean[numberExposures];
                    // shiftedFootprint = new vtkPolyData[numberExposures];
                    // }
                    // }
                    // For backwards compatibility with MSI images we use the
                    // endsWith function
                    // rather than equals for FRUSTUM1, FRUSTUM2, FRUSTUM3,
                    // FRUSTUM4, BORESIGHT_DIRECTION
                    // and UP_DIRECTION since these are all prefixed with MSI_
                    // in the info file.
                    if (token.equals(TARGET_PIXEL_COORD))
                    {
                        st.nextToken();
                        st.nextToken();
                        double x = Double.parseDouble(st.nextToken());
                        st.nextToken();
                        double y = Double.parseDouble(st.nextToken());
                        targetPixelCoordinates[0] = x;
                        targetPixelCoordinates[1] = y;
                    }
                    if (token.equals(TARGET_ROTATION))
                    {
                        st.nextToken();
                        double x = Double.parseDouble(st.nextToken());
                        rotationOffset = x;
                    }
                    if (token.equals(TARGET_ZOOM_FACTOR))
                    {
                        st.nextToken();
                        double x = Double.parseDouble(st.nextToken());
                        zoomFactor = x;
                    }
                    if (token.equals(APPLY_ADJUSTMENTS))
                    {
                        st.nextToken();
                        offset = Boolean.parseBoolean(st.nextToken());
                        applyFrameAdjustments = offset;
                    }

                    if (SPACECRAFT_POSITION.equals(token)
                            || SUN_POSITION_LT.equals(token)
                            || token.endsWith(FRUSTUM1)
                            || token.endsWith(FRUSTUM2)
                            || token.endsWith(FRUSTUM3)
                            || token.endsWith(FRUSTUM4)
                            || token.endsWith(BORESIGHT_DIRECTION)
                            || token.endsWith(UP_DIRECTION))
                    {
                        st.nextToken();
                        st.nextToken();
                        double x = Double.parseDouble(st.nextToken());
                        st.nextToken();
                        double y = Double.parseDouble(st.nextToken());
                        st.nextToken();
                        double z = Double.parseDouble(st.nextToken());
                        if (SPACECRAFT_POSITION.equals(token))
                        {
                            // SPACECRAFT_POSITION is assumed to be at the start
                            // of a frame, so increment slice count
                            spacecraftPosition[0] = x;
                            spacecraftPosition[1] = y;
                            spacecraftPosition[2] = z;
                        }
                        if (SUN_POSITION_LT.equals(token))
                        {
                            sunPosition[0] = x;
                            sunPosition[1] = y;
                            sunPosition[2] = z;
                            // MathUtil.vhat(sunPosition, sunPosition);
                        }
                        else if (token.endsWith(FRUSTUM1))
                        {
                            frustum1[0] = x;
                            frustum1[1] = y;
                            frustum1[2] = z;
                            MathUtil.vhat(frustum1, frustum1);
                        }
                        else if (token.endsWith(FRUSTUM2))
                        {
                            frustum2[0] = x;
                            frustum2[1] = y;
                            frustum2[2] = z;
                            MathUtil.vhat(frustum2, frustum2);
                        }
                        else if (token.endsWith(FRUSTUM3))
                        {
                            frustum3[0] = x;
                            frustum3[1] = y;
                            frustum3[2] = z;
                            MathUtil.vhat(frustum3, frustum3);
                        }
                        else if (token.endsWith(FRUSTUM4))
                        {
                            frustum4[0] = x;
                            frustum4[1] = y;
                            frustum4[2] = z;
                            MathUtil.vhat(frustum4, frustum4);
                        }
                        if (token.endsWith(BORESIGHT_DIRECTION))
                        {
                            boresightDirection[0] = x;
                            boresightDirection[1] = y;
                            boresightDirection[2] = z;
                        }
                        if (token.endsWith(UP_DIRECTION))
                        {
                            upVector[0] = x;
                            upVector[1] = y;
                            upVector[2] = z;
                        }
                    }
                }
            }

            // once we've read in all the frames, pad out any additional missing
            // frames
            if (pad)
            {
                spacecraftPosition[0] = spacecraftPosition[0];
                spacecraftPosition[1] = spacecraftPosition[1];
                spacecraftPosition[2] = spacecraftPosition[2];

                sunPosition[0] = sunPosition[0];
                sunPosition[1] = sunPosition[1];
                sunPosition[2] = sunPosition[2];

                frustum1[0] = frustum1[0];
                frustum1[1] = frustum1[1];
                frustum1[2] = frustum1[2];

                frustum2[0] = frustum2[0];
                frustum2[1] = frustum2[1];
                frustum2[2] = frustum2[2];

                frustum3[0] = frustum3[0];
                frustum3[1] = frustum3[1];
                frustum3[2] = frustum3[2];

                frustum4[0] = frustum4[0];
                frustum4[1] = frustum4[1];
                frustum4[2] = frustum4[2];

                boresightDirection[0] = boresightDirection[0];
                boresightDirection[1] = boresightDirection[1];
                boresightDirection[2] = boresightDirection[2];

                upVector[0] = upVector[0];
                upVector[1] = upVector[1];
                upVector[2] = upVector[2];
            }

            in.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    @Override
	public boolean isPad()
    {
        return pad;
    }

    @Override
	public String getStartTime()
    {
        return startTime;
    }

    @Override
	public String getStopTime()
    {
        return stopTime;
    }

    @Override
	public double[] getSpacecraftPosition()
    {
        return spacecraftPosition;
    }

    @Override
	public double[] getSunPosition()
    {
        return sunPosition;
    }

    @Override
	public double[] getFrustum1()
    {
        return frustum1;
    }

    @Override
	public double[] getFrustum2()
    {
        return frustum2;
    }

    @Override
	public double[] getFrustum3()
    {
        return frustum3;
    }

    @Override
	public double[] getFrustum4()
    {
        return frustum4;
    }

    @Override
	public double[] getBoresightDirection()
    {
        return boresightDirection;
    }

    @Override
	public double[] getUpVector()
    {
        return upVector;
    }

    @Override
	public double[] getTargetPixelCoordinates()
    {
        return targetPixelCoordinates;
    }

    @Override
	public boolean isApplyFrameAdjustments()
    {
        return applyFrameAdjustments;
    }

    @Override
	public double getRotationOffset()
    {
        return rotationOffset;
    }

    @Override
	public double getZoomFactor()
    {
        return zoomFactor;
    }

    @Override
	public float getPds_na()
    {
        return pds_na;
    }



}
