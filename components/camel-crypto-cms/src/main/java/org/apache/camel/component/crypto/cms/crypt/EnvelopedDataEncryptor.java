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
package org.apache.camel.component.crypto.cms.crypt;

import java.io.InputStream;
import java.io.OutputStream;
import java.security.cert.X509Certificate;

import org.apache.camel.Exchange;
import org.apache.camel.component.crypto.cms.common.AttributesGeneratorProvider;
import org.apache.camel.component.crypto.cms.common.CryptoCmsMarshallerAbstract;
import org.apache.camel.component.crypto.cms.common.OriginatorInformationProvider;
import org.apache.camel.component.crypto.cms.exception.CryptoCmsException;
import org.apache.camel.util.IOHelper;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.cms.CMSEnvelopedDataStreamGenerator;
import org.bouncycastle.cms.OriginatorInformation;
import org.bouncycastle.cms.jcajce.JceCMSContentEncryptorBuilder;
import org.bouncycastle.cms.jcajce.JceKeyTransRecipientInfoGenerator;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.OutputEncryptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Processor for creating an enveloped data object.
 */
public class EnvelopedDataEncryptor extends CryptoCmsMarshallerAbstract {
    private static final Logger LOG = LoggerFactory.getLogger(EnvelopedDataEncryptor.class);

    private final EnvelopedDataEncryptorConfiguration conf;

    public EnvelopedDataEncryptor(EnvelopedDataEncryptorConfiguration conf) {
        super(conf);
        this.conf = conf;
    }

    @Override
    protected void marshalInternal(InputStream is, OutputStream os, Exchange exchange) throws Exception {

        LOG.debug("Content encryption algorithm: {}", conf.getAlgorithmID());
        LOG.debug("Parameter secretKeyLength: {}", conf.getSecretKeyLength());

        OutputStream encryptingStream = null;
        try {
            CMSEnvelopedDataStreamGenerator gen = new CMSEnvelopedDataStreamGenerator();
            OriginatorInformationProvider originatorInformationProvider = conf.getOriginatorInformationProvider();
            if (originatorInformationProvider != null) {
                LOG.debug("originatorInformationProvider found");
                OriginatorInformation originatorInformation = originatorInformationProvider.getOriginatorInformation(exchange);
                if (originatorInformation != null) {
                    LOG.debug("originatorInformation found");
                    gen.setOriginatorInfo(originatorInformation);
                }
            }
            AttributesGeneratorProvider attributeGeneratorProvider = conf.getUnprotectedAttributesGeneratorProvider();
            if (attributeGeneratorProvider != null) {
                LOG.debug("attributeGeneratorProvider found");
                gen.setUnprotectedAttributeGenerator(attributeGeneratorProvider.getAttributesGenerator(exchange));
            }

            if (conf.getRecipient().isEmpty()) {
                throw new CryptoCmsException("No recipient configured.");
            }

            for (RecipientInfo recipientInfo : conf.getRecipient()) {
                // currently we only support key transport alternative, in
                // future there maybe others
                TransRecipientInfo keyTransrecipientInfo = (TransRecipientInfo)recipientInfo;
                LOG.debug("Recipient info: {}", keyTransrecipientInfo);
                X509Certificate encryptCert = keyTransrecipientInfo.getCertificate(exchange);
                LOG.debug("Encryption certificate for recipient with '{}' : {}", keyTransrecipientInfo, encryptCert);

                AlgorithmIdentifier keyEncryptionAlgorithm = determineKeyEncryptionAlgorithmIdentifier(keyTransrecipientInfo.getKeyEncryptionAlgorithm(exchange),
                                                                                                       keyTransrecipientInfo);
                JceKeyTransRecipientInfoGenerator keyTransRecipeintInfoGen = new JceKeyTransRecipientInfoGenerator(encryptCert, keyEncryptionAlgorithm);

                keyTransRecipeintInfoGen.setProvider(BouncyCastleProvider.PROVIDER_NAME);
                gen.addRecipientInfoGenerator(keyTransRecipeintInfoGen);
            }

            OutputEncryptor encryptor = new JceCMSContentEncryptorBuilder(conf.getAlgorithmID()).setProvider(BouncyCastleProvider.PROVIDER_NAME).build();

            encryptingStream = gen.open(os, encryptor);

            IOHelper.copy(is, encryptingStream);

            LOG.debug("CMS Enveloped Data creation successful");

        } finally {
            IOHelper.close(is);
            IOHelper.close(encryptingStream);
        }

    }

    private AlgorithmIdentifier determineKeyEncryptionAlgorithmIdentifier(String keyEncryptionAlgorithm, TransRecipientInfo keyTransRecipient) throws CryptoCmsException {

        if (keyEncryptionAlgorithm == null) {
            throw new CryptoCmsException("Key encryption algorithm  of recipient info '" + keyTransRecipient + "' is missing");
        }
        if ("RSA".equals(keyEncryptionAlgorithm)) {
            return new AlgorithmIdentifier(PKCSObjectIdentifiers.rsaEncryption);
        }
        throw new CryptoCmsException("Key encryption algorithm '" + keyEncryptionAlgorithm + "' of recipient info '" + keyTransRecipient + "' is not supported");
    }
}
