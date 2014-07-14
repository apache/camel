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
package org.apache.camel.component.jclouds;

public final class JcloudsConstants {
    public static final String DELIMETER = ":";
    public static final String BLOBSTORE = "blobstore";
    public static final String COMPUTE = "compute";
    public static final String BLOB_NAME = "CamelJcloudsBlobName";
    public static final String CONTAINER_NAME = "CamelJcloudsContainerName";

    public static final String NODE_STATE = "CamelJcloudsNodeState";

    public static final String OPERATION = "CamelJcloudsOperation";
    public static final String PUT = "Put";
    public static final String GET = "Get";
    public static final String LIST_IMAGES = "ListImages";
    public static final String LIST_HARDWARE = "ListHardware";
    public static final String LIST_NODES = "ListNodes";
    public static final String CREATE_NODE = "CreateNode";
    public static final String DESTROY_NODE = "DestroyNode";
    public static final String RUN_SCRIPT = "RunScript";
    
    public static final String CONTENT_LANGUAGE = "CamelJcloudsContentLanguage";
    public static final String CONTENT_DISPOSITION = "CamelJcloudsContentDisposition";
    public static final String PAYLOAD_EXPIRES = "CamelJcloudsPayloadExpires";

    public static final String IMAGE_ID = "CamelJcloudsImageId";
    public static final String LOCATION_ID = "CamelJcloudsLocationId";
    public static final String HARDWARE_ID = "CamelJcloudsHardwareId";
    public static final String GROUP = "CamelJcloudsGroup";
    public static final String NODE_ID = "CamelJcloudsNodeId";
    public static final String USER = "CamelJcloudsUser";

    public static final String RUN_SCRIPT_ERROR = "CamelJcloudsRunScriptError";
    public static final String RUN_SCRIPT_EXIT_CODE = "CamelJcloudsRunScriptErrorCode";


    
    private JcloudsConstants() {
        // utility class
    }
}
