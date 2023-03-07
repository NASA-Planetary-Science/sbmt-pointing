package edu.jhuapl.sbmt.pointing;

/**
 * Provider of instrument pointings.
 *
 * @author Josh Steele
 *
 */
public interface IPointingProvider
{
    /**
     * Provide an instrument pointing for the specified instrument name at the
     * specified time. Implementations shall not return null, rather throw
     * exceptions if a pointing cannot be provided.
     *
     * @param instrumentName the instrument for which to provide the pointing
     * @param time at which to compute the pointing
     * @return the pointing
     * @throws NullPointerException if the instrument name is null
     * @throws IllegalArgumentException if a pointing cannot be provided
     */
    InstrumentPointing provide(String instrumentName, double time);

    /**
     * Convenience method to provide the instrument pointing for name returned
     * by {@link #getCurrentInstrumentName()}). Equivalent to calling
     * {@link #provide(String, double)} passing the string returned by
     * {@link #getCurrentInstrumentName()} for the instrument name.
     *
     * @param time at which to compute the pointing
     * @return the pointing
     * @throws NullPointerException if the instrument name is null
     * @throws IllegalArgumentException if a pointing cannot be provided
     */
    default InstrumentPointing provide(double time)
    {
        return provide(getCurrentInstrumentName(), time);
    }

    /**
     * Return all instrument names for which complete frame information is
     * available in this provider. Implementations may return null or an empty
     * array.
     *
     * @return the instrument names
     */
    String[] getInstrumentNames();

    /**
     * Return the provider's currently selected instrument name, or null if no
     * instrument name has been set.
     *
     * @return the instrument name
     */
    String getCurrentInstrumentName();

    /**
     * Set the current instrument name to the specified string. The instrument
     * name (if not null) must be one of the names in the array returned by
     * {@link #getInstrumentNames()} (i.e., one of the instruments for which
     * complete frame information is available. The name may be null.
     *
     * @param currentInstrumentName the instrument name
     * @throws IllegalArgumentException if complete
     */
    void setCurrentInstrumentName(String currentInstrumentName);

}
