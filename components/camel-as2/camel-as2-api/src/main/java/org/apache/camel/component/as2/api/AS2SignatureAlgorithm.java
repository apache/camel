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

import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.bsi.BSIObjectIdentifiers;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.oiw.OIWObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.RSASSAPSSparams;
import org.bouncycastle.asn1.teletrust.TeleTrusTObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers;

interface AS2SignatureAlgorithmParams {
    
    AlgorithmIdentifier SHA1ALGID = new AlgorithmIdentifier(OIWObjectIdentifiers.idSHA1, DERNull.INSTANCE);
    RSASSAPSSparams SHA1ALGPARAMS = new RSASSAPSSparams(SHA1ALGID,
            new AlgorithmIdentifier(PKCSObjectIdentifiers.id_mgf1, SHA1ALGID), new ASN1Integer(20), new ASN1Integer(1));
    
    AlgorithmIdentifier SHA224ALGID = new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha224, DERNull.INSTANCE);
    RSASSAPSSparams SHA224ALGPARAMS = new RSASSAPSSparams(SHA224ALGID,
            new AlgorithmIdentifier(PKCSObjectIdentifiers.id_mgf1, SHA224ALGID), new ASN1Integer(28), new ASN1Integer(1));
    
    AlgorithmIdentifier SHA256ALGID = new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256, DERNull.INSTANCE);
    RSASSAPSSparams SHA256ALGPARAMS = new RSASSAPSSparams(SHA256ALGID,
            new AlgorithmIdentifier(PKCSObjectIdentifiers.id_mgf1, SHA256ALGID), new ASN1Integer(32), new ASN1Integer(1));
    
    AlgorithmIdentifier SHA384ALGID = new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha384, DERNull.INSTANCE);
    RSASSAPSSparams SHA384ALGPARAMS = new RSASSAPSSparams(SHA384ALGID,
            new AlgorithmIdentifier(PKCSObjectIdentifiers.id_mgf1, SHA384ALGID), new ASN1Integer(48), new ASN1Integer(1));
    
    AlgorithmIdentifier SHA512ALGID = new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha512, DERNull.INSTANCE);
    RSASSAPSSparams SHA512ALGPARAMS = new RSASSAPSSparams(SHA512ALGID,
            new AlgorithmIdentifier(PKCSObjectIdentifiers.id_mgf1, SHA512ALGID), new ASN1Integer(64), new ASN1Integer(1));

    AlgorithmIdentifier SHA3_224ALGID = new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha3_224, DERNull.INSTANCE);
    RSASSAPSSparams SHA3_224ALGPARAMS = new RSASSAPSSparams(SHA3_224ALGID,
            new AlgorithmIdentifier(PKCSObjectIdentifiers.id_mgf1, SHA3_224ALGID), new ASN1Integer(28), new ASN1Integer(1));
    
    AlgorithmIdentifier SHA3_256ALGID = new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha3_256, DERNull.INSTANCE);
    RSASSAPSSparams SHA3_256ALGPARAMS = new RSASSAPSSparams(SHA3_256ALGID,
            new AlgorithmIdentifier(PKCSObjectIdentifiers.id_mgf1, SHA3_256ALGID), new ASN1Integer(32), new ASN1Integer(1));
    
    AlgorithmIdentifier SHA3_384ALGID = new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha3_384, DERNull.INSTANCE);
    RSASSAPSSparams SHA3_384ALGPARAMS = new RSASSAPSSparams(SHA3_384ALGID,
            new AlgorithmIdentifier(PKCSObjectIdentifiers.id_mgf1, SHA3_384ALGID), new ASN1Integer(48), new ASN1Integer(1));
    
    AlgorithmIdentifier SHA3_512ALGID = new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha3_512, DERNull.INSTANCE);
    RSASSAPSSparams SHA3_512ALGPARAMS = new RSASSAPSSparams(SHA3_512ALGID,
            new AlgorithmIdentifier(PKCSObjectIdentifiers.id_mgf1, SHA3_512ALGID), new ASN1Integer(64), new ASN1Integer(1));
    
}


public enum AS2SignatureAlgorithm {
    
