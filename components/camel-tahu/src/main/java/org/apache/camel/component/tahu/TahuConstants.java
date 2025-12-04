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

package org.apache.camel.component.tahu;

import org.apache.camel.spi.Metadata;

public final class TahuConstants {

    public static final String BASE_SCHEME = "tahu";

    public static final String EDGE_NODE_SUBSCHEME = "edge";
    public static final String EDGE_NODE_SCHEME = BASE_SCHEME + "-" + EDGE_NODE_SUBSCHEME;

    public static final String DEVICE_SUBSCHEME = EDGE_NODE_SUBSCHEME; // "device";
    public static final String DEVICE_SCHEME = BASE_SCHEME + "-" + DEVICE_SUBSCHEME;

    public static final String HOST_APP_SUBSCHEME = "host";
    public static final String HOST_APP_SCHEME = BASE_SCHEME + "-" + HOST_APP_SUBSCHEME;

    public static final String MAJOR_SEPARATOR = "/";
    public static final String MINOR_SEPARATOR = "+";
    public static final String CONFIG_LIST_SEPARATOR = ",";

    public static final String EDGE_NODE_ENDPOINT_URI_SYNTAX =
            EDGE_NODE_SCHEME + ":groupId" + MAJOR_SEPARATOR + "edgeNode";

    public static final String DEVICE_ENDPOINT_URI_SYNTAX =
            DEVICE_SCHEME + ":groupId" + MAJOR_SEPARATOR + "edgeNode" + MAJOR_SEPARATOR + "deviceId";

    public static final String HOST_APP_ENDPOINT_URI_SYNTAX = HOST_APP_SCHEME + ":hostId";

    public static final String METRIC_HEADER_PREFIX = "CamelTahuMetric.";

    @Metadata(
            description = "The Sparkplug message type of the message",
            javaType = "String",
            enums = "NBIRTH,NDATA,NDEATH,DBIRTH,DDATA,DDEATH")
    public static final String MESSAGE_TYPE = "CamelTahuMessageType";

    @Metadata(
            description = "The Sparkplug edge node descriptor string source of a message or metric",
            javaType = "String")
    public static final String EDGE_NODE_DESCRIPTOR = "CamelTahuEdgeNodeDescriptor";

    @Metadata(description = "The timestamp of a Sparkplug message", javaType = "Long")
    public static final String MESSAGE_TIMESTAMP = "CamelTahuMessageTimestamp";

    @Metadata(description = "The UUID of a Sparkplug message", javaType = "java.util.UUID")
    public static final String MESSAGE_UUID = "CamelTahuMessageUUID";

    @Metadata(description = "The sequence number of a Sparkplug message", javaType = "Long")
    public static final String MESSAGE_SEQUENCE_NUMBER = "CamelTahuMessageSequenceNumber";

    private TahuConstants() {}
}
