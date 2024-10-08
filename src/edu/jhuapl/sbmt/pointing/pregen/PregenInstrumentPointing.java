package edu.jhuapl.sbmt.pointing.pregen;

import java.util.List;

import edu.jhuapl.sbmt.pointing.InstrumentPointing;
import edu.jhuapl.sbmt.pointing.State;
import edu.jhuapl.sbmt.pointing.scState.CsvState;
import picante.math.vectorspace.UnwritableMatrixIJK;
import picante.math.vectorspace.UnwritableVectorIJK;
import picante.mechanics.EphemerisID;

public class PregenInstrumentPointing implements InstrumentPointing
{
	CsvState state;

	public PregenInstrumentPointing(State state)
	{
		this.state = (CsvState)state;
	}

	@Override
	public UnwritableVectorIJK getScPosition()
	{
		return new UnwritableVectorIJK(state.getSpacecraftPosition());
	}

	@Override
	public UnwritableVectorIJK getScVelocity()
	{
		return new UnwritableVectorIJK(state.getSpacecraftVelocity());
	}

	@Override
	public UnwritableMatrixIJK getScRotation()
	{
		double[] xAxis = state.getSpacecraftXAxis();
		double[] yAxis = state.getSpacecraftYAxis();
		double[] zAxis = state.getSpacecraftZAxis();
		return new UnwritableMatrixIJK(new UnwritableVectorIJK(xAxis),
										new UnwritableVectorIJK(yAxis),
										new UnwritableVectorIJK(zAxis));

	}

	@Override
	public UnwritableVectorIJK getPosition(EphemerisID bodyId)
	{
		switch (bodyId.getName())
		{
		case "SUN":
			return new UnwritableVectorIJK(state.getSunPosition());
		case "EARTH":
			return new UnwritableVectorIJK(state.getEarthPosition());
		default:
			return null;
		}
	}

	@Override
	public UnwritableVectorIJK getBoresight()
	{
		return new UnwritableVectorIJK(new double[] {0,0,1});
	}

	@Override
	public UnwritableVectorIJK getUpDirection()
	{
		return new UnwritableVectorIJK(new double[] {0,1,0});
	}

	@Override
	public List<UnwritableVectorIJK> getFrustum()
	{
		// TODO Auto-generated method stub
		return null;
	}

}
