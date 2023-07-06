package edu.jhuapl.sbmt.pointing.io;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import edu.jhuapl.sbmt.core.io.BasicFileWriter;

public class InfoFileWriter extends BasicFileWriter
{
	private final PointingFileReader pointing;
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
    public static final String DISPLAY_RANGE = "DISPLAY_RANGE";
    public static final String OFFLIMB_DISPLAY_RANGE = "OFFLIMB_DISPLAY_RANGE";
    public static final String TARGET_PIXEL_COORD = "TARGET_PIXEL_COORD";
    public static final String TARGET_ROTATION = "TARGET_ROTATION";
    public static final String TARGET_ZOOM_FACTOR = "TARGET_ZOOM_FACTOR";
    public static final String APPLY_ADJUSTMENTS = "APPLY_ADJUSTMENTS";
    private boolean adjusted;

	public InfoFileWriter(String filename, PointingFileReader pointing, boolean adjusted)
	{
		super(filename);
		this.pointing = pointing;
		this.adjusted = adjusted;
	}

	@Override
	public void write()
	{
		FileOutputStream fs = null;

		// save out info file to cache with ".adjusted" appended to the name
//		boolean flatten = true;
		String suffix = adjusted? ".adjusted" : "";
		try
		{
			fs = new FileOutputStream(filename + suffix);
		} catch (FileNotFoundException e)
		{
			e.printStackTrace();
			return;
		}
		OutputStreamWriter osw = new OutputStreamWriter(fs);
		BufferedWriter out = new BufferedWriter(osw);

		try
		{
			out.write(String.format("%-22s= %s\n", START_TIME, pointing.getStartTime()));
			out.write(String.format("%-22s= %s\n", STOP_TIME, pointing.getStopTime()));
			out.write(String.format("%-22s= ( %1.16e , %1.16e , %1.16e )\n", SPACECRAFT_POSITION,
					pointing.getSpacecraftPosition()[0], pointing.getSpacecraftPosition()[1], pointing.getSpacecraftPosition()[2]));
			out.write(String.format("%-22s= ( %1.16e , %1.16e , %1.16e )\n", BORESIGHT_DIRECTION,
					pointing.getBoresightDirection()[0], pointing.getBoresightDirection()[1], pointing.getBoresightDirection()[2]));
			out.write(String.format("%-22s= ( %1.16e , %1.16e , %1.16e )\n", UP_DIRECTION, pointing.getUpVector()[0],
					pointing.getUpVector()[1], pointing.getUpVector()[2]));
			out.write(String.format("%-22s= ( %1.16e , %1.16e , %1.16e )\n", FRUSTUM1, pointing.getFrustum1()[0],
					pointing.getFrustum1()[1], pointing.getFrustum1()[2]));
			out.write(String.format("%-22s= ( %1.16e , %1.16e , %1.16e )\n", FRUSTUM2, pointing.getFrustum2()[0],
					pointing.getFrustum2()[1], pointing.getFrustum2()[2]));
			out.write(String.format("%-22s= ( %1.16e , %1.16e , %1.16e )\n", FRUSTUM3, pointing.getFrustum3()[0],
					pointing.getFrustum3()[1], pointing.getFrustum3()[2]));
			out.write(String.format("%-22s= ( %1.16e , %1.16e , %1.16e )\n", FRUSTUM4, pointing.getFrustum4()[0],
					pointing.getFrustum4()[1], pointing.getFrustum4()[2]));
			out.write(String.format("%-22s= ( %1.16e , %1.16e , %1.16e )\n", SUN_POSITION_LT, pointing.getSunPosition()[0],
					pointing.getSunPosition()[1], pointing.getSunPosition()[2]));

			//TODO FIX THIS
//			out.write(String.format("%-22s= ( %16d , %16d )\n", DISPLAY_RANGE, displayRange.min, displayRange.max));
//			out.write(String.format("%-22s= ( %16d , %16d )\n", OFFLIMB_DISPLAY_RANGE, offLimbDisplayRange.min,
//					offLimbDisplayRange.max));
	//
//			boolean writeApplyAdustments = false;
	//
//			if (!flatten)
//			{
//				if (targetPixelCoordinates[0] != Double.MAX_VALUE && targetPixelCoordinates[1] != Double.MAX_VALUE)
//				{
//					out.write(String.format("%-22s= ( %1.16e , %1.16e )\n", TARGET_PIXEL_COORD, targetPixelCoordinates[0],
//							targetPixelCoordinates[1]));
//					writeApplyAdustments = true;
//				}
	//
//				if (zoomFactor[0] != 1.0)
//				{
//					out.write(String.format("%-22s= %1.16e\n", TARGET_ZOOM_FACTOR, zoomFactor[0]));
//					writeApplyAdustments = true;
//				}
	//
//				if (rotationOffset[0] != 0.0)
//				{
//					out.write(String.format("%-22s= %1.16e\n", TARGET_ROTATION, rotationOffset[0]));
//					writeApplyAdustments = true;
//				}
	//
//				// only write out user-modified offsets if the image info has been
//				// modified
//				if (writeApplyAdustments)
//					out.write(String.format("%-22s= %b\n", APPLY_ADJUSTMENTS, applyFrameAdjustments));
//			}

			out.close();
		} catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
