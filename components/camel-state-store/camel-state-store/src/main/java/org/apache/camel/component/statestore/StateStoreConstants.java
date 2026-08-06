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
package org.apache.camel.component.statestore;

import org.apache.camel.spi.Metadata;

/**
 * Constants for the State Store component.
 */
public final class StateStoreConstants {

    @Metadata(description = "The operation to perform", javaType = "org.apache.camel.component.statestore.StateStoreOperations",
              label = "producer")
    public static final String OPERATION = "CamelStateStoreOperation";

    @Metadata(description = "The key to use for the operation", javaType = "String", label = "producer")
    public static final String KEY = "CamelStateStoreKey";

    @Metadata(description = "Per-message TTL override in milliseconds. Takes precedence over the endpoint ttl option.",
              javaType = "Long", label = "producer")
    public static final String TTL = "CamelStateStoreTtl";

    private StateStoreConstants() {
    }
}
