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
package org.apache.camel.component.crypto.cms.sig;

import java.io.InputStream;
import java.io.OutputStream;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.crypto.cms.common.CryptoCmsConstants;
import org.apache.camel.component.crypto.cms.common.CryptoCmsMarshallerAbstract;
import org.apache.camel.component.crypto.cms.exception.CryptoCmsException;
import org.apache.camel.component.crypto.cms.exception.CryptoCmsInvalidKeyException;
import org.apache.camel.util.IOHelper;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSSignedDataStreamGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SignedDataCreator extends CryptoCmsMarshallerAbstract {
    private static final Logger LOG = LoggerFactory.getLogger(SignedDataCreator.class);

    private SignedDataCreatorConfiguration config;

    public SignedDataCreator(SignedDataCreatorConfiguration conf) {
        super(conf);
        config = conf;
    }

    @Override
    protected void setBodyAndHeader(Message out, Object encodedSignedData) {
        if (config.getIncludeContent()) {
            /*
             * The encodedSignedData object contains the signer infos including
             * the message content.
             */
            out.setBody(encodedSignedData);
        } else {
            /*
             * The encodedSignedData object contains only the signer infos
             * (without the message content). As the message body is not changed
             * in this case and is passed through
             */
            out.setHeader(CryptoCmsConstants.CAMEL_CRYPTO_CMS_SIGNED_DATA, encodedSignedData);
        }
    }

    @Override
    protected void marshalInternal(InputStream is, OutputStream os, Exchange exchange) throws Exception {

        CMSSignedDataStreamGenerator gen = new CMSSignedDataStreamGenerator();

        if (config.getSigner().isEmpty()) {
            throw new CryptoCmsException("No signer information configured");
        }
        for (SignerInfo signer : config.getSigner()) {
            // these certificates are sent within the signature
            LOG.debug("Signer info: {}", signer);
            X509Certificate signerCert = signer.getCertificate(exchange);
            if (signerCert == null) {
                throw new CryptoCmsException("Certificate missing in the singer information " + signer);
            }

            PrivateKey key = signer.getPrivateKey(exchange);
            if (key == null) {
                throw new CryptoCmsException("Private key missing in the singer information " + signer);
            }

            ContentSigner contentSigner;
            try {
                contentSigner = new JcaContentSignerBuilder(signer.getSignatureAlgorithm(exchange)).setProvider(BouncyCastleProvider.PROVIDER_NAME).build(key);
            } catch (OperatorCreationException e) {
                throw new CryptoCmsInvalidKeyException("The private key of the signer information  '" + signer + "' does not fit to the specified signature algorithm '"
                                                       + signer.getSignatureAlgorithm(exchange) + "': " + e.getMessage(), e);
            }

            JcaSignerInfoGeneratorBuilder signerBuilder = new JcaSignerInfoGeneratorBuilder(new JcaDigestCalculatorProviderBuilder().setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build());
            signerBuilder.setSignedAttributeGenerator(signer.getSignedAttributeGenerator(exchange)).setUnsignedAttributeGenerator(signer.getUnsignedAttributeGenerator(exchange));

            gen.addSignerInfoGenerator(signerBuilder.build(contentSigner, signerCert));

            List<Certificate> certificateList = new ArrayList<Certificate>();
            for (Certificate cert : signer.getCertificateChain(exchange)) {
                if (!certificateList.contains(cert)) {
                    certificateList.add(cert);
                    gen.addCertificate(new X509CertificateHolder(cert.getEncoded()));
                    LOG.debug("Certificate added to Signed Data certificate list: {}", cert);
                }
            }
        }

        OutputStream sigOut = gen.open(os, config.getIncludeContent());
        try {
            IOHelper.copyAndCloseInput(is, sigOut);
        } finally {
            IOHelper.close(sigOut);
        }

        LOG.debug("CMS Signed Data generation successful");

    }

}
