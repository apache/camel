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
package org.apache.camel.component.crypto.processor;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Signature;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.crypto.DigitalSignatureConfiguration;
import org.apache.camel.component.crypto.DigitalSignatureConstants;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.util.IOHelper;

public abstract class DigitalSignatureProcessor implements Processor {

    protected DigitalSignatureConfiguration config;

    protected DigitalSignatureProcessor(DigitalSignatureConfiguration configuration) {
        this.config = configuration;
    }

    public void calculateSignature(Exchange exchange, Signature signer) throws Exception {
        Object payload = exchange.getIn().getBody();
        if (payload != null) {
            InputStream payloadStream = ExchangeHelper.convertToMandatoryType(exchange, InputStream.class, payload);
            try {
                byte[] buffer = new byte[config.getBufferSize()];
                int read;
                while ((read = payloadStream.read(buffer)) > 0) {
                    signer.update(buffer, 0, read);
                }
            } finally {
                IOHelper.close(payloadStream);
            }
        }
    }

    protected String getAlias(Exchange exchange) throws Exception {
        KeyStore keystore = config.getKeystore();
        if (keystore != null) {
            String alias = exchange.getIn().getHeader(DigitalSignatureConstants.KEYSTORE_ALIAS, String.class);
            if (alias == null) {
                alias = config.getAlias();
            }

            // if there is only one entry then use it. Document this well
            if (alias == null && keystore.size() == 1) {
                alias = keystore.aliases().nextElement();
            }
            return alias;
        }
        return null;
    }

    public void setConfig(DigitalSignatureConfiguration config) {
        this.config = config;
    }

    protected Signature createSignatureService() throws NoSuchAlgorithmException, NoSuchProviderException {
        String algorithm = config.getAlgorithm();
        String provider = config.getProvider();
        return provider == null ? Signature.getInstance(algorithm) : Signature.getInstance(algorithm, provider);
    }

    protected void clearMessageHeaders(Message in) {
        if (config.isClearHeaders()) {
            in.removeHeaders("^Camel(Digital)?Signature.*");
        }
    }
}
