package edu.jhuapl.sbmt.pointing.io;

public interface PointingFileReader
{

	boolean isPad();

	String getStartTime();

	String getStopTime();

	double[] getSpacecraftPosition();

	double[] getSunPosition();

	double[] getFrustum1();

	double[] getFrustum2();

	double[] getFrustum3();

	double[] getFrustum4();

	double[] getBoresightDirection();

	double[] getUpVector();

	double[] getTargetPixelCoordinates();

	boolean isApplyFrameAdjustments();

	double getRotationOffset();

	double getZoomFactor();

	float getPds_na();

}