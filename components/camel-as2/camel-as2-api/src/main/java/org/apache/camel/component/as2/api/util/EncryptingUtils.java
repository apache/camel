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

import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;

import org.apache.camel.component.as2.api.AS2EncryptionAlgorithm;
import org.apache.http.HttpException;
import org.apache.http.util.Args;
import org.bouncycastle.cms.CMSEnvelopedDataGenerator;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.jcajce.JceCMSContentEncryptorBuilder;
import org.bouncycastle.cms.jcajce.JceKeyTransRecipientInfoGenerator;
import org.bouncycastle.operator.OutputEncryptor;

public final class EncryptingUtils {

    private EncryptingUtils() {
    }
    
    public static CMSEnvelopedDataGenerator createEnvelopDataGenerator(Certificate[] encryptionCertificateChain) throws HttpException {
        Args.notNull(encryptionCertificateChain, "encryptionCertificateChain");
        if (encryptionCertificateChain.length == 0 || !(encryptionCertificateChain[0] instanceof X509Certificate)) {
            throw new IllegalArgumentException("Invalid certificate chain");
        }
        
        try {
            X509Certificate encryptionCertificate = (X509Certificate) encryptionCertificateChain[0];
            
            CMSEnvelopedDataGenerator cmsEnvelopeDataGenerator = new CMSEnvelopedDataGenerator();
            
            JceKeyTransRecipientInfoGenerator recipientInfoGenerator = new JceKeyTransRecipientInfoGenerator(encryptionCertificate);
            cmsEnvelopeDataGenerator.addRecipientInfoGenerator(recipientInfoGenerator);

            return cmsEnvelopeDataGenerator;
        } catch (CertificateEncodingException e) {
            throw new HttpException("Failed to create envelope data generator", e);
        }
    }
    
    public static OutputEncryptor createEncryptor(AS2EncryptionAlgorithm encryptionAlgorithm) throws HttpException {
        Args.notNull(encryptionAlgorithm, "encryptionAlgorithmName");
        try {
            return new JceCMSContentEncryptorBuilder(encryptionAlgorithm.getAlgorithmOID()).build();
        } catch (CMSException e) {
            throw new HttpException("Failed to create encryptor ", e);
        }
    }
}
