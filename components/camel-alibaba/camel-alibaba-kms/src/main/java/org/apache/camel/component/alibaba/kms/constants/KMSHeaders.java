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

    @Metadata(label = "producer", description = "KMS key id override", javaType = "String")
    public static final String KEY_ID = KMSProperties.KEY_ID;

    @Metadata(label = "producer", description = "Plaintext to encrypt", javaType = "String")
    public static final String PLAINTEXT = KMSProperties.PLAINTEXT;

    @Metadata(label = "producer", description = "Ciphertext blob to decrypt", javaType = "String")
    public static final String CIPHERTEXT_BLOB = KMSProperties.CIPHERTEXT_BLOB;

    @Metadata(label = "producer", description = "Key spec for generateDataKey", javaType = "String")
    public static final String KEY_SPEC = KMSProperties.KEY_SPEC;

    @Metadata(label = "producer", description = "Number of bytes for generateDataKey", javaType = "Integer")
    public static final String NUMBER_OF_BYTES = KMSProperties.NUMBER_OF_BYTES;

    @Metadata(label = "producer", description = "Alibaba Cloud request id", javaType = "String")
    public static final String REQUEST_ID = KMSProperties.REQUEST_ID;

    private KMSHeaders() {
    }
}
