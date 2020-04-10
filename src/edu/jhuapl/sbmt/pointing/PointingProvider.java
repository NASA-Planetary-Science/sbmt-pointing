package edu.jhuapl.sbmt.pointing;

public interface PointingProvider
{

    TimeRange getValidTimeRange();

    Pointing provide(double time);

}
