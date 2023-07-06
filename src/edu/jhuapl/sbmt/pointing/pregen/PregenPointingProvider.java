package edu.jhuapl.sbmt.pointing.pregen;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.NavigableMap;
import java.util.TreeMap;

import org.joda.time.DateTime;

import com.google.common.base.Preconditions;

import edu.jhuapl.saavtk.util.FileCache;
import edu.jhuapl.sbmt.core.pointing.InstrumentPointing;
import edu.jhuapl.sbmt.pointing.IPointingProvider;
import edu.jhuapl.sbmt.pointing.StateHistoryUtil;
import edu.jhuapl.sbmt.pointing.scState.CsvState;
import edu.jhuapl.sbmt.stateHistory.model.interfaces.State;

public abstract class PregenPointingProvider implements IPointingProvider
{

	public PregenPointingProvider()
	{
		// TODO Auto-generated constructor stub
	}

	public static class Builder
    {
		private final File path;
		private final DateTime startTime;
		private final DateTime endTime;
		private NavigableMap<Double, State> timeToStateMap = new TreeMap<Double, State>();

		protected Builder(String filename, DateTime startTime, DateTime endTime)
		{
			super();
			this.path = FileCache.getFileFromServer(filename);
			this.startTime = startTime;
			this.endTime = endTime;
		}

		public PregenPointingProvider build()
		{
			final int lineLength = 121;
			String startString = startTime.toString().substring(0, 23);
			String endString = endTime.toString().substring(0, 23);
			int positionStart = StateHistoryUtil.binarySearch(1, (int) StateHistoryUtil.getBinaryFileLength(path, lineLength), startString, false, lineLength, path);
			int positionEnd = StateHistoryUtil.binarySearch(1, (int) StateHistoryUtil.getBinaryFileLength(path, lineLength), endString, true, lineLength, path);

			for (int i = positionStart; i <= positionEnd; i += lineLength)
			{
				//populate the position array at this index
				int[] position = new int[12];
				for (int j = 0; j < position.length; j++)
				{
					position[j] = i + 25 + (j * 8);
				}

				//populate a flyby state object, and use it to populate the history and trajectory
				State state = new CsvState(i, path, position);
				timeToStateMap.put(state.getEphemerisTime(), state);

			}

			return new PregenPointingProvider() {

				@Override
				public NavigableMap<Double, State> getStateMap()
				{
					return timeToStateMap;
				}
			};
		}
    }

	public static class CSVBuilder
    {
		private final File path;
		private final double startTime;
		private final double endTime;
		private NavigableMap<Double, State> timeToStateMap = new TreeMap<Double, State>();

		protected CSVBuilder(String filename, double startTime, double endTime)
		{
			super();
			this.path = new File(filename);
			this.startTime = startTime;
			this.endTime = endTime;
		}

		public PregenPointingProvider build()
		{
			BufferedReader in;
			try
			{
				in = new BufferedReader(new FileReader(path));

	            in.readLine();

	            // get name, desc, color form second line
	            String info = in.readLine();


	            // discard third line of headers
	            in.readLine();

	            String line;
	            while ((line = in.readLine()) != null)
	            {
	                // parse line of file
	                State state = new CsvState(line);
	                timeToStateMap.put(state.getEphemerisTime(), state);

	            }
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

			return new PregenPointingProvider() {

				@Override
				public NavigableMap<Double, State> getStateMap()
				{
					return timeToStateMap;
				}
			};

		}
    }

	public static CSVBuilder builder(String filename, double startTime, double endTime)
	{
		return new CSVBuilder(filename, startTime, endTime);
	}

	public static Builder builder(String filename, DateTime startTime, DateTime endTime)
	{
		return new Builder(filename, startTime, endTime);
	}

	public InstrumentPointing provide(String instrumentName, double time)
	{
		Preconditions.checkNotNull(time);
		State state = getStateMap().floorEntry(time).getValue();
		return new PregenInstrumentPointing(state);
	}

	public String[] getInstrumentNames()
	{
		return new String[] {};
	}

	public abstract NavigableMap<Double, State> getStateMap();

	public String getCurrentInstrumentName()
	{
		return null;
	}

	/**
	 * @param currentInstrumentName the currentInstName to set
	 */
	public void setCurrentInstrumentName(String currentInstrumentName)
	{

	}
}
