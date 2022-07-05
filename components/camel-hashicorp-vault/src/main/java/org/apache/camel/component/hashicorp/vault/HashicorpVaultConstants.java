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
package org.apache.camel.component.hashicorp.vault;

import org.apache.camel.spi.Metadata;

public final class HashicorpVaultConstants {
    private static final String HEADER_PREFIX = "CamelHashicorpVault";

    // headers set by the producer only
    @Metadata(label = "producer", description = "Overrides the desired operation to be used in the producer.",
              javaType = "String")
    public static final String OPERATION = HEADER_PREFIX + "ProducerOperation";

    // headers set by the producer only
    @Metadata(label = "producer", description = "Set the desired secret path as header.",
              javaType = "String")
    public static final String SECRET_PATH = HEADER_PREFIX + "SecretPath";

    // headers set by the producer only
    @Metadata(label = "producer", description = "Set the desired secret version as header.",
              javaType = "String")
    public static final String SECRET_VERSION = HEADER_PREFIX + "SecretVersion";

    private HashicorpVaultConstants() {
    }
}
