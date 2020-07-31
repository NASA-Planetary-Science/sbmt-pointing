package edu.jhuapl.sbmt.pointing.spice;

import java.util.List;

import edu.jhuapl.sbmt.pointing.AbstractInstrumentPointing;

import crucible.core.math.vectorspace.UnwritableVectorIJK;

public final class SpiceInstrumentPointing extends AbstractInstrumentPointing
{
    private final UnwritableVectorIJK scPos;
    private final UnwritableVectorIJK sunPos;
    private final UnwritableVectorIJK boresight;
    private final UnwritableVectorIJK upDir;
    private final List<UnwritableVectorIJK> frustum;

    public SpiceInstrumentPointing( //
            UnwritableVectorIJK scPos, //
            UnwritableVectorIJK sunPos, //
            UnwritableVectorIJK boresight, //
            UnwritableVectorIJK upDir, //
            List<UnwritableVectorIJK> frustum
    )
    {
        this.scPos = UnwritableVectorIJK.copyOf(scPos);
        this.sunPos = UnwritableVectorIJK.copyOf(sunPos);
        this.boresight = normalize(boresight);
        this.upDir = normalize(upDir);
        this.frustum = frustum;
    }

    /**
     * Return a vector that gives the spacecraft position relative to the body
     * fixed frame.
     *
     * @return the spacecraft position vector
     */
    public UnwritableVectorIJK getSpacecraftPos()
    {
        return scPos;
    }

    /**
     * Return a vector that gives the sun position relative to the body fixed
     * frame.
     *
     * @return the sun position vector
     */
    public UnwritableVectorIJK getSunPos()
    {
        return sunPos;
    }

    /**
     * Return a unit vector that indicates the direction of the instrument
     * boresight axis.
     *
     * @return the boresight vector
     */
    public UnwritableVectorIJK getBoresight()
    {
        return boresight;
    }

    /**
     * Return a unit vector that indicates the "up" direction of the instrument.
     *
     * @return the up vector
     */
    public UnwritableVectorIJK getUp()
    {
        return upDir;
    }

    /**
     * Return an object that represents the frustum of the instrument.
     *
     * @return the frustum
     */
    public List<UnwritableVectorIJK> getFrustum()
    {
        return frustum;
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
