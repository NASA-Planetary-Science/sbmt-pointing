package edu.jhuapl.sbmt.pointing;

import java.util.List;

import crucible.core.math.vectorspace.UnwritableMatrixIJK;
import crucible.core.math.vectorspace.UnwritableVectorIJK;
import crucible.core.mechanics.EphemerisID;

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
     * Return a vector that gives the spacecraft position in the body fixed
     * frame.
     *
     * @return the spacecraft position vector
     */
    UnwritableVectorIJK getScPosition();

    /**
     * Return a vector that gives the spacecraft velocity in the body fixed
     * frame.
     *
     * @return the spacecraft velocity vector
     */
    UnwritableVectorIJK getScVelocity();

    /**
     * Return a rotation vector that gives the orientation of the spacecraft in
     * the body fixed frame.
     *
     * @return the rotation
     */
    UnwritableMatrixIJK getScOrientation();

    /**
     * Return a vector that gives the position of the specified body relative to
     * the body fixed frame. May be null if this pointing does not contain
     * information about the specified body.
     *
     * @param bodyId The body whose position to return
     *
     * @return the position vector
     */
    UnwritableVectorIJK getPosition(EphemerisID bodyId);

    /**
     * Return a unit vector that indicates the direction of the instrument
     * boresight axis in the body fixed frame.
     *
     * @return the boresight vector
     */
    UnwritableVectorIJK getBoresight();

    /**
     * Return a unit vector that indicates the "up" direction of the instrument
     * in the body fixed frame.
     *
     * @return the up vector
     */
    UnwritableVectorIJK getUpDirection();

    /**
     * Return a collection of unit vectors that together give the corners of the
     * field of view in the body fixed frame.
     *
     * @return the corner vectors
     */
    List<UnwritableVectorIJK> getFrustum();

}
