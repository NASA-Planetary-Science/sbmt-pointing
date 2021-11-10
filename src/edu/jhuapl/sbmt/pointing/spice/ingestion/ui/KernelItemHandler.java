package edu.jhuapl.sbmt.pointing.spice.ingestion.ui;

import java.util.Map;

import com.google.common.collect.ImmutableMap;

import edu.jhuapl.sbmt.dem.KeyValueNode;
import edu.jhuapl.sbmt.pointing.spice.ingestion.model.KernelInfo;

import glum.gui.panel.itemList.BasicItemHandler;
import glum.gui.panel.itemList.query.QueryComposer;

public class KernelItemHandler extends BasicItemHandler<KernelInfo, KernelLookup>
{
	// State vars
	private ImmutableMap<KernelInfo, KeyValueNode> workKeyValueM;

	/** Standard Constructor */
	public KernelItemHandler(QueryComposer<KernelLookup> aComposer)
	{
		super(aComposer);

		workKeyValueM = ImmutableMap.of();
	}

	/**
	 * Returns the currently installed key-value map
	 */
	public ImmutableMap<KernelInfo, KeyValueNode> getKeyValuePairMap()
	{
		return workKeyValueM;
	}

	/**
	 * Installs in the new key-value map.
	 */
	public void setKeyValuePairMap(Map<KernelInfo, KeyValueNode> aKeyValueM)
	{
		workKeyValueM = ImmutableMap.copyOf(aKeyValueM);
	}

	@Override
	public Object getColumnValue(KernelInfo aItem, KernelLookup aEnum)
	{
//		String tmpKey = aItem;
		switch (aEnum)
		{
			case Kernel:
				return aItem.getKernelName();
			case Directory:
				return workKeyValueM.get(aItem).getValue();
//			case Comment:
//				return workKeyValueM.get(aItem).getComment();
			default:
				break;
		}

		throw new UnsupportedOperationException("Column is not supported. Enum: " + aEnum);
	}

	@Override
	public void setColumnValue(KernelInfo aItem, KernelLookup aEnum, Object aValue)
	{
		throw new UnsupportedOperationException("Column is not supported. Enum: " + aEnum);
	}

}
