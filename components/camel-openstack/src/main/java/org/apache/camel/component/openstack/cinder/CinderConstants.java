/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.openstack.cinder;

import org.apache.camel.component.openstack.common.OpenstackConstants;

public final class CinderConstants extends OpenstackConstants {

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

    private CinderConstants() { }

}
