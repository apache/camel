/**
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
import org.bouncycastle.cms.CMSAlgorithm;

public enum AS2Algorithm {
    DES_CBC(CMSAlgorithm.DES_CBC),
    DES_EDE3_CBC(CMSAlgorithm.DES_EDE3_CBC),
    RC2_CBC(CMSAlgorithm.RC2_CBC),
    IDEA_CBC(CMSAlgorithm.IDEA_CBC),
    CAST5_CBC(CMSAlgorithm.CAST5_CBC),
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
    GOST28147_GCFB(CMSAlgorithm.GOST28147_GCFB),
    SEED_CBC(CMSAlgorithm.SEED_CBC),
    DES_EDE3_WRAP(CMSAlgorithm.DES_EDE3_WRAP),
    AES128_WRAP(CMSAlgorithm.AES128_WRAP),
    AES192_WRAP(CMSAlgorithm.AES192_WRAP),
    AES256_WRAP(CMSAlgorithm.AES256_WRAP),
    CAMELLIA128_WRAP(CMSAlgorithm.CAMELLIA128_WRAP),
    CAMELLIA192_WRAP(CMSAlgorithm.CAMELLIA192_WRAP),
    CAMELLIA256_WRAP(CMSAlgorithm.CAMELLIA256_WRAP),
    SEED_WRAP(CMSAlgorithm.SEED_WRAP),
    GOST28147_WRAP(CMSAlgorithm.GOST28147_WRAP),
    GOST28147_CRYPTOPRO_WRAP(CMSAlgorithm.GOST28147_CRYPTOPRO_WRAP),
    ECDH_SHA1KDF(CMSAlgorithm.ECDH_SHA1KDF),
    ECCDH_SHA1KDF(CMSAlgorithm.ECCDH_SHA1KDF),
    ECMQV_SHA1KDF(CMSAlgorithm.ECMQV_SHA1KDF),
    ECDH_SHA224KDF(CMSAlgorithm.ECDH_SHA224KDF),
    ECCDH_SHA224KDF(CMSAlgorithm.ECCDH_SHA224KDF),
    ECMQV_SHA224KDF(CMSAlgorithm.ECMQV_SHA224KDF),
    ECDH_SHA256KDF(CMSAlgorithm.ECDH_SHA256KDF),
    ECCDH_SHA256KDF(CMSAlgorithm.ECCDH_SHA256KDF),
    ECMQV_SHA256KDF(CMSAlgorithm.ECMQV_SHA256KDF),
    ECDH_SHA384KDF(CMSAlgorithm.ECDH_SHA384KDF),
    ECCDH_SHA384KDF(CMSAlgorithm.ECCDH_SHA384KDF),
    ECMQV_SHA384KDF(CMSAlgorithm.ECMQV_SHA384KDF),
    ECDH_SHA512KDF(CMSAlgorithm.ECDH_SHA512KDF),
    ECCDH_SHA512KDF(CMSAlgorithm.ECCDH_SHA512KDF),
    ECMQV_SHA512KDF(CMSAlgorithm.ECMQV_SHA512KDF),
    ECDHGOST3410_2001(CMSAlgorithm.ECDHGOST3410_2001),
    ECDHGOST3410_2012_256(CMSAlgorithm.ECDHGOST3410_2012_256),
    ECDHGOST3410_2012_512(CMSAlgorithm.ECDHGOST3410_2012_512),
    SHA1(CMSAlgorithm.SHA1),
    SHA224(CMSAlgorithm.SHA224),
    SHA256(CMSAlgorithm.SHA256),
    SHA384(CMSAlgorithm.SHA384),
    SHA512(CMSAlgorithm.SHA512),
    MD5(CMSAlgorithm.MD5),
    GOST3411(CMSAlgorithm.GOST3411),
    GOST3411_2012_256(CMSAlgorithm.GOST3411_2012_256),
    GOST3411_2012_512(CMSAlgorithm.GOST3411_2012_512),
    RIPEMD128(CMSAlgorithm.RIPEMD128),
    RIPEMD160(CMSAlgorithm.RIPEMD160),
    RIPEMD256(CMSAlgorithm.RIPEMD256);
    
    private ASN1ObjectIdentifier algorithmOID;
    
    private AS2Algorithm(ASN1ObjectIdentifier algorithmOID) {
        this.algorithmOID = algorithmOID;
    }
    public String getAlgorithmName() {
        return name().toString();
    }
    public ASN1ObjectIdentifier getAlgorithmOID() {
        return algorithmOID;
    }
    
    public static AS2Algorithm getAS2Algorithm(String algorithmName) {
        return Enum.valueOf(AS2Algorithm.class, algorithmName);
    }
    
}
