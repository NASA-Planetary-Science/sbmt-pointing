package edu.jhuapl.sbmt.pointing;

import edu.jhuapl.sbmt.core.pointing.InstrumentPointing;

import crucible.core.math.vectorspace.UnwritableVectorIJK;

public abstract class AbstractInstrumentPointing implements InstrumentPointing
{
    protected AbstractInstrumentPointing()
    {
        super();
    }

    /**
     * Return a vector in the same direction as the input vector, but guaranteed
     * unwritable, and normalized to length 1 (unit vector) if possible. This
     * method is needed because similar methods of {@link UnwritableVectorIJK}
     * have some undesirable corner-case behaviors.
     * <p>
     * Provided all components of the provided vector are finite, the result is
     * guaranteed to be either a unit vector or a vector of length 0, with no
     * exceptions thrown.
     *
     * @param vector the input vector
     * @return a vector in the same direction as the input vector, but with
     *         length equal to 1, or else with length equal to 0 if the input
     *         vector has 0 length
     */
    public static UnwritableVectorIJK normalize(UnwritableVectorIJK vector)
    {
        // If the norm is anything other than 0 or 1, need to normalize the
        // input.
        double norm = vector.getLength();
        boolean normalize = norm != 1. && norm != 0.;

        // Make a copy in order to normalize or to ensure unwritable.
        boolean copy = normalize || vector.getClass() != UnwritableVectorIJK.class;

        if (copy)
        {
            vector = normalize ? new UnwritableVectorIJK(1.0 / norm, vector) : new UnwritableVectorIJK(vector);
        }

        return vector;
    }

}
