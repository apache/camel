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
package org.apache.camel.component.pqc;

import org.apache.camel.spi.Metadata;

/**
 * Constants used in PQC module
 */
public interface PQCConstants {
    @Metadata(description = "The operation we want to perform", javaType = "String")
    String OPERATION = "CamelPQCOperation";

    @Metadata(description = "The signature of a body", javaType = "String")
    String SIGNATURE = "CamelPQCSignature";

    @Metadata(description = "The result of verification of a Body signature", javaType = "Boolean")
    String VERIFY = "CamelPQCVerification";

    @Metadata(description = "The extracted key in case of extractSecretKeyFromEncapsulation operation and storeExtractedSecretKeyAsHeader option enabled",
              javaType = "Boolean")
    String SECRET_KEY = "CamelPQCSecretKey";

    @Metadata(description = "The remaining signatures for a stateful key", javaType = "Long")
    String REMAINING_SIGNATURES = "CamelPQCRemainingSignatures";

    @Metadata(description = "The key state for a stateful key",
              javaType = "org.apache.camel.component.pqc.stateful.StatefulKeyState")
    String KEY_STATE = "CamelPQCKeyState";

    @Metadata(description = "The key ID for stateful key operations", javaType = "String")
    String KEY_ID = "CamelPQCKeyId";

    @Metadata(description = "The generated key pair", javaType = "java.security.KeyPair")
    String KEY_PAIR = "CamelPQCKeyPair";

    @Metadata(description = "The key format for import/export operations", javaType = "String")
    String KEY_FORMAT = "CamelPQCKeyFormat";

    @Metadata(description = "The exported key data", javaType = "byte[]")
    String EXPORTED_KEY = "CamelPQCExportedKey";

    @Metadata(description = "The key metadata", javaType = "org.apache.camel.component.pqc.lifecycle.KeyMetadata")
    String KEY_METADATA = "CamelPQCKeyMetadata";

    @Metadata(description = "List of key metadata", javaType = "java.util.List")
    String KEY_LIST = "CamelPQCKeyList";

    @Metadata(description = "The algorithm for key generation", javaType = "String")
    String ALGORITHM = "CamelPQCAlgorithm";

    @Metadata(description = "Include private key in export", javaType = "Boolean")
    String INCLUDE_PRIVATE = "CamelPQCIncludePrivate";

    @Metadata(description = "Revocation reason", javaType = "String")
    String REVOCATION_REASON = "CamelPQCRevocationReason";
}
