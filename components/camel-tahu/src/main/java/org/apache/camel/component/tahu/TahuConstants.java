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

    public static final String DEVICE_SUBSCHEME = EDGE_NODE_SUBSCHEME; //"device";
    public static final String DEVICE_SCHEME = BASE_SCHEME + "-" + DEVICE_SUBSCHEME;

    public static final String HOST_APP_SUBSCHEME = "host";
    public static final String HOST_APP_SCHEME = BASE_SCHEME + "-" + HOST_APP_SUBSCHEME;

    public static final String MAJOR_SEPARATOR = "/";
    public static final String MINOR_SEPARATOR = "+";
    public static final String CONFIG_LIST_SEPARATOR = ",";

    public static final String EDGE_NODE_ENDPOINT_URI_SYNTAX = EDGE_NODE_SCHEME + ":groupId" + MAJOR_SEPARATOR + "edgeNode";

    public static final String DEVICE_ENDPOINT_URI_SYNTAX
            = DEVICE_SCHEME + ":groupId" + MAJOR_SEPARATOR + "edgeNode" + MAJOR_SEPARATOR + "deviceId";

    public static final String HOST_APP_ENDPOINT_URI_SYNTAX = HOST_APP_SCHEME + ":hostId";

    public static final String METRIC_HEADER_PREFIX = "CamelTahuMetric.";

    @Metadata(description = "The Sparkplug message type. The consumer sets it on the exchange of each message, but not on"
                            + " the exchange of a single metric. The producer sets it to NDATA for an Edge Node and to DDATA"
                            + " for a Device.",
              javaType = "String",
              enums = "NBIRTH,NDATA,NDEATH,DBIRTH,DDATA,DDEATH")
    public static final String MESSAGE_TYPE = "CamelTahuMessageType";

    @Metadata(description = "The Edge Node or Device of the message or metric, as groupId/edgeNode or"
                            + " groupId/edgeNode/deviceId. The consumer sets it as a String, and the producer sets the Tahu"
                            + " EdgeNodeDescriptor of the endpoint, a DeviceDescriptor for a Device.",
              javaType = "String")
    public static final String EDGE_NODE_DESCRIPTOR = "CamelTahuEdgeNodeDescriptor";

    @Metadata(description = "The timestamp of the Sparkplug payload. The consumer sets it in milliseconds since the epoch,"
                            + " and the producer sets the java.util.Date of the payload it publishes.",
              javaType = "Long")
    public static final String MESSAGE_TIMESTAMP = "CamelTahuMessageTimestamp";

    @Metadata(description = "The UUID of the Sparkplug payload. The consumer sets it when the payload has a valid UUID. The"
                            + " producer uses it as the UUID of a payload that it makes from the message, and sets it to the"
                            + " UUID of the payload it publishes, as a String.",
              javaType = "java.util.UUID")
    public static final String MESSAGE_UUID = "CamelTahuMessageUUID";

    @Metadata(description = "The sequence number of the Sparkplug payload. The consumer sets it when the payload has one,"
                            + " and the producer only when the payload to publish already has one.",
              javaType = "Long")
    public static final String MESSAGE_SEQUENCE_NUMBER = "CamelTahuMessageSequenceNumber";

    private TahuConstants() {
    }
}
