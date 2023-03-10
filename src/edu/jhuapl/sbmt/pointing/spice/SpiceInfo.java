package edu.jhuapl.sbmt.pointing.spice;

import java.io.File;
import java.util.Arrays;

import crucible.crust.metadata.api.Key;
import crucible.crust.metadata.api.Metadata;
import crucible.crust.metadata.api.Version;
import crucible.crust.metadata.impl.InstanceGetter;
import crucible.crust.metadata.impl.SettableMetadata;
import crucible.crust.metadata.impl.gson.Serializers;

public class SpiceInfo
{
	String scId;
	String bodyFrameName;
	String scFrameName;
	String bodyName;
	String[] bodyNamesToBind;
	String[] bodyFramesToBind;
	String[] instrumentNamesToBind;
	String[] instrumentFrameNamesToBind;

    private static final Key<SpiceInfo> SPICE_INFO_KEY = Key.of("SpiceInfo");
	private static final Key<String> SCID_KEY = Key.of("scId");
	private static final Key<String> BODYFRAMENAME_KEY = Key.of("bodyFrameName");
	private static final Key<String> SCFRAMENAME_KEY = Key.of("scFrameName");
	private static final Key<String> BODYNAME_KEY = Key.of("bodyName");
	private static final Key<String[]> BODYNAMESTOBIND_KEY = Key.of("bodyNamesToBind");
	private static final Key<String[]> BODYFRAMESTOBIND_KEY = Key.of("bodyFramesToBind");
	private static final Key<String[]> INSTRUMENTNAMESTOBIND_KEY = Key.of("instrumentNamesToBind");
	private static final Key<String[]> INSTRUMENTFRAMENAMESTOBIND_KEY = Key.of("instrumentFrameNamesToBind");

    public static void initializeSerializationProxy()
	{
    	InstanceGetter.defaultInstanceGetter().register(SPICE_INFO_KEY, (source) -> {

    		String scId = source.get(SCID_KEY);
    		String bodyFrameName = source.get(BODYFRAMENAME_KEY);
    		String scFrameName = source.get(SCFRAMENAME_KEY);
    		String bodyName = source.get(BODYNAME_KEY);
    		String[] bodyNamesToBind = new String[] {};
    		String[] bodyFramesToBind = new String[] {};
    		String[] instrumentNamesToBind = new String[] {};
    		String[] instrumentFrameNamesToBind = new String[] {};

    		if (source.hasKey(BODYNAMESTOBIND_KEY))
    			bodyNamesToBind = source.get(BODYNAMESTOBIND_KEY);
    		if (source.hasKey(BODYFRAMESTOBIND_KEY))
    			bodyFramesToBind = source.get(BODYFRAMESTOBIND_KEY);
   			instrumentNamesToBind = source.get(INSTRUMENTNAMESTOBIND_KEY);
    		if (source.hasKey(INSTRUMENTFRAMENAMESTOBIND_KEY))
    			instrumentFrameNamesToBind = source.get(INSTRUMENTFRAMENAMESTOBIND_KEY);

    		SpiceInfo spiceInfo = new SpiceInfo(scId, bodyFrameName, scFrameName, bodyName, bodyNamesToBind, bodyFramesToBind, instrumentNamesToBind, instrumentFrameNamesToBind);
    		return spiceInfo;

    	}, SpiceInfo.class, spiceInfo -> {

    		SettableMetadata result = SettableMetadata.of(Version.of(1, 0));
    		result.put(SCID_KEY, spiceInfo.getScId());
    		result.put(BODYFRAMENAME_KEY, spiceInfo.getBodyFrameName());
    		result.put(SCFRAMENAME_KEY, spiceInfo.getScFrameName());
    		result.put(BODYNAME_KEY, spiceInfo.getBodyName());
    		result.put(BODYNAMESTOBIND_KEY, spiceInfo.getBodyNamesToBind());
    		result.put(BODYFRAMESTOBIND_KEY, spiceInfo.getBodyFramesToBind());
    		result.put(INSTRUMENTNAMESTOBIND_KEY, spiceInfo.getInstrumentNamesToBind());
    		result.put(INSTRUMENTFRAMENAMESTOBIND_KEY, spiceInfo.getInstrumentFrameNamesToBind());

    		return result;
    	});
	}

	public SpiceInfo()
	{
		// TODO Auto-generated constructor stub
	}

	/**
	 * @param scId
	 * @param bodyFrameName
	 * @param scFrameName
	 * @param bodyName
	 * @param bodyNamesToBind
	 * @param instrumentNamesToBind
	 */
	public SpiceInfo(String scId, String bodyFrameName, String scFrameName, String bodyName, String[] bodyNamesToBind, String[] bodyFramesToBind,
			String[] instrumentNamesToBind, String[] instrumentFrameNamesToBind)
	{
		this.scId = scId;
		this.bodyFrameName = bodyFrameName;
		this.scFrameName = scFrameName;
		this.bodyName = bodyName;
		this.bodyNamesToBind = bodyNamesToBind;
		this.bodyFramesToBind = bodyFramesToBind;
		this.instrumentNamesToBind = instrumentNamesToBind;
		this.instrumentFrameNamesToBind = instrumentFrameNamesToBind;
	}

	/**
	 * @return the scId
	 */
	public String getScId()
	{
		return scId;
	}

	/**
	 * @return the bodyFrameName
	 */
	public String getBodyFrameName()
	{
		return bodyFrameName;
	}

	/**
	 * @return the scFrameName
	 */
	public String getScFrameName()
	{
		return scFrameName;
	}

	/**
	 * @return the bodyName
	 */
	public String getBodyName()
	{
		return bodyName;
	}

	/**
	 * @return the bodyNamesToBind
	 */
	public String[] getBodyNamesToBind()
	{
		return bodyNamesToBind;
	}

	/**
	 * @return the bodyNamesToBind
	 */
	public String[] getBodyFramesToBind()
	{
		return bodyFramesToBind;
	}

	/**
	 * @return the instrumentNamesToBind
	 */
	public String[] getInstrumentNamesToBind()
	{
		return instrumentNamesToBind;
	}

	/**
	 * @return the instrumentNamesToBind
	 */
	public String[] getInstrumentFrameNamesToBind()
	{
		return instrumentFrameNamesToBind;
	}

	@Override
	public String toString()
	{
		return "SpiceInfo [scId=" + scId + ", bodyFrameName=" + bodyFrameName + ", scFrameName=" + scFrameName
				+ ", bodyName=" + bodyName + ", bodyNamesToBind=" + Arrays.toString(bodyNamesToBind)
				+ ", instrumentNamesToBind=" + Arrays.toString(instrumentNamesToBind) + "]";
	}

	public static void main(String[] args) throws Exception
	{
		SpiceInfo spice = new SpiceInfo("MMX", "IAU_PHOBOS", "MMX_SPACECRAFT", "PHOBOS",
    			new String[] {"EARTH" , "SUN", "MARS"}, new String[] {"IAU_MARS"}, new String[] {"MMX_MEGANE"}, new String[] {});
		SpiceInfo.initializeSerializationProxy();
		Metadata provide = InstanceGetter.defaultInstanceGetter().providesMetadataFromGenericObject(SpiceInfo.class).provide(spice);
		Serializers.serialize("spiceInfoTest", provide, new File("/Users/steelrj1/Desktop/spice.txt"));
	}

}
