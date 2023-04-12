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

import java.security.Key;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.http.entity.ContentType;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.cms.CMSAlgorithm;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.SignerInfoGenerator;
import org.bouncycastle.cms.SignerInformation;

public class AS2SignedDataGenerator extends CMSSignedDataGenerator {

    public static final Map<ASN1ObjectIdentifier, String> STANDARD_MICALGS;

    static {
        Map<ASN1ObjectIdentifier, String> stdMicAlgs = new HashMap<>();

        stdMicAlgs.put(CMSAlgorithm.MD5, "md5");
        stdMicAlgs.put(CMSAlgorithm.SHA1, "sha-1");
        stdMicAlgs.put(CMSAlgorithm.SHA224, "sha-224");
        stdMicAlgs.put(CMSAlgorithm.SHA256, "sha-256");
        stdMicAlgs.put(CMSAlgorithm.SHA384, "sha-384");
        stdMicAlgs.put(CMSAlgorithm.SHA512, "sha-512");
        stdMicAlgs.put(CMSAlgorithm.GOST3411, "gostr3411-94");
        stdMicAlgs.put(CMSAlgorithm.GOST3411_2012_256, "gostr3411-2012-256");
        stdMicAlgs.put(CMSAlgorithm.GOST3411_2012_512, "gostr3411-2012-512");

        STANDARD_MICALGS = Collections.unmodifiableMap(stdMicAlgs);
    }

    /**
     * Signing algorithms for DSA keys in order of preference
     */
    public static final String[] DSA_SIGNING_ALGORITHMS = {
            "SHA512WITHDSA",
            "SHA384WITHDSA",
            "SHA256WITHDSA",
            "SHA224WITHDSA",
            "SHA1WITHDSA",
    };

    /**
     * Signing algorithms for RSA keys in order of preference
     */
    public static final String[] RSA_SIGNING_ALGORITHMS = {
            "SHA512WITHRSA",
            "SHA384WITHRSA",
            "SHA256WITHRSA",
            "SHA224WITHRSA",
            "SHA1WITHRSA",
            "MD5WITHRSA",
            "MD2WITHRSA",
    };

    /**
     * Signing algorithms for EC keys in order of preference
     */
    public static final String[] EC_SIGNING_ALGORITHMS = {
            "SHA512WITHECDSA",
            "SHA384WITHECDSA",
            "SHA256WITHECDSA",
            "SHA224WITHECDSA",
            "SHA1WITHECDSA",
    };

    public AS2SignedDataGenerator() {
    }

    /**
     * Creates a <code>multipart/signed</code> content type containing the algorithms used by this generator.
     *
     * @param  boundary - boundary to use to demarcate content.
     * @return          A <code>multipart/signed</code> content type
     */
    public ContentType createMultipartSignedContentType(String boundary) {
        StringBuilder header = new StringBuilder(AS2MediaType.MULTIPART_SIGNED);
        header.append("; boundary=").append(boundary);
        Set<String> micAlgSet = new HashSet<>();

        // Collect algorithm names used by pre-calculated signers
        for (@SuppressWarnings("rawtypes")
        Iterator it = _signers.iterator(); it.hasNext();) {
            SignerInformation signer = (SignerInformation) it.next();
            ASN1ObjectIdentifier digestOID = signer.getDigestAlgorithmID().getAlgorithm();

            String micAlg = STANDARD_MICALGS.get(digestOID);

            if (micAlg == null) {
                micAlgSet.add("unknown");
            } else {
                micAlgSet.add(micAlg);
            }
        }

        // Collect algorithm names used by signer generators
        for (@SuppressWarnings("rawtypes")
        Iterator it = signerGens.iterator(); it.hasNext();) {
            SignerInfoGenerator signerInfoGen = (SignerInfoGenerator) it.next();
            ASN1ObjectIdentifier digestOID = signerInfoGen.getDigestAlgorithm().getAlgorithm();

            String micAlg = STANDARD_MICALGS.get(digestOID);

            if (micAlg == null) {
                micAlgSet.add("unknown");
            } else {
                micAlgSet.add(micAlg);
            }
        }

        // Add algorithm names to multipart signed header.
        int count = 0;
        for (String micAlg : micAlgSet) {
            if (count == 0) {
                if (micAlgSet.size() != 1) {
                    header.append("; micalg=\"");
                } else {
                    header.append("; micalg=");
                }
            } else {
                header.append(',');
            }

            header.append(micAlg);

            count++;
        }

        if (count != 0) {
            if (micAlgSet.size() != 1) {
                header.append('\"');
            }
        }

        return ContentType.parse(header.toString());
    }

    public static String[] getSupportedSignatureAlgorithmNamesForKey(Key key) {

        switch (key.getAlgorithm()) {
            case "DSA":
                return DSA_SIGNING_ALGORITHMS;
            case "RSA":
                return RSA_SIGNING_ALGORITHMS;
            case "EC":
                return EC_SIGNING_ALGORITHMS;
            default:
                return new String[0];
        }
    }

}
