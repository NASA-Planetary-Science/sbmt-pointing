package edu.jhuapl.sbmt.pointing.spice;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableList;

import edu.jhuapl.sbmt.pointing.AbstractInstrumentPointing;

import crucible.core.math.vectorspace.RotationMatrixIJK;
import crucible.core.math.vectorspace.UnwritableVectorIJK;
import crucible.core.mechanics.CelestialFrames;
import crucible.core.mechanics.Coverage;
import crucible.core.mechanics.EphemerisID;
import crucible.core.mechanics.FrameID;
import crucible.core.mechanics.FrameTransformFunction;
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
    private RotationMatrixIJK instToCenterRotation;

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
        this.scPos = null; // Not computed yet.
        this.timeAtTarget = -1.; // Not computed yet.
        this.instToCenterRotation = null; // Not computed yet.
    }

    public UnwritableVectorIJK getSpacecraftPos()
    {
        computeScPointing();

        return scPos;
    }

    public UnwritableVectorIJK getPos(EphemerisID bodyId)
    {
        UnwritableStateVector bodyState = bodyStates.get(bodyId);
        if (bodyState == null)
        {
            // Needed to guarantee timeAtTarget is computed correctly.
            computeScPointing();

            AberratedStateVectorFunction bodyFromTarget = ephProvider.createAberratedStateVectorFunction(bodyId, targetId, centerFrameId, Coverage.ALL_TIME, AberrationCorrection.LT_S);
            bodyState = UnwritableStateVector.copyOf(bodyFromTarget.getState(timeAtTarget));

            bodyStates.put(bodyId, bodyState);
        }

        return bodyState.getPosition();
    }

    public UnwritableVectorIJK getBoresight()
    {
        computeScPointing();

        return normalize(instToCenterRotation.mxv(boresight));
    }

    public UnwritableVectorIJK getUp()
    {
        computeScPointing();

        return normalize(instToCenterRotation.mxv(upDir));
    }

    public List<UnwritableVectorIJK> getFrustum()
    {
        computeScPointing();

        return rotateAll(frustum);
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

            timeAtTarget = time - bodyFromSc.getLightTime(time);

            // Need to do two-step transformation here. Convert first to an inertial frame at time = time:
            FrameTransformFunction instToJ2000 = ephProvider.createFrameTransformFunction(instFrame, CelestialFrames.J2000, Coverage.ALL_TIME);

            // Then from J2000 to center frame at time = timeAtTarget.
            FrameTransformFunction j2000ToCenter = ephProvider.createFrameTransformFunction(CelestialFrames.J2000, centerFrameId, Coverage.ALL_TIME);

            instToCenterRotation = RotationMatrixIJK.mxm(j2000ToCenter.getTransform(timeAtTarget), instToJ2000.getTransform(time));

            scPos = UnwritableVectorIJK.copyOf(bodyFromScState.getPosition());
        }
    }

    private ImmutableList<UnwritableVectorIJK> rotateAll(List<UnwritableVectorIJK> vectors)
    {
        ImmutableList.Builder<UnwritableVectorIJK> builder = ImmutableList.builder();
        for (UnwritableVectorIJK vector : vectors)
        {
            builder.add(normalize(instToCenterRotation.mxv(vector)));
        }

        return builder.build();
    }
}
