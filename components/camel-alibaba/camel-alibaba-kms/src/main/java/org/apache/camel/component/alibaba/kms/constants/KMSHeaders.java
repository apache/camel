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
package org.apache.camel.component.alibaba.kms.constants;

import org.apache.camel.spi.Metadata;

public final class KMSHeaders {

    @Metadata(label = "producer",
              description = "The ID, alias or ARN of the KMS key for encrypt and generateDataKey. This header replaces the"
                            + " keyId option. If this header is not set, an exchange property of the same name replaces the"
                            + " keyId option.",
              javaType = "String")
    public static final String KEY_ID = KMSProperties.KEY_ID;

    @Metadata(label = "producer",
              description = "The plaintext for encrypt. The producer encodes it in UTF-8 and sends it to KMS in Base64. This"
                            + " header replaces the plaintext option. If this header is not set, an exchange property of the"
                            + " same name replaces the plaintext option.",
              javaType = "String")
    public static final String PLAINTEXT = KMSProperties.PLAINTEXT;

    @Metadata(label = "producer",
              description = "The ciphertext blob for decrypt, for example from the response to encrypt or generateDataKey. This"
                            + " header replaces the ciphertextBlob option. If this header is not set, an exchange property of"
                            + " the same name replaces the ciphertextBlob option.",
              javaType = "String")
    public static final String CIPHERTEXT_BLOB = KMSProperties.CIPHERTEXT_BLOB;

    @Metadata(label = "producer",
              description = "The key spec of the data key for generateDataKey. The value must be AES_256 or AES_128. KMS does"
                            + " not use it when the number of bytes is set as well. This header replaces the keySpec option. If"
                            + " this header is not set, an exchange property of the same name replaces the keySpec option.",
              javaType = "String")
    public static final String KEY_SPEC = KMSProperties.KEY_SPEC;

    @Metadata(label = "producer",
              description = "The number of bytes of the data key for generateDataKey. The value must be from 1 to 1024. This"
                            + " header replaces the numberOfBytes option. If this header is not set, an exchange property of"
                            + " the same name replaces the numberOfBytes option.",
              javaType = "Integer")
    public static final String NUMBER_OF_BYTES = KMSProperties.NUMBER_OF_BYTES;

    @Metadata(label = "producer",
              description = "The request ID that KMS returns in the response to the operation. The producer sets it, unless the"
                            + " exchange fails.",
              javaType = "String")
    public static final String REQUEST_ID = KMSProperties.REQUEST_ID;

    private KMSHeaders() {
    }
}
