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
package org.apache.camel.component.crypto.cms;

import java.net.MalformedURLException;
import java.net.URI;
import java.security.Security;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.component.crypto.cms.crypt.DefaultEnvelopedDataDecryptorConfiguration;
import org.apache.camel.component.crypto.cms.crypt.EnvelopedDataDecryptor;
import org.apache.camel.component.crypto.cms.crypt.EnvelopedDataDecryptorConfiguration;
import org.apache.camel.component.crypto.cms.crypt.EnvelopedDataEncryptor;
import org.apache.camel.component.crypto.cms.crypt.EnvelopedDataEncryptorConfiguration;
import org.apache.camel.component.crypto.cms.sig.DefaultSignedDataVerifierConfiguration;
import org.apache.camel.component.crypto.cms.sig.SignedDataCreator;
import org.apache.camel.component.crypto.cms.sig.SignedDataCreatorConfiguration;
import org.apache.camel.component.crypto.cms.sig.SignedDataVerifierConfiguration;
import org.apache.camel.component.crypto.cms.sig.SignedDataVerifierFromHeader;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.ObjectHelper;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CryptoCmsComponent extends UriEndpointComponent {

    private static final Logger LOG = LoggerFactory.getLogger(CryptoCmsComponent.class);

    @Metadata(label = "advanced")
    private SignedDataVerifierConfiguration signedDataVerifierConfiguration;

    @Metadata(label = "advanced")
    private EnvelopedDataDecryptorConfiguration envelopedDataDecryptorConfiguration;

    public CryptoCmsComponent() {
        super(CryptoCmsEndpoint.class);
    }

    public CryptoCmsComponent(CamelContext context) {
        super(context, CryptoCmsEndpoint.class);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception { // NOPMD
                                                                                                                       // called
                                                                                                                       // method
                                                                                                                       // setProperties
                                                                                                                       // throws
                                                                                                                       // Exception
        ObjectHelper.notNull(getCamelContext(), "CamelContext");

        String scheme;
        String name;
        try {
            URI u = new URI(remaining);
            scheme = u.getScheme();
            name = u.getPath();
        } catch (Exception e) {
            throw new MalformedURLException(// NOPMD -- the stack trace does not
                                            // help in this case.
                                            String.format("An invalid crypto-cms uri was provided '%s'."
                                                          + " Check that the uri matches the format crypto-cms:sign://<name>, crypto-cms:verify://<name>, "
                                                          + "crypto-cms:encrypt://<name>, or crypto-cms:decrpyt://<name>", uri));
        }
        Processor processor;
        CryptoOperation operation;
        if (CryptoOperation.sign.name().equals(scheme)) {
            operation = CryptoOperation.sign;
            SignedDataCreatorConfiguration config = new SignedDataCreatorConfiguration(getCamelContext());
            // properties must be set to config before processor is initialized
            setProperties(config, parameters);
            config.init();
            processor = new SignedDataCreator(config);
        } else if (CryptoOperation.verify.name().equals(scheme)) {
            operation = CryptoOperation.verify;
            SignedDataVerifierConfiguration config = getSignedDataVerifierConfiguration().copy();
            // properties must be set to config before processor is initialized
            setProperties(config, parameters);
            processor = new SignedDataVerifierFromHeader(config);
        } else if (CryptoOperation.encrypt.name().equals(scheme)) {
            operation = CryptoOperation.encrypt;
            EnvelopedDataEncryptorConfiguration config = new EnvelopedDataEncryptorConfiguration(getCamelContext());
            // properties must be set to config before processor is initialized
            setProperties(config, parameters);
            config.init();
            processor = new EnvelopedDataEncryptor(config);
        } else if (CryptoOperation.decrypt.name().equals(scheme)) {
            operation = CryptoOperation.decrypt;
            EnvelopedDataDecryptorConfiguration config = getEnvelopedDataDecryptorConfiguration().copy();
            // properties must be set to config before processor is initialized
            setProperties(config, parameters);
            processor = new EnvelopedDataDecryptor(config);
        } else {
            String error = "Endpoint uri " + uri + " is wrong configured. Operation " + scheme + " is not supported. Supported operations are: sign, verify, encrypt, decrypt";
            LOG.error(error);
            throw new IllegalStateException(error);
        }
        CryptoCmsEndpoint endpoint = new CryptoCmsEndpoint(uri, this, processor);
        endpoint.setCryptoOperation(operation);
        endpoint.setName(name);
        return endpoint;
    }

    /**
     * To configure the shared SignedDataVerifierConfiguration, which determines
     * the uri parameters for the verify operation.
     */
    public void setSignedDataVerifierConfiguration(SignedDataVerifierConfiguration signedDataVerifierConfiguration) {
        this.signedDataVerifierConfiguration = signedDataVerifierConfiguration;
    }

    public SignedDataVerifierConfiguration getSignedDataVerifierConfiguration() {
        if (signedDataVerifierConfiguration == null) {
            signedDataVerifierConfiguration = new DefaultSignedDataVerifierConfiguration();
        }
        return signedDataVerifierConfiguration;
    }

    public EnvelopedDataDecryptorConfiguration getEnvelopedDataDecryptorConfiguration() {
        if (envelopedDataDecryptorConfiguration == null) {
            envelopedDataDecryptorConfiguration = new DefaultEnvelopedDataDecryptorConfiguration();
        }
        return envelopedDataDecryptorConfiguration;
    }

    /**
     * To configure the shared EnvelopedDataDecryptorConfiguration, which
     * determines the uri parameters for the decrypt operation.
     */
    public void setEnvelopedDataDecryptorConfiguration(EnvelopedDataDecryptorConfiguration envelopedDataDecryptorConfiguration) {
        this.envelopedDataDecryptorConfiguration = envelopedDataDecryptorConfiguration;
    }

    @Override
    protected void doStart() throws Exception { // NOPMD
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            LOG.debug("Adding BouncyCastleProvider as security provider");
            Security.addProvider(new BouncyCastleProvider());
        }
        super.doStart();
    }

}
