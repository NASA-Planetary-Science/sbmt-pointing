package edu.jhuapl.sbmt.pointing.spice.ingestion.controller;

import java.io.File;
import java.io.IOException;

import edu.jhuapl.sbmt.pointing.spice.ingestion.model.KernelManagementModel;
import edu.jhuapl.sbmt.pointing.spice.ingestion.ui.KernelManagementPanel;



public class KernelManagementController
{
	private KernelManagementPanel view;
	private KernelManagementModel model;

	public KernelManagementController(File loadedKernelsDirectory, KernelSetChangedListener listener)
	{
		try
		{
			model = new KernelManagementModel(loadedKernelsDirectory.getAbsolutePath());
			view = new KernelManagementPanel(model);
		}
		catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		view.getDeleteKernelButton().addActionListener(e -> {
			model.getSelectedItems().forEach( kernelSet ->
				{
					model.deleteKernelSet(kernelSet.getKernelDirectory(), true);
					listener.kernelDeleted(kernelSet.getKernelDirectory());
				}
			);
		});

		view.getItemEditB().addActionListener(e -> {

		});
	}


	/**
	 * Returns the view associated with this controller
	 * @return
	 */
	public KernelManagementPanel getView()
	{
		try
		{
			model.refreshModel();
			view.setKernelSet(model);
		}
		catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return view;
	}

}
