package edu.jhuapl.sbmt.pointing.io;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import edu.jhuapl.saavtk.util.DateTimeUtil;
import edu.jhuapl.saavtk.util.MathUtil;
import edu.jhuapl.sbmt.core.io.BasicFileReader;

public class SumFileReader extends BasicFileReader<IOException> implements PointingFileReader
{
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
    int imageHeight, imageWidth;

	public SumFileReader(String sumfilename) throws IOException
	{
		super(sumfilename);
	}

	@Override
    public void read() throws IOException
    {
		FileInputStream fs = new FileInputStream(filename);
        InputStreamReader isr = new InputStreamReader(fs);
        BufferedReader in = new BufferedReader(isr);

        in.readLine();

        String datetime = in.readLine().trim();
        datetime = DateTimeUtil.convertDateTimeFormat(datetime);
        startTime = datetime;
        stopTime = datetime;

        String[] tmp = in.readLine().trim().split("\\s+");
        double npx = Integer.parseInt(tmp[0]);
        double nln = Integer.parseInt(tmp[1]);

        tmp = in.readLine().trim().split("\\s+");
        replaceDwithE(tmp);
        double focalLengthMillimeters = Double.parseDouble(tmp[0]);

        tmp = in.readLine().trim().split("\\s+");
        replaceDwithE(tmp);
        spacecraftPosition[0] = -Double.parseDouble(tmp[0]);
        spacecraftPosition[1] = -Double.parseDouble(tmp[1]);
        spacecraftPosition[2] = -Double.parseDouble(tmp[2]);

        double[] cx = new double[3];
        double[] cy = new double[3];
        double[] cz = new double[3];
        double[] sz = new double[3];

        tmp = in.readLine().trim().split("\\s+");
        replaceDwithE(tmp);
        cx[0] = Double.parseDouble(tmp[0]);
        cx[1] = Double.parseDouble(tmp[1]);
        cx[2] = Double.parseDouble(tmp[2]);

        tmp = in.readLine().trim().split("\\s+");
        replaceDwithE(tmp);
        cy[0] = Double.parseDouble(tmp[0]);
        cy[1] = Double.parseDouble(tmp[1]);
        cy[2] = Double.parseDouble(tmp[2]);

        tmp = in.readLine().trim().split("\\s+");
        replaceDwithE(tmp);
        cz[0] = Double.parseDouble(tmp[0]);
        cz[1] = Double.parseDouble(tmp[1]);
        cz[2] = Double.parseDouble(tmp[2]);

        tmp = in.readLine().trim().split("\\s+");
        replaceDwithE(tmp);
        sz[0] = Double.parseDouble(tmp[0]);
        sz[1] = Double.parseDouble(tmp[1]);
        sz[2] = Double.parseDouble(tmp[2]);

        tmp = in.readLine().trim().split("\\s+");
        replaceDwithE(tmp);
        double kmatrix00 = Math.abs(Double.parseDouble(tmp[0]));
        double kmatrix11 = Math.abs(Double.parseDouble(tmp[4]));

        // Here we calculate the image width and height using the K-matrix values.
        // This is used only when the constructor of this function was called with
        // loadPointingOnly set to true. When set to false, the image width and
        // and height is set in the loadImage function (after this function is called
        // and will overwrite these values here--though they should not be different).
        // But when in pointing-only mode, the loadImage function is not called so
        // we therefore set the image width and height here since some functions need
        // it.
        imageWidth = (int) npx;
        imageHeight = (int) nln;
        if (kmatrix00 > kmatrix11)
            imageHeight = (int) Math.round(nln * (kmatrix00 / kmatrix11));
        else if (kmatrix11 > kmatrix00)
            imageWidth = (int) Math.round(npx * (kmatrix11 / kmatrix00));

        double[] cornerVector = new double[3];
        double fov1 = Math.atan(npx / (2.0 * focalLengthMillimeters * kmatrix00));
        double fov2 = Math.atan(nln / (2.0 * focalLengthMillimeters * kmatrix11));
        cornerVector[0] = -Math.tan(fov1);
        cornerVector[1] = -Math.tan(fov2);
        cornerVector[2] = 1.0;

        double fx = cornerVector[0];
        double fy = cornerVector[1];
        double fz = cornerVector[2];
        frustum3[0] = fx * cx[0] + fy * cy[0] + fz * cz[0];
        frustum3[1] = fx * cx[1] + fy * cy[1] + fz * cz[1];
        frustum3[2] = fx * cx[2] + fy * cy[2] + fz * cz[2];

        fx = -cornerVector[0];
        fy = cornerVector[1];
        fz = cornerVector[2];
        frustum4[0] = fx * cx[0] + fy * cy[0] + fz * cz[0];
        frustum4[1] = fx * cx[1] + fy * cy[1] + fz * cz[1];
        frustum4[2] = fx * cx[2] + fy * cy[2] + fz * cz[2];


        fx = cornerVector[0];
        fy = -cornerVector[1];
        fz = cornerVector[2];
        frustum1[0] = fx * cx[0] + fy * cy[0] + fz * cz[0];
        frustum1[1] = fx * cx[1] + fy * cy[1] + fz * cz[1];
        frustum1[2] = fx * cx[2] + fy * cy[2] + fz * cz[2];

        fx = -cornerVector[0];
        fy = -cornerVector[1];
        fz = cornerVector[2];
        frustum2[0] = fx * cx[0] + fy * cy[0] + fz * cz[0];
        frustum2[1] = fx * cx[1] + fy * cy[1] + fz * cz[1];
        frustum2[2] = fx * cx[2] + fy * cy[2] + fz * cz[2];

        MathUtil.vhat(frustum1, frustum1);
        MathUtil.vhat(frustum2, frustum2);
        MathUtil.vhat(frustum3, frustum3);
        MathUtil.vhat(frustum4, frustum4);

        MathUtil.vhat(cz, boresightDirection);
        MathUtil.vhat(cx, upVector);
        MathUtil.vhat(sz, sunPosition);

        in.close();
	}

	/**
     * Sometimes Bob Gaskell sumfiles contain numbers of the form .1192696009D+03
     * rather than .1192696009E+03 (i.e. a D instead of an E). This function
     * replaces D's with E's.
     *
     * @param s
     * @return
     */
    private void replaceDwithE(String[] s)
    {
        for (int i = 0; i < s.length; ++i)
            s[i] = s[i].replace('D', 'E');
    }

    public boolean isPad()
    {
        return pad;
    }

    public String getStartTime()
    {
        return startTime;
    }

    public String getStopTime()
    {
        return stopTime;
    }

    public double[] getSpacecraftPosition()
    {
        return spacecraftPosition;
    }

    public double[] getSunPosition()
    {
        return sunPosition;
    }

    public double[] getFrustum1()
    {
        return frustum1;
    }

    public double[] getFrustum2()
    {
        return frustum2;
    }

    public double[] getFrustum3()
    {
        return frustum3;
    }

    public double[] getFrustum4()
    {
        return frustum4;
    }

    public double[] getBoresightDirection()
    {
        return boresightDirection;
    }

    public double[] getUpVector()
    {
        return upVector;
    }

    public double[] getTargetPixelCoordinates()
    {
        return targetPixelCoordinates;
    }

    public boolean isApplyFrameAdjustments()
    {
        return applyFrameAdjustments;
    }

    public double getRotationOffset()
    {
        return rotationOffset;
    }

    public double getZoomFactor()
    {
        return zoomFactor;
    }

    public float getPds_na()
    {
        return pds_na;
    }
}
