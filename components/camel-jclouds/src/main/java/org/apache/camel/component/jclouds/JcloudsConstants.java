/*
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
package org.apache.camel.component.jclouds;

import org.apache.camel.spi.Metadata;

public final class JcloudsConstants {
    public static final String DELIMETER = ":";
    public static final String BLOBSTORE = "blobstore";
    public static final String COMPUTE = "compute";
    @Metadata(label = "producer blobstore", description = "The name of the blob.", javaType = "String")
    public static final String BLOB_NAME = "CamelJcloudsBlobName";
    @Metadata(label = "producer blobstore", description = "The name of the blob container.", javaType = "String")
    public static final String CONTAINER_NAME = "CamelJcloudsContainerName";
    @Metadata(label = "producer blobstore", description = "The blob name list.", javaType = "List")
    public static final String BLOB_NAME_LIST = "CamelJcloudsBlobNameList";

    @Metadata(label = "producer compute", description = "The node state", javaType = "Object")
    public static final String NODE_STATE = "CamelJcloudsNodeState";

    @Metadata(label = "producer",
              description = "The operation to be performed on the blob.\n\nThe valid options are:\n\n* PUT\n* GET",
              javaType = "String")
    public static final String OPERATION = "CamelJcloudsOperation";
    public static final String PUT = "CamelJcloudsPut";
    public static final String GET = "CamelJcloudsGet";
    public static final String COUNT_BLOBS = "CamelJcloudsCountBlobs";
    public static final String REMOVE_BLOB = "CamelJcloudsRemoveBlob";
    public static final String REMOVE_BLOBS = "CamelJcloudsRemoveBlobs";
    public static final String CLEAR_CONTAINER = "CamelJcloudsClearContainer";
    public static final String DELETE_CONTAINER = "CamelJcloudsDeleteContainer";
    public static final String CONTAINER_EXISTS = "CamelJcloudsExistsContainer";
    public static final String LIST_IMAGES = "CamelJcloudsListImages";
    public static final String LIST_HARDWARE = "CamelJcloudsListHardware";
    public static final String LIST_NODES = "CamelJcloudsListNodes";
    public static final String CREATE_NODE = "CamelJcloudsCreateNode";
    public static final String DESTROY_NODE = "CamelJcloudsDestroyNode";
    public static final String REBOOT_NODE = "CamelJcloudsRebootNode";
    public static final String SUSPEND_NODE = "CamelJcloudsSuspendNode";
    public static final String RESUME_NODE = "CamelJcloudsResumeNode";
    public static final String RUN_SCRIPT = "CamelJcloudsRunScript";

    public static final String CONTENT_LANGUAGE = "CamelJcloudsContentLanguage";
    public static final String CONTENT_DISPOSITION = "CamelJcloudsContentDisposition";
    public static final String PAYLOAD_EXPIRES = "CamelJcloudsPayloadExpires";

    @Metadata(label = "producer compute",
              description = "The imageId that will be used for creating a node. Values depend on the actual cloud provider.",
              javaType = "String")
    public static final String IMAGE_ID = "CamelJcloudsImageId";
    @Metadata(label = "producer",
              description = "The location that will be used for creating a node. Values depend on the actual cloud provider.",
              javaType = "String")
    public static final String LOCATION_ID = "CamelJcloudsLocationId";
    @Metadata(label = "producer compute",
              description = "The hardware that will be used for creating a node. Values depend on the actual cloud provider.",
              javaType = "String")
    public static final String HARDWARE_ID = "CamelJcloudsHardwareId";
    @Metadata(label = "producer compute",
              description = "The group that will be assigned to the newly created node. Values depend on the actual cloud provider.",
              javaType = "String")
    public static final String GROUP = "CamelJcloudsGroup";
    @Metadata(label = "producer compute", description = "The id of the node that will run the script or destroyed.",
              javaType = "String")
    public static final String NODE_ID = "CamelJcloudsNodeId";
    @Metadata(label = "producer compute", description = "The user on the target node that will run the script.",
              javaType = "String")
    public static final String USER = "CamelJcloudsUser";

    public static final String RUN_SCRIPT_ERROR = "CamelJcloudsRunScriptError";
    public static final String RUN_SCRIPT_EXIT_CODE = "CamelJcloudsRunScriptErrorCode";

    private JcloudsConstants() {
        // utility class
    }
}