    SHA3_224WITHRSA(new AlgorithmIdentifier(NISTObjectIdentifiers.id_rsassa_pkcs1_v1_5_with_sha3_224), "SHA3-224", "RSA"),
    SHA3_256WITHRSA(new AlgorithmIdentifier(NISTObjectIdentifiers.id_rsassa_pkcs1_v1_5_with_sha3_256), "SHA3-256", "RSA"),
    SHA3_384withRSA(new AlgorithmIdentifier(NISTObjectIdentifiers.id_rsassa_pkcs1_v1_5_with_sha3_384), "SHA3-384", "RSA"),
    SHA3_512WITHRSA(new AlgorithmIdentifier(NISTObjectIdentifiers.id_rsassa_pkcs1_v1_5_with_sha3_512), "SHA3-512", "RSA"),
    MD5WITHRSA(new AlgorithmIdentifier(OIWObjectIdentifiers.md5WithRSA), "MD5", "RSA"),
    SHA1WITHRSA(new AlgorithmIdentifier(NISTObjectIdentifiers.id_rsassa_pkcs1_v1_5_with_sha3_224), "SHA1", "RSA"),
    MD2WITHRSA(new AlgorithmIdentifier(NISTObjectIdentifiers.id_rsassa_pkcs1_v1_5_with_sha3_224), "MD2", "RSA"),
    SHA224WITHRSA(new AlgorithmIdentifier(PKCSObjectIdentifiers.sha1WithRSAEncryption), "SHA224", "RSA"),
    SHA256WITHRSA(new AlgorithmIdentifier(PKCSObjectIdentifiers.sha256WithRSAEncryption), "SHA256", "RSA"),
    SHA384WITHRSA(new AlgorithmIdentifier(PKCSObjectIdentifiers.sha384WithRSAEncryption), "SHA384", "RSA"),
    SHA512WITHRSA(new AlgorithmIdentifier(PKCSObjectIdentifiers.sha512WithRSAEncryption), "SHA512", "RSA"),
    RIPEMD128WITHRSA(new AlgorithmIdentifier(TeleTrusTObjectIdentifiers.rsaSignatureWithripemd128), "RIPEMD128", "RSA"),
    RIPEMD160WITHRSA(new AlgorithmIdentifier(TeleTrusTObjectIdentifiers.rsaSignatureWithripemd160), "RIPEMD160", "RSA"),
    RIPEMD256WITHRSA(new AlgorithmIdentifier(TeleTrusTObjectIdentifiers.rsaSignatureWithripemd256), "RIPEMD256", "RSA"),
    
    SHA224WITHDSA(new AlgorithmIdentifier(NISTObjectIdentifiers.dsa_with_sha224), "SHA224", "DSA"),
    SHA256WITHDSA(new AlgorithmIdentifier(NISTObjectIdentifiers.dsa_with_sha256), "SHA256", "DSA"),
    SHA384WITHDSA(new AlgorithmIdentifier(NISTObjectIdentifiers.dsa_with_sha384), "SHA384", "DSA"),
    SHA512WITHDSA(new AlgorithmIdentifier(NISTObjectIdentifiers.dsa_with_sha512), "SHA512", "DSA"),
    SHA3_224WITHDSA(new AlgorithmIdentifier(NISTObjectIdentifiers.id_dsa_with_sha3_224), "SHA3-224", "DSA"),
    SHA3_256WITHDSA(new AlgorithmIdentifier(NISTObjectIdentifiers.id_dsa_with_sha3_256), "SHA3-256", "DSA"),
    SHA3_384WITHDSA(new AlgorithmIdentifier(NISTObjectIdentifiers.id_dsa_with_sha3_384), "SHA3-384", "DSA"),
    SHA3_512WITHDSA(new AlgorithmIdentifier(NISTObjectIdentifiers.id_dsa_with_sha3_512), "SHA3-512", "DSA"),
    SHA1WITHDSA(new AlgorithmIdentifier(OIWObjectIdentifiers.dsaWithSHA1), "SHA1", "DSA"),
    
    SHA3_224WITHECDSA(new AlgorithmIdentifier(NISTObjectIdentifiers.id_ecdsa_with_sha3_224), "SHA3-224", "ECDSA"),
    SHA3_256WITHECDSA(new AlgorithmIdentifier(NISTObjectIdentifiers.id_ecdsa_with_sha3_256), "SHA3-256", "ECDSA"),
    SHA3_384WITHECDSA(new AlgorithmIdentifier(NISTObjectIdentifiers.id_ecdsa_with_sha3_384), "SHA3-384", "ECDSA"),
    SHA3_512WITHECDSA(new AlgorithmIdentifier(NISTObjectIdentifiers.id_ecdsa_with_sha3_512), "SHA3-512", "ECDSA"),
    SHA1WITHECDSA(new AlgorithmIdentifier(X9ObjectIdentifiers.ecdsa_with_SHA1), "SHA1", "ECDSA"),
    SHA224WITHECDSA(new AlgorithmIdentifier(X9ObjectIdentifiers.ecdsa_with_SHA224), "SHA224", "ECDSA"),
    SHA256WITHECDSA(new AlgorithmIdentifier(X9ObjectIdentifiers.ecdsa_with_SHA256), "SHA256", "ECDSA"),
    SHA384WITHECDSA(new AlgorithmIdentifier(X9ObjectIdentifiers.ecdsa_with_SHA384), "SHA384", "ECDSA"),
    SHA512WITHECDSA(new AlgorithmIdentifier(X9ObjectIdentifiers.ecdsa_with_SHA512), "SHA512", "ECDSA"),
    
