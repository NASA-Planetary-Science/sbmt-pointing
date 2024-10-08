package edu.jhuapl.sbmt.pointing.spice;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableList;

import edu.jhuapl.sbmt.pointing.AbstractInstrumentPointing;
import edu.jhuapl.sbmt.pointing.InstrumentPointing;
import picante.math.vectorspace.RotationMatrixIJK;
import picante.math.vectorspace.UnwritableMatrixIJK;
import picante.math.vectorspace.UnwritableVectorIJK;
import picante.mechanics.CelestialFrames;
import picante.mechanics.Coverage;
import picante.mechanics.EphemerisID;
import picante.mechanics.FrameID;
import picante.mechanics.FrameTransformFunction;
import picante.mechanics.StateTransformFunction;
import picante.mechanics.StateVector;
import picante.mechanics.StateVectorFunction;
import picante.mechanics.StateVectorFunctions;
import picante.mechanics.UnwritableStateVector;
import picante.mechanics.providers.aberrated.AberratedEphemerisProvider;
import picante.mechanics.providers.aberrated.AberratedStateVectorFunction;
import picante.mechanics.providers.aberrated.AberrationCorrection;

/**
 * SPICE-based implementation of {@link InstrumentPointing}. Mostly immutable,
 * with a few quantities computed on-demand and cached. The FOV in the
 * instrument frame is stored in fields, along with an
 * {@link AberratedEphemerisProvider}, which is used to provide the
 * {@link StateTransformFunction}s and {@link FrameTransformFunction}s necessary
 * for computing quantities for the frame/time of interest.
 *
 * @author James Peachey
 *
 */
final class SpiceInstrumentPointing extends AbstractInstrumentPointing
{
    private final AberratedEphemerisProvider ephProvider;
    private final EphemerisID targetId;
    private final FrameID targetFrame;
    private final EphemerisID scId;
    private final FrameID scFrame;
    private final FrameID instFrame;
    private final UnwritableVectorIJK boresight; // in instFrame
    private final UnwritableVectorIJK upDir; // in instFrame
    private final List<UnwritableVectorIJK> frustum; // in instFrame
    private final double time; // TDB
    // Cached map of other body states (Sun/Earth/etc.) in the target frame.
    private final Map<EphemerisID, UnwritableStateVector> bodyStates;
    private UnwritableVectorIJK scPos;
    private UnwritableVectorIJK scVel;
    // Time when light left the target, used for computations with an
    // intermediate inertial frame.
    private double timeAtTarget;
    private RotationMatrixIJK scToTargetRotation;
    private RotationMatrixIJK instToTargetRotation;

    public SpiceInstrumentPointing( //
            AberratedEphemerisProvider ephProvider, //
            EphemerisID targetId, //
            FrameID targetFrame, //
            EphemerisID scId, //
            FrameID scFrame, //
            FrameID instFrame, //
            UnwritableVectorIJK boresight, //
            UnwritableVectorIJK upDir, //
            List<UnwritableVectorIJK> frustum, //
            double time //
    )
    {
        this.ephProvider = ephProvider;
        this.targetId = targetId;
        this.targetFrame = targetFrame;
        this.scId = scId;
        this.scFrame = scFrame;
        this.instFrame = instFrame;
        this.boresight = normalize(boresight);
        this.upDir = normalize(upDir);
        this.frustum = frustum;
        this.time = time;
        this.bodyStates = new HashMap<>(); // Not populated yet.
        this.scPos = null; // Not computed yet.
        this.timeAtTarget = -1.; // Not computed yet.
        this.scToTargetRotation = null; // Not computed yet.
        this.instToTargetRotation = null; // Not computed yet.
    }

    @Override
    public UnwritableVectorIJK getScPosition()
    {
        computeScPointing();

        return scPos;
    }

    @Override
    public UnwritableVectorIJK getScVelocity()
    {
        computeScPointing();

        return scVel;
    }

