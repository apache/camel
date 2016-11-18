package org.apache.camel.component.openstack.cinder;

import org.apache.camel.component.openstack.common.OpenstackConstants;

public final class CinderConstants extends OpenstackConstants{

	public static final String VOLUMES = "volumes";
	public static final String SNAPSHOTS = "snapshots";

	//volumes
	public static final String SIZE = "size";
	public static final String VOLUME_TYPE = "volumeType";
	public static final String IMAGE_REF = "imageRef";
	public static final String SNAPSHOT_ID = "snapshotId";
	public static final String IS_BOOTABLE = "isBootable";

	//volumeSnapshots
	public static final String VOLUME_ID = "volumeId";
	public static final String FORCE = "force";

	public static final String GET_ALL_TYPES = "getAllTypes";

}
