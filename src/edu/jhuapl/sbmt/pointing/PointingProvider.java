package edu.jhuapl.sbmt.pointing;

import crucible.core.time.TSEpoch;

/**
 * Interface defining core behavior for an object that can return a
 * {@link Pointing} for the specified time.
 *
 * @author James Peachey
 *
 */
public interface PointingProvider
{

    /**
     * Return a pointing object that is valid at the specified time.
     *
     * @param time the {@link TSEpoch} at which to compute the pointing
     * @return the {@link Pointing} object
     */
    Pointing provide(TSEpoch time);

}
