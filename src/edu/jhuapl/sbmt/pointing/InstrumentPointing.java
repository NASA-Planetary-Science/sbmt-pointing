package edu.jhuapl.sbmt.pointing;

import java.util.List;

import crucible.core.math.vectorspace.UnwritableVectorIJK;

/**
 * Encapsulation of a pointing of a specific instrument on a specific
 * spacecraft, which is pointing at a specific target body. This uses crucible
 * abstractions to represent typical attributes (position, orientation)
 * describing the spacecraft/instrument at the time of an event occurring on the
 * spacecraft, such as capturing an image at a specified moment in time.
 * <p>
 * All vectors returned by a pointing are required to be in one frame, called
 * the center frame, which may or may not have its origin at the center of mass
 * of the target body.
 *
 * @author James Peachey
 *
 */
public interface InstrumentPointing
{
    /**
     * Return a vector that gives the spacecraft position relative to the body
     * fixed frame.
     *
     * @return the spacecraft position vector
     */
    UnwritableVectorIJK getSpacecraftPos();

    /**
     * Return a vector that gives the position of the specified body relative to
     * the body fixed frame. May be null if this pointing does not contain
     * information about the specified body.
     * <p>
     * TODO pass the body name as an argument and drop the "Sun" in the method
     * name.
     *
     * @return the position vector
     */
    UnwritableVectorIJK getSunPos();

    /**
     * Return a unit vector that indicates the direction of the instrument
     * boresight axis.
     *
     * @return the boresight vector
     */
    UnwritableVectorIJK getBoresight();

    /**
     * Return a unit vector that indicates the "up" direction of the instrument.
     *
     * @return the up vector
     */
    UnwritableVectorIJK getUp();

    /**
     * Return a collection of unit vectors that together give the corners of the
     * field of view.
     *
     * @return the corner vectors
     */
    List<UnwritableVectorIJK> getFrustum();

}
