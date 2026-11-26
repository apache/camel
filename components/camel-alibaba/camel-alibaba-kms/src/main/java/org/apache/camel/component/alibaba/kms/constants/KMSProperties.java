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

/**
 * The names the producer reads as an exchange property. Each one is read as a message header too, which
 * {@link KMSHeaders} documents for the catalog.
 */
public final class KMSProperties {

    public static final String OPERATION = KMSHeaders.OPERATION;
    public static final String KEY_ID = KMSHeaders.KEY_ID;
    public static final String PLAINTEXT = KMSHeaders.PLAINTEXT;
    public static final String CIPHERTEXT_BLOB = KMSHeaders.CIPHERTEXT_BLOB;
    public static final String KEY_SPEC = KMSHeaders.KEY_SPEC;
    public static final String NUMBER_OF_BYTES = KMSHeaders.NUMBER_OF_BYTES;
    public static final String REQUEST_ID = KMSHeaders.REQUEST_ID;

    private KMSProperties() {
    }
}
