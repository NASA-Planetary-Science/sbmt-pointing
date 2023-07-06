package edu.jhuapl.sbmt.pointing.spice.ingestion.ui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;

import edu.jhuapl.saavtk.gui.util.IconUtil;
import edu.jhuapl.saavtk.gui.util.ToolTipUtil;
import edu.jhuapl.sbmt.core.util.KeyValueNode;
import edu.jhuapl.sbmt.pointing.spice.ingestion.model.KernelInfo;

import glum.gui.GuiUtil;
import glum.gui.panel.itemList.ItemListPanel;
import glum.gui.panel.itemList.query.QueryComposer;
import glum.item.ItemEventListener;
import glum.item.ItemEventType;
import glum.item.ItemManager;
import glum.item.ItemManagerUtil;
import net.miginfocom.swing.MigLayout;

public class KernelManagementPanel extends JPanel implements ActionListener, ItemEventListener
{
	private ItemManager<KernelInfo> kernelItemManager;
	private final KernelItemHandler kernelItemHandler;

	private final ItemListPanel<KernelInfo> itemILP;
	private final JLabel titleL;
	private final JButton selectAllB, selectInvertB, selectNoneB;
	private JButton deleteKernelButton, itemEditB;

	public KernelManagementPanel(ItemManager<KernelInfo> kernelItemManager)
	{

		setLayout(new MigLayout("", "0[][][][]0", "0[][]0"));

		deleteKernelButton = GuiUtil.formButton(this, IconUtil.getItemDel());
		deleteKernelButton.setToolTipText(ToolTipUtil.getItemDel());
		deleteKernelButton.setEnabled(false);

		itemEditB = GuiUtil.formButton(this, IconUtil.getItemEdit());
		itemEditB.setToolTipText(ToolTipUtil.getItemEdit());
		itemEditB.setEnabled(false);

		// Table header
		selectInvertB = GuiUtil.formButton(this, IconUtil.getSelectInvert());
		selectInvertB.setToolTipText(ToolTipUtil.getSelectInvert());

		selectNoneB = GuiUtil.formButton(this, IconUtil.getSelectNone());
		selectNoneB.setToolTipText(ToolTipUtil.getSelectNone());

		selectAllB = GuiUtil.formButton(this, IconUtil.getSelectAll());
		selectAllB.setToolTipText(ToolTipUtil.getSelectAll());

		titleL = new JLabel("Items: ---");
		add(titleL, "growx,pushx,span,split");

		add(deleteKernelButton, "w 24!,h 24!");
		add(itemEditB, "gapright 24,w 24!,h 24!");
		Component horizontalStrut = Box.createHorizontalStrut(24);
		horizontalStrut.setMaximumSize(new Dimension(24, 5));
		add(selectInvertB, "w 24!,h 24!");
		add(selectNoneB, "w 24!,h 24!");
		add(selectAllB, "w 24!,h 24!,wrap 2");

		// Table Content
		QueryComposer<KernelLookup> tmpComposer = new QueryComposer<>();
		tmpComposer.addAttribute(KernelLookup.Kernel, String.class, "Metakernel", null);
		tmpComposer.addAttribute(KernelLookup.Directory, String.class, "Directory", null);
//		tmpComposer.addAttribute(KernelLookup.Comment, String.class, "Comment", null);
		tmpComposer.getItem(KernelLookup.Kernel).defaultSize *= 3;
		tmpComposer.getItem(KernelLookup.Directory).defaultSize *= 9;
//		tmpComposer.getItem(KernelLookup.Comment).defaultSize *= 2;

//		kernelItemManager = new BaseItemManager<>();
		kernelItemHandler = new KernelItemHandler(tmpComposer);
		setKernelSet(kernelItemManager);
		itemILP = new ItemListPanel<KernelInfo>(kernelItemHandler, kernelItemManager, true);
		itemILP.setSortingEnabled(true);

		JTable itemTable = itemILP.getTable();
		itemTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		add(new JScrollPane(itemTable), "growx,growy,pushy,span,h 70::");

		updateGui();


		// Register for events of interest
		kernelItemManager.addListener(this);
	}

	public void setKernelSet(ItemManager<KernelInfo> itemManager)
	{
		this.kernelItemManager = itemManager;
		Map<KernelInfo, KeyValueNode> kvPairs = new HashMap<KernelInfo, KeyValueNode>();
		for (KernelInfo info : itemManager.getAllItems())
		{
			kvPairs.put(info, new KeyValueNode(info.getKernelName(), info.getKernelDirectory(), ""));
		}
		this.kernelItemHandler.setKeyValuePairMap(kvPairs);
	}

	@Override
	public void actionPerformed(ActionEvent aEvent)
	{
		Object source = aEvent.getSource();
		if (source == selectAllB)
			ItemManagerUtil.selectAll(kernelItemManager);
		else if (source == selectNoneB)
			ItemManagerUtil.selectNone(kernelItemManager);
		else if (source == selectInvertB)
			ItemManagerUtil.selectInvert(kernelItemManager);

		updateGui();
	}

	@Override
	public void handleItemEvent(Object aSource, ItemEventType aEventType)
	{
		updateGui();
	}

	public JButton getDeleteKernelButton()
	{
		return deleteKernelButton;
	}

	public JButton getItemEditB()
	{
		return itemEditB;
	}

	/**
	 * Helper method that updates the various UI elements to keep them
	 * synchronized.
	 */
	private void updateGui()
	{
		// Gather various stats
		int cntFullItems = kernelItemManager.getAllItems().size();

		Set<KernelInfo> pickS = kernelItemManager.getSelectedItems();
		int cntPickItems = pickS.size();

		// Update action buttons
		boolean isEnabled;

		isEnabled = cntFullItems > 0;
		selectInvertB.setEnabled(isEnabled);

		isEnabled = cntPickItems > 0;
		selectNoneB.setEnabled(isEnabled);
		itemEditB.setEnabled(isEnabled);
		deleteKernelButton.setEnabled(isEnabled);

		isEnabled = cntFullItems > 0 && cntPickItems < cntFullItems;
		selectAllB.setEnabled(isEnabled);

		// Table title
		DecimalFormat cntFormat = new DecimalFormat("#,###");
		String infoStr = "Items: " + cntFormat.format(cntFullItems);
		if (cntPickItems > 0)
			infoStr += "  (Selected: " + cntFormat.format(cntPickItems) + ")";
		titleL.setText(infoStr);
	}
}
