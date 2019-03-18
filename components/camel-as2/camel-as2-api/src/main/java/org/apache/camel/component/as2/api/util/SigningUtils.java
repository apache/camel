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
package org.apache.camel.component.as2.api.util;

import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import org.apache.camel.component.as2.api.AS2SignatureAlgorithm;
import org.apache.camel.component.as2.api.AS2SignedDataGenerator;
import org.apache.http.HttpException;
import org.apache.http.util.Args;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.cms.IssuerAndSerialNumber;
import org.bouncycastle.asn1.smime.SMIMECapabilitiesAttribute;
import org.bouncycastle.asn1.smime.SMIMECapability;
import org.bouncycastle.asn1.smime.SMIMECapabilityVector;
import org.bouncycastle.asn1.smime.SMIMEEncryptionKeyPreferenceAttribute;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.SignerInfoGenerator;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoGeneratorBuilder;

public final class SigningUtils {

    private SigningUtils() {
    }

    public static AS2SignedDataGenerator createSigningGenerator(AS2SignatureAlgorithm signingAlgorithm, Certificate[] certificateChain, PrivateKey privateKey) throws HttpException {
        Args.notNull(certificateChain, "certificateChain");
        if (certificateChain.length == 0 || !(certificateChain[0] instanceof X509Certificate)) {
            throw new IllegalArgumentException("Invalid certificate chain");
        }
        Args.notNull(privateKey, "privateKey");
        
        AS2SignedDataGenerator gen = new AS2SignedDataGenerator();

        // Get first certificate in chain for signing
        X509Certificate signingCert = (X509Certificate) certificateChain[0];

        // Create capabilities vector
        SMIMECapabilityVector capabilities = new SMIMECapabilityVector();
        capabilities.addCapability(SMIMECapability.dES_EDE3_CBC);
        capabilities.addCapability(SMIMECapability.rC2_CBC, 128);
        capabilities.addCapability(SMIMECapability.dES_CBC);

        // Create signing attributes
        ASN1EncodableVector attributes = new ASN1EncodableVector();
        attributes.add(new SMIMEEncryptionKeyPreferenceAttribute(new IssuerAndSerialNumber(
                new X500Name(signingCert.getIssuerDN().getName()), signingCert.getSerialNumber())));
        attributes.add(new SMIMECapabilitiesAttribute(capabilities));

        SignerInfoGenerator signerInfoGenerator = null;
        try {
            signerInfoGenerator = new JcaSimpleSignerInfoGeneratorBuilder().setProvider("BC")
                    .setSignedAttributeGenerator(new AttributeTable(attributes))
                    .build(signingAlgorithm.getSignatureAlgorithmName(), privateKey, signingCert);
           
        } catch (Exception e) {
            throw new HttpException("Failed to create signer info", e);
        }
        gen.addSignerInfoGenerator(signerInfoGenerator);

        // Create and populate certificate store.
        try {
            JcaCertStore certs = new JcaCertStore(Arrays.asList(certificateChain));
            gen.addCertificates(certs);
        } catch (CertificateEncodingException | CMSException e) {
            throw new HttpException("Failed to add certificate chain to signature", e);
        }

        return gen;

    }
}
