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
package org.apache.camel.component.milvus;

import org.apache.camel.spi.Metadata;

public class Milvus {
    public static final String SCHEME = "milvus";

    private Milvus() {
    }

    public static class Headers {
        @Metadata(description = "The action to be performed.", javaType = "String",
                  enums = "CREATE_COLLECTION,CREATE_INDEX,UPSERT,INSERT,SEARCH,DELETE")
        public static final String ACTION = "CamelMilvusAction";

        @Metadata(description = "Operation ID.", javaType = "long")
        public static final String OPERATION_ID = "CamelMilvusOperationID";

        @Metadata(description = "Operation Status.", javaType = "String")
        public static final String OPERATION_STATUS = "CamelMilvusOperationStatus";

        @Metadata(description = "Operation Status Value.", javaType = "int")
        public static final String OPERATION_STATUS_VALUE = "CamelMilvusOperationStatusValue";

        @Metadata(description = "Include Payload.", javaType = "boolean", defaultValue = "true")
        public static final String INCLUDE_PAYLOAD = "CamelMilvusWithPayload";
        public static final boolean DEFAULT_INCLUDE_PAYLOAD = true;

        @Metadata(description = "Include Vectors.", javaType = "boolean", defaultValue = "false")
        public static final String INCLUDE_VECTORS = "CamelMilvusWithVectors";
        public static final boolean DEFAULT_INCLUDE_VECTORS = false;

        @Metadata(description = "The number of elements.", javaType = "int")
        public static final String SIZE = "CamelMilvusSize";
    }
}
