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
package org.apache.camel.component.crypto.processor;

import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import static java.lang.String.format;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.crypto.DigitalSignatureConfiguration;
import org.apache.camel.component.crypto.DigitalSignatureConstants;
import org.apache.commons.codec.binary.Base64;


public class SigningProcessor extends DigitalSignatureProcessor {

    public SigningProcessor(DigitalSignatureConfiguration configuration) {
        super(configuration);
    }

    public void process(Exchange exchange) throws Exception {
        Signature service = initSignatureService(exchange);
        calculateSignature(exchange, service);
        byte[] signature = service.sign();

        Message in = exchange.getIn();
        clearMessageHeaders(in);
        Message out = exchange.getOut();
        out.copyFrom(in);
        out.setHeader(config.getSignatureHeaderName(), new Base64().encode(signature));
    }

    protected Signature initSignatureService(Exchange exchange) throws Exception {
        PrivateKey pk = getPrivateKeyFromKeystoreOrExchange(exchange);
        SecureRandom random = config.getSecureRandom();
        Signature service = createSignatureService();
        if (random != null) {
            service.initSign(pk, random);
        } else {
            service.initSign(pk);
        }
        return service;
    }

    private PrivateKey getPrivateKeyFromKeystoreOrExchange(Exchange exchange) throws Exception {
        PrivateKey pk = config.getPrivateKey(getAlias(exchange), getKeyPassword(exchange));

        if (pk == null) {
            pk = exchange.getIn().getHeader(DigitalSignatureConstants.SIGNATURE_PRIVATE_KEY, PrivateKey.class);
            if (pk == null) {
                throw new IllegalStateException(format("Cannot sign message as no Private Key has been supplied. "
                    + "Either supply one in the route definition sign(keystore, alias) or sign(privateKey) "
                    + "or via the message header '%s'", DigitalSignatureConstants.SIGNATURE_PRIVATE_KEY));
            }
        }
        return pk;
    }

    protected char[] getKeyPassword(Exchange exchange) throws Exception {
        KeyStore keystore = config.getKeystore();
        char[] password = null;
        if (keystore != null) {
            password = exchange.getIn().getHeader(DigitalSignatureConstants.KEYSTORE_PASSWORD, char[].class);
            if (password == null) {
                password = config.getPassword();
            }
        }
        return password;
    }
}
