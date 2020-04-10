package edu.jhuapl.sbmt.pointing;

import crucible.core.math.vectorspace.VectorIJK;

public interface Pointing
{

    VectorIJK getSpacecraftPosition();

    VectorIJK getSunPosition();

    VectorIJK getBoresightDirection();

    VectorIJK getUpDirection();

    Frustum getFrustum();

}
