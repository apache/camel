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

interface AS2AlgorithmConstants {
    static final String DES_CBC = "DES_CBC";
    static final String DES_EDE3_CBC = "DES_EDE3_CBC";
    static final String RC2_CBC = "RC2_CBC";
    static final String IDEA_CBC = "IDEA_CBC";
    static final String CAST5_CBC = "CAST5_CBC";
    static final String AES128_CBC = "AES128_CBC";
    static final String AES192_CBC = "AES192_CBC";
    static final String AES256_CBC = "AES256_CBC";
    static final String AES128_CCM = "AES128_CCM";
    static final String AES192_CCM = "AES192_CCM";
    static final String AES256_CCM = "AES256_CCM";
    static final String AES128_GCM = "AES128_GCM";
    static final String AES192_GCM = "AES192_GCM";
    static final String AES256_GCM = "AES256_GCM";
    static final String CAMELLIA128_CBC = "CAMELLIA128_CBC";
    static final String CAMELLIA192_CBC = "CAMELLIA192_CBC";
    static final String CAMELLIA256_CBC = "CAMELLIA256_CBC";
    static final String GOST28147_GCFB = "GOST28147_GCFB";
    static final String SEED_CBC = "SEED_CBC";
    static final String DES_EDE3_WRAP = "DES_EDE3_WRAP";
    static final String AES128_WRAP = "AES128_WRAP";
    static final String AES192_WRAP = "AES192_WRAP";
    static final String AES256_WRAP = "AES256_WRAP";
    static final String CAMELLIA128_WRAP = "CAMELLIA128_WRAP";
    static final String CAMELLIA192_WRAP = "CAMELLIA192_WRAP";
    static final String CAMELLIA256_WRAP = "CAMELLIA256_WRAP";
    static final String SEED_WRAP = "SEED_WRAP";
    static final String GOST28147_WRAP = "GOST28147_WRAP";
    static final String GOST28147_CRYPTOPRO_WRAP = "GOST28147_CRYPTOPRO_WRAP";
    static final String ECDH_SHA1KDF = "ECDH_SHA1KDF";
    static final String ECCDH_SHA1KDF = "ECCDH_SHA1KDF";
    static final String ECMQV_SHA1KDF = "ECMQV_SHA1KDF";
    static final String ECDH_SHA224KDF = "ECDH_SHA224KDF";
    static final String ECCDH_SHA224KDF = "ECCDH_SHA224KDF";
    static final String ECMQV_SHA224KDF = "ECMQV_SHA224KDF";
    static final String ECDH_SHA256KDF = "ECDH_SHA256KDF";
    static final String ECCDH_SHA256KDF = "ECCDH_SHA256KDF";
    static final String ECMQV_SHA256KDF = "ECMQV_SHA256KDF";
    static final String ECDH_SHA384KDF = "ECDH_SHA384KDF";
    static final String ECCDH_SHA384KDF = "ECCDH_SHA384KDF";
    static final String ECMQV_SHA384KDF = "ECMQV_SHA384KDF";
    static final String ECDH_SHA512KDF = "ECDH_SHA512KDF";
    static final String ECCDH_SHA512KDF = "ECCDH_SHA512KDF";
    static final String ECMQV_SHA512KDF = "ECMQV_SHA512KDF";
    static final String ECDHGOST3410_2001 = "ECDHGOST3410_2001";
    static final String ECDHGOST3410_2012_256 = "ECDHGOST3410_2012_256";
    static final String ECDHGOST3410_2012_512 = "ECDHGOST3410_2012_512";
    static final String SHA1 = "SHA1";
    static final String SHA224 = "SHA224";
    static final String SHA256 = "SHA256";
    static final String SHA384 = "SHA384";
    static final String SHA512 = "SHA512";
    static final String MD5 = "MD5";
    static final String GOST3411 = "GOST3411";
    static final String GOST3411_2012_256 = "GOST3411_2012_256";
    static final String GOST3411_2012_512 = "GOST3411_2012_512";
    static final String RIPEMD128 = "RIPEMD128";
    static final String RIPEMD160 = "RIPEMD160";
    static final String RIPEMD256 = "RIPEMD256";
}

