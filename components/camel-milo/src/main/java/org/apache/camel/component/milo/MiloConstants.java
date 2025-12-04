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

package org.apache.camel.component.milo;

import org.apache.camel.spi.Metadata;

public final class MiloConstants {

    // The schemes
    public static final String SCHEME_BROWSE = "milo-browse";
    public static final String SCHEME_CLIENT = "milo-client";
    public static final String SCHEME_SERVER = "milo-server";

    @Metadata(label = "producer", description = "The node ids.", javaType = "List")
    public static final String HEADER_NODE_IDS = "CamelMiloNodeIds";

    @Metadata(
            label = "producer",
            description = "The \"await\" setting for writes.",
            javaType = "Boolean",
            applicableFor = SCHEME_CLIENT)
    public static final String HEADER_AWAIT = "await";

    private MiloConstants() {}
}
