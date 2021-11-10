package edu.jhuapl.sbmt.pointing.spice;

import edu.jhuapl.saavtk.model.IPositionOrientationManager;

public interface PositionOrientationManagerListener
{
	public void managerUpdated(IPositionOrientationManager manager);
}