public enum AS2Algorithm {
    DES_CBC(AS2AlgorithmConstants.DES_CBC, CMSAlgorithm.DES_CBC),
    DES_EDE3_CBC(AS2AlgorithmConstants.DES_EDE3_CBC, CMSAlgorithm.DES_EDE3_CBC),
    RC2_CBC(AS2AlgorithmConstants.RC2_CBC, CMSAlgorithm.RC2_CBC),
    IDEA_CBC(AS2AlgorithmConstants.IDEA_CBC, CMSAlgorithm.IDEA_CBC),
    CAST5_CBC(AS2AlgorithmConstants.CAST5_CBC, CMSAlgorithm.CAST5_CBC),
    AES128_CBC(AS2AlgorithmConstants.AES128_CBC, CMSAlgorithm.AES128_CBC),
    AES192_CBC(AS2AlgorithmConstants.AES192_CBC, CMSAlgorithm.AES192_CBC),
    AES256_CBC(AS2AlgorithmConstants.AES256_CBC, CMSAlgorithm.AES256_CBC),
    AES128_CCM(AS2AlgorithmConstants.AES128_CCM, CMSAlgorithm.AES128_CCM),
    AES192_CCM(AS2AlgorithmConstants.AES192_CCM, CMSAlgorithm.AES192_CCM),
    AES256_CCM(AS2AlgorithmConstants.AES256_CCM, CMSAlgorithm.AES256_CCM),
    AES128_GCM(AS2AlgorithmConstants.AES128_GCM, CMSAlgorithm.AES128_GCM),
    AES192_GCM(AS2AlgorithmConstants.AES192_GCM, CMSAlgorithm.AES192_GCM),
    AES256_GCM(AS2AlgorithmConstants.AES256_GCM, CMSAlgorithm.AES256_GCM),
    CAMELLIA128_CBC(AS2AlgorithmConstants.CAMELLIA128_CBC, CMSAlgorithm.CAMELLIA128_CBC),
    CAMELLIA192_CBC(AS2AlgorithmConstants.CAMELLIA192_CBC, CMSAlgorithm.CAMELLIA192_CBC),
    CAMELLIA256_CBC(AS2AlgorithmConstants.CAMELLIA256_CBC, CMSAlgorithm.CAMELLIA256_CBC),
    GOST28147_GCFB(AS2AlgorithmConstants.GOST28147_GCFB, CMSAlgorithm.GOST28147_GCFB),
    SEED_CBC(AS2AlgorithmConstants.SEED_CBC, CMSAlgorithm.SEED_CBC),
    DES_EDE3_WRAP(AS2AlgorithmConstants.DES_EDE3_WRAP, CMSAlgorithm.DES_EDE3_WRAP),
    AES128_WRAP(AS2AlgorithmConstants.AES128_WRAP, CMSAlgorithm.AES128_WRAP),
    AES192_WRAP(AS2AlgorithmConstants.AES192_WRAP, CMSAlgorithm.AES192_WRAP),
    AES256_WRAP(AS2AlgorithmConstants.AES256_WRAP, CMSAlgorithm.AES256_WRAP),
    CAMELLIA128_WRAP(AS2AlgorithmConstants.CAMELLIA128_WRAP, CMSAlgorithm.CAMELLIA128_WRAP),
    CAMELLIA192_WRAP(AS2AlgorithmConstants.CAMELLIA192_WRAP, CMSAlgorithm.CAMELLIA192_WRAP),
    CAMELLIA256_WRAP(AS2AlgorithmConstants.CAMELLIA256_WRAP, CMSAlgorithm.CAMELLIA256_WRAP),
    SEED_WRAP(AS2AlgorithmConstants.SEED_WRAP, CMSAlgorithm.SEED_WRAP),
    GOST28147_WRAP(AS2AlgorithmConstants.GOST28147_WRAP, CMSAlgorithm.GOST28147_WRAP),
    GOST28147_CRYPTOPRO_WRAP(AS2AlgorithmConstants.GOST28147_CRYPTOPRO_WRAP, CMSAlgorithm.GOST28147_CRYPTOPRO_WRAP),
    ECDH_SHA1KDF(AS2AlgorithmConstants.ECDH_SHA1KDF, CMSAlgorithm.ECDH_SHA1KDF),
    ECCDH_SHA1KDF(AS2AlgorithmConstants.ECCDH_SHA1KDF, CMSAlgorithm.ECCDH_SHA1KDF),
    ECMQV_SHA1KDF(AS2AlgorithmConstants.ECMQV_SHA1KDF, CMSAlgorithm.ECMQV_SHA1KDF),
    ECDH_SHA224KDF(AS2AlgorithmConstants.ECDH_SHA224KDF, CMSAlgorithm.ECDH_SHA224KDF),
    ECCDH_SHA224KDF(AS2AlgorithmConstants.ECCDH_SHA224KDF, CMSAlgorithm.ECCDH_SHA224KDF),
    ECMQV_SHA224KDF(AS2AlgorithmConstants.ECMQV_SHA224KDF, CMSAlgorithm.ECMQV_SHA224KDF),
    ECDH_SHA256KDF(AS2AlgorithmConstants.ECDH_SHA256KDF, CMSAlgorithm.ECDH_SHA256KDF),
    ECCDH_SHA256KDF(AS2AlgorithmConstants.ECCDH_SHA256KDF, CMSAlgorithm.ECCDH_SHA256KDF),
    ECMQV_SHA256KDF(AS2AlgorithmConstants.ECMQV_SHA256KDF, CMSAlgorithm.ECMQV_SHA256KDF),
    ECDH_SHA384KDF(AS2AlgorithmConstants.ECDH_SHA384KDF, CMSAlgorithm.ECDH_SHA384KDF),
    ECCDH_SHA384KDF(AS2AlgorithmConstants.ECCDH_SHA384KDF, CMSAlgorithm.ECCDH_SHA384KDF),
    ECMQV_SHA384KDF(AS2AlgorithmConstants.ECMQV_SHA384KDF, CMSAlgorithm.ECMQV_SHA384KDF),
    ECDH_SHA512KDF(AS2AlgorithmConstants.ECDH_SHA512KDF, CMSAlgorithm.ECDH_SHA512KDF),
    ECCDH_SHA512KDF(AS2AlgorithmConstants.ECCDH_SHA512KDF, CMSAlgorithm.ECCDH_SHA512KDF),
    ECMQV_SHA512KDF(AS2AlgorithmConstants.ECMQV_SHA512KDF, CMSAlgorithm.ECMQV_SHA512KDF),
    ECDHGOST3410_2001(AS2AlgorithmConstants.ECDHGOST3410_2001, CMSAlgorithm.ECDHGOST3410_2001),
    ECDHGOST3410_2012_256(AS2AlgorithmConstants.ECDHGOST3410_2012_256, CMSAlgorithm.ECDHGOST3410_2012_256),
    ECDHGOST3410_2012_512(AS2AlgorithmConstants.ECDHGOST3410_2012_512, CMSAlgorithm.ECDHGOST3410_2012_512),
    SHA1(AS2AlgorithmConstants.SHA1, CMSAlgorithm.SHA1),
    SHA224(AS2AlgorithmConstants.SHA224, CMSAlgorithm.SHA224),
    SHA256(AS2AlgorithmConstants.SHA256, CMSAlgorithm.SHA256),
    SHA384(AS2AlgorithmConstants.SHA384, CMSAlgorithm.SHA384),
    SHA512(AS2AlgorithmConstants.SHA512, CMSAlgorithm.SHA512),
    MD5(AS2AlgorithmConstants.MD5, CMSAlgorithm.MD5),
    GOST3411(AS2AlgorithmConstants.GOST3411, CMSAlgorithm.GOST3411),
    GOST3411_2012_256(AS2AlgorithmConstants.GOST3411_2012_256, CMSAlgorithm.GOST3411_2012_256),
    GOST3411_2012_512(AS2AlgorithmConstants.GOST3411_2012_512, CMSAlgorithm.GOST3411_2012_512),
    RIPEMD128(AS2AlgorithmConstants.RIPEMD128, CMSAlgorithm.RIPEMD128),
    RIPEMD160(AS2AlgorithmConstants.RIPEMD160, CMSAlgorithm.RIPEMD160),
    RIPEMD256(AS2AlgorithmConstants.RIPEMD256, CMSAlgorithm.RIPEMD256);
    
