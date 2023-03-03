package edu.jhuapl.sbmt.pointing.spice.ingestion.controller;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;

import org.apache.commons.io.FilenameUtils;

import edu.jhuapl.saavtk.gui.dialog.CustomFileChooser;
import edu.jhuapl.saavtk.model.ModelManager;
import edu.jhuapl.sbmt.pointing.spice.ingestion.model.SpiceKernelIngestor;
import edu.jhuapl.sbmt.pointing.spice.ingestion.model.SpiceKernelLoadStatusListener;
import edu.jhuapl.sbmt.pointing.spice.ingestion.model.StateHistoryIOException;

public class KernelSelectionFrame extends JFrame
{

	private SpiceKernelIngestor kernelIngestor;

    private KernelIngestor ingestor;

    private KernelManagementController kernelManagementController;

    private JComboBox<String> kernelComboBox;

	private String metakernelToLoad;

	private JProgressBar progressBar = new JProgressBar();

	private JButton cancelButton = new JButton("Cancel");

	private Function<String, Void> completionBlock;

	public KernelSelectionFrame(ModelManager modelManager, Function<String, Void> completionBlock)
	{
		setTitle("Select Kernels");
		this.completionBlock = completionBlock;
		kernelIngestor = new SpiceKernelIngestor(modelManager.getPolyhedralModel().getCustomDataFolder());
		kernelManagementController = new KernelManagementController(kernelIngestor.getLoadedKernelsDirectory(), new KernelSetChangedListener()
		{

			@Override
			public void kernelDeleted(String kernelName)
			{
				DefaultComboBoxModel<String> model = new DefaultComboBoxModel<String>(getAvailableKernels());
				kernelComboBox.setModel(model);
			}
		});
		initGUI();
		setSize(500, 150);
		setVisible(true);
	}


	public void initGUI()
	{
		kernelComboBox = new JComboBox<String>(getAvailableKernels());
		cancelButton.setEnabled(false);
		kernelComboBox.addActionListener(e -> {


			String selectedItem = (String)kernelComboBox.getSelectedItem();
			File loadedKernelsDirectory = kernelIngestor.getLoadedKernelsDirectory();
			if (selectedItem.equals("Load new kernel..."))
			{
				File file = CustomFileChooser.showOpenDialog(this, "Select Metakernel");
				if (file == null) return;
				metakernelToLoad = file.getAbsolutePath();
				ingestor  = new KernelIngestor(progressBar, kernelComboBox);
				ingestor.execute();
			}
			else
			{
				File selectedKernelDirectory = new File(loadedKernelsDirectory, selectedItem);
				metakernelToLoad = new File(selectedKernelDirectory, selectedItem + ".mk").getAbsolutePath();
			}

		});

		JButton manageKernels = new JButton("Manage Kernels");
    	manageKernels.addActionListener(l -> {
    		JFrame frame2 = new JFrame("Manage Kernels");
    		frame2.add(kernelManagementController.getView());
    		frame2.setMinimumSize(new Dimension(800, frame2.getHeight()));
        	frame2.pack();
        	frame2.setVisible(true);
    	});

		JTextArea label = new JTextArea("This configuration uses SPICE kernels to properly position secondary bodies.  Please select a metakernel to load, or load a new one from your system.");
		label.setWrapStyleWord(true);
		label.setLineWrap(true);
		label.setRows(3);

		setLayout(new BoxLayout(getContentPane(), BoxLayout.Y_AXIS));
		getContentPane().add(label);
		JPanel subPanel = new JPanel();
		subPanel.setLayout(new BoxLayout(subPanel, BoxLayout.X_AXIS));
		subPanel.add(kernelComboBox);
		subPanel.add(progressBar);
		getContentPane().add(subPanel);

		JButton acceptButton = new JButton("Save MetaKernel");
		acceptButton.addActionListener(new ActionListener()
		{

			@Override
			public void actionPerformed(ActionEvent arg0)
			{
				completionBlock.apply(metakernelToLoad);
				setVisible(false);
			}
		});

		JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));
		buttonPanel.add(Box.createGlue());
		buttonPanel.add(manageKernels);
		buttonPanel.add(Box.createGlue());
		buttonPanel.add(acceptButton);
		buttonPanel.add(Box.createGlue());

		getContentPane().add(buttonPanel);

	}

	private String[] getAvailableKernels()
	{
		List<String> loadedKernels = new ArrayList<String>();
		if (kernelIngestor.getLoadedKernelsDirectory().listFiles() != null)
			loadedKernels = Stream.of(kernelIngestor.getLoadedKernelsDirectory().listFiles()).filter(file -> file.isDirectory()).map(File::getName).collect(Collectors.toList());
		loadedKernels.add(0, "Load new kernel...");
		String[] loadedKernelNamesArray = new String[loadedKernels.size()];
		loadedKernels.toArray(loadedKernelNamesArray);
		return loadedKernelNamesArray;
	}

	class KernelIngestor extends SwingWorker<Void, Void>
	{
		JProgressBar progressBar;
		JComboBox<String> kernelComboBox;

		public KernelIngestor(JProgressBar progressBar, JComboBox<String> kernelComboBox)
		{
			this.progressBar = progressBar;
			this.kernelComboBox = kernelComboBox;
		}

		@Override
		protected Void doInBackground() throws Exception
		{
			try
			{
				cancelButton.setEnabled(true);
				metakernelToLoad = kernelIngestor.ingestMetaKernelToCache(metakernelToLoad, new SpiceKernelLoadStatusListener() {

					@Override
					public void percentageLoaded(double percentage) {
						progressBar.setValue((int)percentage);
					}
				});
			}
			catch (StateHistoryIOException | IOException e1)
			{
				if (!isCancelled())
				{
					JOptionPane.showMessageDialog(KernelSelectionFrame.this, "Problem ingesting SPICE kernel.  Please check the file for correctness.",
												"Ingestion Error", JOptionPane.ERROR_MESSAGE);
					e1.printStackTrace();
				}
			}
			finally {
				progressBar.setValue(0);
				cancelButton.setEnabled(false);
			}
			return null;
		}

		@Override
		protected void done()
		{
			if (!isCancelled())
			{
				String newComboItemName = FilenameUtils.getBaseName(new File(metakernelToLoad).getName());

				kernelComboBox.addItem(newComboItemName);
				kernelComboBox.setSelectedItem(newComboItemName);
			}
			super.done();
		}

	}
}