    SHA1WITHPLAIN_ECDSA(new AlgorithmIdentifier(BSIObjectIdentifiers.ecdsa_plain_SHA1), "SHA1", "ECDSA"),
    SHA224WITHPLAIN_ECDSA(new AlgorithmIdentifier(BSIObjectIdentifiers.ecdsa_plain_SHA224), "SHA224", "ECDSA"),
    SHA256WITHPLAIN_ECDSA(new AlgorithmIdentifier(BSIObjectIdentifiers.ecdsa_plain_SHA256), "SHA256", "ECDSA"),
    SHA384WITHPLAIN_ECDSA(new AlgorithmIdentifier(BSIObjectIdentifiers.ecdsa_plain_SHA384), "SHA384", "ECDSA"),
    SHA512WITHPLAIN_ECDSA(new AlgorithmIdentifier(BSIObjectIdentifiers.ecdsa_plain_SHA512), "SHA512", "ECDSA"),
    RIPEMD160WITHPLAIN_ECDSA(new AlgorithmIdentifier(BSIObjectIdentifiers.ecdsa_plain_RIPEMD160), "RIPEMD160", "ECDSA"),
    
    SHA1WITHRSAANDMGF1(new AlgorithmIdentifier(OIWObjectIdentifiers.idSHA1, AS2SignatureAlgorithmParams.SHA1ALGPARAMS), "SHA1", "RSA"),
    SHA224WITHRSAANDMGF1(new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha224, AS2SignatureAlgorithmParams.SHA224ALGPARAMS), "SHA224", "RSA"),
    SHA256WITHRSAANDMGF1(new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256, AS2SignatureAlgorithmParams.SHA256ALGPARAMS), "SHA256", "RSA"),
    SHA384WITHRSAANDMGF1(new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha384, AS2SignatureAlgorithmParams.SHA384ALGPARAMS), "SHA384", "RSA"),
    SHA512WITHRSAANDMGF1(new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha512, AS2SignatureAlgorithmParams.SHA512ALGPARAMS), "SHA512", "RSA"),
    SHA3_224WITHRSAANDMGF1(new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha3_224, AS2SignatureAlgorithmParams.SHA3_224ALGPARAMS), "SHA3_224", "RSA"),
    SHA3_256WITHRSAANDMGF1(new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha3_256, AS2SignatureAlgorithmParams.SHA3_256ALGPARAMS), "SHA3_256", "RSA"),
    SHA3_384WITHRSAANDMGF1(new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha3_384, AS2SignatureAlgorithmParams.SHA3_384ALGPARAMS), "SHA3_384", "RSA"),
    SHA3_512WITHRSAANDMGF1(new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha3_512, AS2SignatureAlgorithmParams.SHA3_512ALGPARAMS), "SHA3_512", "RSA");

    private final AlgorithmIdentifier signatureAlgorithm;
    private final String digestAlgorithmName;
    private final String encryptionAlgorithmName;

    AS2SignatureAlgorithm(AlgorithmIdentifier signatureAlgorithm,
                          String digestAlgorithmName,
                          String encryptionAlgorithmName) {
        this.signatureAlgorithm = signatureAlgorithm;
        this.digestAlgorithmName = digestAlgorithmName;
        this.encryptionAlgorithmName = encryptionAlgorithmName;
    }

    public AlgorithmIdentifier getSignatureAlgorithm() {
        return signatureAlgorithm;
    }

    public String getSignatureAlgorithmName() {
        return name().replace("_", "-");
    }

    public String getDigestAlgorithmName() {
        return digestAlgorithmName;
    }

    public String getEncryptionAlgorithmName() {
        return encryptionAlgorithmName;
    }
    
}
