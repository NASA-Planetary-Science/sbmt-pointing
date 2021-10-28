package edu.jhuapl.sbmt.pointing;

public interface IPointingProvider
{
	public InstrumentPointing provide(String instFrameName, double time);

	public InstrumentPointing provide(double time);

	public String[] getInstrumentNames();

	public String getCurrentInstFrameName();

	public void setCurrentInstFrameName(String currentInstFrameName);
}
