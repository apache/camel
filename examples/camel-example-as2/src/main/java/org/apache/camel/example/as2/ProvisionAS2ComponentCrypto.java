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
package org.apache.camel.example.as2;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.as2.AS2Component;
import org.apache.camel.component.as2.AS2Configuration;
import org.apache.camel.component.as2.api.AS2CompressionAlgorithm;
import org.apache.camel.component.as2.api.AS2EncryptionAlgorithm;
import org.apache.camel.component.as2.api.AS2SignatureAlgorithm;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

public class ProvisionAS2ComponentCrypto implements CamelContextAware {
    
    private KeyPair issueKP;
    private X509Certificate issueCert;

    private KeyPair signingKP;
    private KeyPair decryptingKP;
    
    private X509Certificate clientCert;
    private List<X509Certificate> certList;
  

    @Override
    public void setCamelContext(CamelContext camelContext) {
        try {
            setupKeysAndCertificates();
        } catch (Exception e) {
            throw new RuntimeCamelException("failed to initialized Camel context for AS component", e);
        }
        AS2Component as2Component = camelContext.getComponent("as2", AS2Component.class);
        AS2Configuration configuration = new AS2Configuration();
        configuration.setSigningAlgorithm(AS2SignatureAlgorithm.SHA512WITHRSA);
        configuration.setSigningCertificateChain(certList.toArray(new Certificate[0]));
        configuration.setSigningPrivateKey(signingKP.getPrivate());
        configuration.setSignedReceiptMicAlgorithms(new String[] {"sha1", "md5"});
        configuration.setEncryptingAlgorithm(AS2EncryptionAlgorithm.AES128_CBC);
        configuration.setEncryptingCertificateChain(certList.toArray(new Certificate[0]));
        configuration.setDecryptingPrivateKey(decryptingKP.getPrivate());
        configuration.setCompressionAlgorithm(AS2CompressionAlgorithm.ZLIB);
        as2Component.setConfiguration(configuration);
    }

    @Override
    public CamelContext getCamelContext() {
        return null;
    }

    private void setupKeysAndCertificates() throws Exception {
        
        Security.addProvider(new BouncyCastleProvider());
        
        //
        // set up our certificates
        //
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", "BC");

        kpg.initialize(1024, new SecureRandom());

        String issueDN = "O=Punkhorn Software, C=US";
        issueKP = kpg.generateKeyPair();
        issueCert = Utils.makeCertificate(issueKP, issueDN, issueKP, issueDN);

        //
        // certificate we sign against
        //
        String signingDN = "CN=William J. Collins, E=punkhornsw@gmail.com, O=Punkhorn Software, C=US";
        signingKP = kpg.generateKeyPair();
        clientCert = Utils.makeCertificate(signingKP, signingDN, issueKP, issueDN);

        certList = new ArrayList<>();

        certList.add(clientCert);
        certList.add(issueCert);

        // keys used to encrypt/decrypt
        decryptingKP = signingKP;
    }
}
