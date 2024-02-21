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
package org.apache.camel.component.crypto;

import org.apache.camel.spi.Metadata;

/**
 * <code>DigitalSignatureConstants</code> contains Constants for use as Message header keys.
 */
public final class DigitalSignatureConstants {
    @Metadata(description = "The PrivateKey that should be used to sign the message", javaType = "java.security.PrivateKey")
    public static final String SIGNATURE_PRIVATE_KEY = "CamelSignaturePrivateKey";
    @Metadata(description = "The Certificate or PublicKey that should be used to verify the signature",
              javaType = "Certificate or PublicKey")
    public static final String SIGNATURE_PUBLIC_KEY_OR_CERT = "CamelSignaturePublicKeyOrCert";
    public static final String SIGNATURE = "CamelDigitalSignature";
    @Metadata(description = "The alias used to query the KeyStore for keys and Certificates to be\n" +
                            " used in signing and verifying exchanges",
              javaType = "String")
    public static final String KEYSTORE_ALIAS = "CamelSignatureKeyStoreAlias";
    @Metadata(description = "The password used to access an aliased PrivateKey in the KeyStore.", javaType = "char[]")
    public static final String KEYSTORE_PASSWORD = "CamelSignatureKeyStorePassword";

    private DigitalSignatureConstants() {
        // Helper Class
    }
}
