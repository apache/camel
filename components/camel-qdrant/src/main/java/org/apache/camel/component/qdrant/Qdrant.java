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
package org.apache.camel.component.qdrant;

import org.apache.camel.spi.Metadata;

public class Qdrant {
    public static final String SCHEME = "qdrant";

    private Qdrant() {
    }

    public static class Headers {
        @Metadata(description = "The action to be performed.", javaType = "String", enums = "UPSERT,RETRIEVE,DELETE")
        public static final String ACTION = "CamelQdrantAction";

        @Metadata(description = "Payload Selector.", javaType = "io.qdrant.client.grpc.Points$WithPayloadSelector")
        public static final String PAYLOAD_SELECTOR = "CamelQdrantPointsPayloadSelector";

        @Metadata(description = "Operation ID.", javaType = "long")
        public static final String OPERATION_ID = "CamelQdrantOperationID";

        @Metadata(description = "Operation Status.", javaType = "String")
        public static final String OPERATION_STATUS = "CamelQdrantOperationStatus";

        @Metadata(description = "Operation Status Value.", javaType = "int")
        public static final String OPERATION_STATUS_VALUE = "CamelQdrantOperationStatusValue";

        @Metadata(description = "Read Consistency.", javaType = "io.qdrant.client.grpc.Points$ReadConsistency")
        public static final String READ_CONSISTENCY = "CamelQdrantReadConsistency";

        @Metadata(description = "Include Payload.", javaType = "boolean", defaultValue = "true")
        public static final String INCLUDE_PAYLOAD = "CamelQdrantWithPayload";
        public static final boolean DEFAULT_INCLUDE_PAYLOAD = true;

        @Metadata(description = "Include Vectors.", javaType = "boolean", defaultValue = "false")
        public static final String INCLUDE_VECTORS = "CamelQdrantWithVectors";
        public static final boolean DEFAULT_INCLUDE_VECTORS = false;

        @Metadata(description = "The number of elements.", javaType = "int")
        public static final String SIZE = "CamelQdrantSize";
    }
}