    @Override
    public UnwritableMatrixIJK getScRotation()
    {
        if (scToTargetRotation == null)
        {
            scToTargetRotation = computeRotationToTargetFrame(scFrame);
        }

        return scToTargetRotation;
    }

    @Override
    public UnwritableVectorIJK getPosition(EphemerisID bodyId)
    {
        UnwritableStateVector bodyState = bodyStates.get(bodyId);
        if (bodyState == null)
        {
            computeScPointing(); // for timeAtTarget

            AberratedStateVectorFunction bodyFromTarget = ephProvider.createAberratedStateVectorFunction(bodyId, targetId, targetFrame, Coverage.ALL_TIME, AberrationCorrection.LT_S);
            bodyState = UnwritableStateVector.copyOf(bodyFromTarget.getState(timeAtTarget));

            bodyStates.put(bodyId, bodyState);
        }

        return bodyState.getPosition();
    }

    @Override
    public UnwritableVectorIJK getBoresight()
    {
        computeRotationToTargetFromInst();

        return normalize(instToTargetRotation.mxv(boresight));
    }

    @Override
    public UnwritableVectorIJK getUpDirection()
    {
        computeRotationToTargetFromInst();

        return normalize(instToTargetRotation.mxv(upDir));
    }

    @Override
    public List<UnwritableVectorIJK> getFrustum()
    {
        computeRotationToTargetFromInst();

        ImmutableList.Builder<UnwritableVectorIJK> builder = ImmutableList.builder();
        for (UnwritableVectorIJK vector : frustum)
        {
            builder.add(normalize(instToTargetRotation.mxv(vector)));
        }

        return builder.build();
    }

    /**
     * Compute and cache timeAtTarget, scPos, scVel
     */
    private void computeScPointing()
    {
        if (scPos == null)
        {
            AberratedStateVectorFunction targetFromSc = ephProvider.createAberratedStateVectorFunction(targetId, scId, targetFrame, Coverage.ALL_TIME, AberrationCorrection.LT_S);

            // Need spacecraft-from-target as well as the target-from-spacecraft
            // frame calculations.
            StateVectorFunction scFromTarget = StateVectorFunctions.negate(targetFromSc);

            // Get state of spacecraft relative to target body.
            StateVector scState = scFromTarget.getState(time);

            timeAtTarget = time - targetFromSc.getLightTime(time);

            scVel = UnwritableVectorIJK.copyOf(scState.getVelocity());

            scPos = UnwritableVectorIJK.copyOf(scState.getPosition());
        }
    }

    private void computeRotationToTargetFromInst()
    {
        if (instToTargetRotation == null)
        {
            instToTargetRotation = computeRotationToTargetFrame(instFrame);
        }
    }

    /**
     * Compute rotation from a non-inertial frame to the target body frame,
     * using J2000 as intermediary in order to factor in light-time corrected
     * time-at-target accurately.
     * <p>
     * Note that there are two input times involved in this computation: the
     * time (at spacecraft) and the timeAtTarget (at target body)
     *
     * @param fromFrame identifier of frame to be rotated to the target body
     *            frame
     */
    private RotationMatrixIJK computeRotationToTargetFrame(FrameID fromFrame)
    {
        computeScPointing(); // for timeAtTarget.

        // Need to do two-step transformation here. Convert first to an
        // inertial frame at time = time:
        FrameTransformFunction toJ2000 = ephProvider.createFrameTransformFunction(fromFrame, CelestialFrames.J2000, Coverage.ALL_TIME);

        // Then from J2000 to target body frame at time = timeAtTarget.
        FrameTransformFunction j2000ToTarget = ephProvider.createFrameTransformFunction(CelestialFrames.J2000, targetFrame, Coverage.ALL_TIME);

        return RotationMatrixIJK.mxm(j2000ToTarget.getTransform(timeAtTarget), toJ2000.getTransform(time));
    }

}