    private String algorithmName;
    private ASN1ObjectIdentifier algorithmOID;
    
    private AS2Algorithm(String algorithmName, ASN1ObjectIdentifier algorithmOID) {
        this.algorithmName = algorithmName;
        this.algorithmOID = algorithmOID;
    }
    public String getAlgorithmName() {
        return algorithmName;
    }
    public ASN1ObjectIdentifier getAlgorithmOID() {
        return algorithmOID;
    }
    
    public static AS2Algorithm getAS2Algorithm(String algorithmName) {
        AS2Algorithm as2Algorithm;
        switch (algorithmName) {
        case "DES_CBC":
            as2Algorithm = DES_CBC;
            break;
        case "DES_EDE3_CBC":
            as2Algorithm = DES_EDE3_CBC;
            break;
        case "RC2_CBC":
            as2Algorithm = RC2_CBC;
            break;
        case "IDEA_CBC":
            as2Algorithm = IDEA_CBC;
            break;
        case "CAST5_CBC":
            as2Algorithm = CAST5_CBC;
            break;
        case "AES128_CBC":
            as2Algorithm = AES128_CBC;
            break;
        case "AES192_CBC":
            as2Algorithm = AES192_CBC;
            break;
        case "AES256_CBC":
            as2Algorithm = AES256_CBC;
            break;
        case "AES128_CCM":
            as2Algorithm = AES128_CCM;
            break;
        case "AES192_CCM":
            as2Algorithm = AES192_CCM;
            break;
        case "AES256_CCM":
            as2Algorithm = AES256_CCM;
            break;
        case "AES128_GCM":
            as2Algorithm = AES128_GCM;
            break;
        case "AES192_GCM":
            as2Algorithm = AES192_GCM;
            break;
        case "AES256_GCM":
            as2Algorithm = AES256_GCM;
            break;
        case "CAMELLIA128_CBC":
            as2Algorithm = CAMELLIA128_CBC;
            break;
        case "CAMELLIA192_CBC":
            as2Algorithm = CAMELLIA192_CBC;
            break;
        case "CAMELLIA256_CBC":
            as2Algorithm = CAMELLIA256_CBC;
            break;
        case "GOST28147_GCFB":
            as2Algorithm = GOST28147_GCFB;
            break;
        case "SEED_CBC":
            as2Algorithm = SEED_CBC;
            break;
        case "DES_EDE3_WRAP":
            as2Algorithm = DES_EDE3_WRAP;
            break;
        case "AES128_WRAP":
            as2Algorithm = AES128_WRAP;
            break;
        case "AES192_WRAP":
            as2Algorithm = AES192_WRAP;
            break;
        case "AES256_WRAP":
            as2Algorithm = AES256_WRAP;
            break;
        case "CAMELLIA128_WRAP":
            as2Algorithm = CAMELLIA128_WRAP;
            break;
        case "CAMELLIA192_WRAP":
            as2Algorithm = CAMELLIA192_WRAP;
            break;
        case "CAMELLIA256_WRAP":
            as2Algorithm = CAMELLIA256_WRAP;
            break;
        case "SEED_WRAP":
            as2Algorithm = SEED_WRAP;
            break;
        case "GOST28147_WRAP":
            as2Algorithm = GOST28147_WRAP;
            break;
        case "GOST28147_CRYPTOPRO_WRAP":
            as2Algorithm = GOST28147_CRYPTOPRO_WRAP;
            break;
        case "ECDH_SHA1KDF":
            as2Algorithm = ECDH_SHA1KDF;
            break;
        case "ECCDH_SHA1KDF":
            as2Algorithm = ECCDH_SHA1KDF;
            break;
        case "ECMQV_SHA1KDF":
            as2Algorithm = ECMQV_SHA1KDF;
            break;
        case "ECDH_SHA224KDF":
            as2Algorithm = ECDH_SHA224KDF;
            break;
        case "ECCDH_SHA224KDF":
            as2Algorithm = ECCDH_SHA224KDF;
            break;
        case "ECMQV_SHA224KDF":
            as2Algorithm = ECMQV_SHA224KDF;
            break;
        case "ECDH_SHA256KDF":
            as2Algorithm = ECDH_SHA256KDF;
            break;
        case "ECCDH_SHA256KDF":
            as2Algorithm = ECCDH_SHA256KDF;
            break;
        case "ECMQV_SHA256KDF":
            as2Algorithm = ECMQV_SHA256KDF;
            break;
        case "ECDH_SHA384KDF":
            as2Algorithm = ECDH_SHA384KDF;
            break;
        case "ECCDH_SHA384KDF":
            as2Algorithm = ECCDH_SHA384KDF;
            break;
        case "ECMQV_SHA384KDF":
            as2Algorithm = ECMQV_SHA384KDF;
            break;
        case "ECDH_SHA512KDF":
            as2Algorithm = ECDH_SHA512KDF;
            break;
        case "ECCDH_SHA512KDF":
            as2Algorithm = ECCDH_SHA512KDF;
            break;
        case "ECMQV_SHA512KDF":
            as2Algorithm = ECMQV_SHA512KDF;
            break;
        case "ECDHGOST3410_2001":
            as2Algorithm = ECDHGOST3410_2001;
            break;
        case "ECDHGOST3410_2012_256":
            as2Algorithm = ECDHGOST3410_2012_256;
            break;
        case "ECDHGOST3410_2012_512":
            as2Algorithm = ECDHGOST3410_2012_512;
            break;
        case "SHA1":
            as2Algorithm = SHA1;
            break;
        case "SHA224":
            as2Algorithm = SHA224;
            break;
        case "SHA256":
            as2Algorithm = SHA256;
            break;
        case "SHA384":
            as2Algorithm = SHA384;
            break;
        case "SHA512":
            as2Algorithm = SHA512;
            break;
        case "MD5":
            as2Algorithm = MD5;
            break;
        case "GOST3411":
            as2Algorithm = GOST3411;
            break;
        case "GOST3411_2012_256":
            as2Algorithm = GOST3411_2012_256;
            break;
        case "GOST3411_2012_512":
            as2Algorithm = GOST3411_2012_512;
            break;
        case "RIPEMD128":
            as2Algorithm = RIPEMD128;
            break;
        case "RIPEMD160":
            as2Algorithm = RIPEMD160;
            break;
        case "RIPEMD256":
            as2Algorithm = RIPEMD256;
            break;

        default:
            throw new IllegalArgumentException("Unsupported algorithm '" + algorithmName + "'");
        }
        return as2Algorithm;
    }
    
}
