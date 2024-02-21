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
package org.apache.camel.component.as2.api;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.cms.CMSAlgorithm;

public enum AS2EncryptionAlgorithm {
    AES128_CBC(CMSAlgorithm.AES128_CBC),
    AES192_CBC(CMSAlgorithm.AES192_CBC),
    AES256_CBC(CMSAlgorithm.AES256_CBC),
    AES128_CCM(CMSAlgorithm.AES128_CCM),
    AES192_CCM(CMSAlgorithm.AES192_CCM),
    AES256_CCM(CMSAlgorithm.AES256_CCM),
    AES128_GCM(CMSAlgorithm.AES128_GCM),
    AES192_GCM(CMSAlgorithm.AES192_GCM),
    AES256_GCM(CMSAlgorithm.AES256_GCM),
    CAMELLIA128_CBC(CMSAlgorithm.CAMELLIA128_CBC),
    CAMELLIA192_CBC(CMSAlgorithm.CAMELLIA192_CBC),
    CAMELLIA256_CBC(CMSAlgorithm.CAMELLIA256_CBC),
    CAST5_CBC(CMSAlgorithm.CAST5_CBC),
    DES_CBC(CMSAlgorithm.DES_CBC),
    DES_EDE3_CBC(CMSAlgorithm.DES_EDE3_CBC),
    GOST28147_GCFB(CMSAlgorithm.GOST28147_GCFB),
    IDEA_CBC(CMSAlgorithm.IDEA_CBC),
    RC2_CBC(CMSAlgorithm.RC2_CBC),
    RC4(PKCSObjectIdentifiers.rc4),
    SEED_CBC(CMSAlgorithm.SEED_CBC);

    private final ASN1ObjectIdentifier algorithmOID;

    private AS2EncryptionAlgorithm(ASN1ObjectIdentifier algorithmOID) {
        this.algorithmOID = algorithmOID;
    }

    public String getAlgorithmName() {
        return this.name();
    }

    public ASN1ObjectIdentifier getAlgorithmOID() {
        return algorithmOID;
    }

    public static AS2EncryptionAlgorithm getAS2Algorithm(String algorithmName) {
        return AS2EncryptionAlgorithm.valueOf(algorithmName);
    }

}
