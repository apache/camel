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
package org.apache.camel.component.azure.key.vault;

import org.apache.camel.spi.Metadata;

public final class KeyVaultConstants {
    private static final String HEADER_PREFIX = "CamelAzureKeyVault";

    // headers set by the producer only
    @Metadata(label = "producer", description = "The operation that the producer performs. It replaces the operation option.",
              javaType = "org.apache.camel.component.azure.key.vault.KeyVaultOperation")
    public static final String OPERATION = HEADER_PREFIX + "ProducerOperation";
    // headers set by the producer only
    @Metadata(label = "producer",
              description = "The name of the secret, required by every operation except updateSecretProperties."
                            + " createSecret adds a new version when the secret already exists, and"
                            + " getSecret returns the value of the latest version.",
              javaType = "String")
    public static final String SECRET_NAME = HEADER_PREFIX + "SecretName";
    @Metadata(label = "producer",
              description = "The SecretProperties for updateSecretProperties, which requires this header. Its name"
                            + " and its version select the secret. Only the attributes that are set in it, such as"
                            + " enabled, expiresOn, notBefore, contentType and tags, are changed, and the"
                            + " value of the secret is not.",
              javaType = "com.azure.security.keyvault.secrets.models.SecretProperties")
    public static final String SECRET_PROPERTIES = HEADER_PREFIX + "SecretProperties";

    private KeyVaultConstants() {
    }
}
