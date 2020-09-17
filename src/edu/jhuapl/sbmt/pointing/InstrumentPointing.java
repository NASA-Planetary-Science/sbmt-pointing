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
 * All vectors returned by a pointing are required to be defined in the same
 * target body fixed frame.
 *
 * @author James Peachey
 *
 */
public interface InstrumentPointing
{
    /**
     * Return a vector that gives the spacecraft position in the target body
     * fixed frame. This information is required to define a pointing, so
     * implementations should not return null.
     *
     * @return the spacecraft position vector
     */
    UnwritableVectorIJK getScPosition();

    /**
     * Return a vector that gives the spacecraft velocity in the target body
     * fixed frame. May return null if this pointing does not include this
     * information.
     *
     * @return the spacecraft velocity vector
     */
    UnwritableVectorIJK getScVelocity();

    /**
     * Return a rotation matrix that transforms vectors defined in the
     * spacecraft frame into the target body fixed frame. May return null if
     * this pointing does not include this information.
     *
     * @return the rotation
     */
    UnwritableMatrixIJK getScRotation();

    /**
     * Return a vector that gives the position of the specified body/object in
     * the target body fixed frame. May be null if this pointing cannot provide
     * information about the specified body.
     *
     * @param bodyId The body whose position to return
     *
     * @return the position vector
     */
    UnwritableVectorIJK getPosition(EphemerisID bodyId);

    /**
     * Return a unit vector that indicates the direction of the instrument
     * boresight axis in the target body fixed frame. This information is
     * required to define a pointing, so implementations should not return null.
     *
     * @return the boresight vector
     */
    UnwritableVectorIJK getBoresight();

    /**
     * Return a unit vector that indicates the "up" direction of the instrument
     * in the target body fixed frame. This information is required to define a
     * pointing, so implementations should not return null.
     *
     * @return the up vector
     */
    UnwritableVectorIJK getUpDirection();

    /**
     * Return a collection of unit vectors that together give the corners of the
     * field of view in the target body fixed frame. This information is
     * required to define a pointing, so implementations should not return null.
     *
     * @return the corner vectors
     */
    List<UnwritableVectorIJK> getFrustum();

}
