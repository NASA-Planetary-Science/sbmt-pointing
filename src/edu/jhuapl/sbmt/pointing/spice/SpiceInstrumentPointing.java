package edu.jhuapl.sbmt.pointing.spice;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.jhuapl.sbmt.pointing.AbstractInstrumentPointing;

import crucible.core.math.vectorspace.UnwritableVectorIJK;
import crucible.core.mechanics.Coverage;
import crucible.core.mechanics.EphemerisID;
import crucible.core.mechanics.FrameID;
import crucible.core.mechanics.StateVector;
import crucible.core.mechanics.StateVectorFunction;
import crucible.core.mechanics.StateVectorFunctions;
import crucible.core.mechanics.UnwritableStateVector;
import crucible.core.mechanics.providers.aberrated.AberratedEphemerisProvider;
import crucible.core.mechanics.providers.aberrated.AberratedStateVectorFunction;
import crucible.core.mechanics.providers.aberrated.AberrationCorrection;

public final class SpiceInstrumentPointing extends AbstractInstrumentPointing
{
    private final AberratedEphemerisProvider ephProvider;
    private final EphemerisID scId;
    private final FrameID instFrame;
    private final EphemerisID targetId;
    private final FrameID centerFrameId;
    private final UnwritableVectorIJK boresight;
    private final UnwritableVectorIJK upDir;
    private final List<UnwritableVectorIJK> frustum;
    private final double time;
    private final Map<EphemerisID, UnwritableStateVector> bodyStates;
    private UnwritableVectorIJK scPos;
    private double timeAtTarget;

    public SpiceInstrumentPointing( //
            AberratedEphemerisProvider ephProvider, //
            EphemerisID scId, //
            FrameID instFrame, //
            EphemerisID targetId, //
            FrameID centerFrameId, //
            UnwritableVectorIJK boresight, //
            UnwritableVectorIJK upDir, //
            List<UnwritableVectorIJK> frustum, //
            double time //
    )
    {
        this.ephProvider = ephProvider;
        this.scId = scId;
        this.instFrame = instFrame;
        this.targetId = targetId;
        this.centerFrameId = centerFrameId;
        this.boresight = normalize(boresight);
        this.upDir = normalize(upDir);
        this.frustum = frustum;
        this.time = time;
        this.bodyStates = new HashMap<>();
        this.scPos = null;
        this.timeAtTarget = -1.; // Just for debugging, so one can easily tell whether it has been computed yet.
    }

    /**
     * Return a vector that gives the spacecraft position relative to the body
     * fixed frame.
     *
     * @return the spacecraft position vector
     */
    public UnwritableVectorIJK getSpacecraftPos()
    {
        computeScPointing();

        return scPos;
    }

    /**
     * Return a vector that gives the sun position relative to the body fixed
     * frame.
     *
     * @return the sun position vector
     */
    public UnwritableVectorIJK getPos(EphemerisID bodyId)
    {
        UnwritableStateVector bodyState = bodyStates.get(bodyId);
        if (bodyState == null)
        {
            computeScPointing(); // Needed so timeAtTarget is computed correctly.

            AberratedStateVectorFunction bodyFromTarget = ephProvider.createAberratedStateVectorFunction(bodyId, targetId, centerFrameId, Coverage.ALL_TIME, AberrationCorrection.LT_S);
            bodyState = UnwritableStateVector.copyOf(bodyFromTarget.getState(timeAtTarget));

            bodyStates.put(bodyId, bodyState);
        }

        return bodyState.getPosition();
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

    private void computeScPointing()
    {
        if (scPos == null)
        {
            AberratedStateVectorFunction bodyFromSc = ephProvider.createAberratedStateVectorFunction(targetId, scId, centerFrameId, Coverage.ALL_TIME, AberrationCorrection.LT_S);

            // Need spacecraft-from-body as well as the body-from-spacecraft
            // frame calculations.
            StateVectorFunction scFromBody = StateVectorFunctions.negate(bodyFromSc);

            // Get position of body relative to spacecraft.
            StateVector bodyFromScState = scFromBody.getState(time);

            scPos = UnwritableVectorIJK.copyOf(bodyFromScState.getPosition());

            timeAtTarget = time - bodyFromSc.getLightTime(time);
        }
    }

}
