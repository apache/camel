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
package org.apache.camel.maven.packaging.endpoint;

import org.apache.camel.spi.Metadata;

public final class SomeExtendingConstants {
    @Metadata(description = "key on extended overriding parent", label = "my label", displayName = "my extended display name",
              javaType = "org.apache.camel.maven.packaging.endpoint.SomeEndpoint$MyEnum", required = true,
              defaultValue = "VAL1", secret = true)
    public static final String KEY_FULL = "KEY_FULL";

    @Metadata(description = "key on extended class")
    public static final String KEY_EXTENDED = "KEY_EXTENDED";

    private SomeExtendingConstants() {
    }
}
