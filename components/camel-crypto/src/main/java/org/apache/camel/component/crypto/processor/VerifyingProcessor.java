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

import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.Certificate;

import org.apache.camel.Exchange;
import org.apache.camel.component.crypto.DigitalSignatureConfiguration;
import org.apache.camel.component.crypto.DigitalSignatureConstants;
import org.apache.camel.support.ExchangeHelper;
import org.apache.commons.codec.binary.Base64;

/**
 * <code>VerifyingProcessor</code>
 */
public class VerifyingProcessor extends DigitalSignatureProcessor {

    public VerifyingProcessor(DigitalSignatureConfiguration configuration) {
        super(configuration);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Signature signer = createSignatureService();
        // the key or certificate in the message header wins over the one configured on the endpoint
        Certificate cert
                = exchange.getIn().getHeader(DigitalSignatureConstants.SIGNATURE_PUBLIC_KEY_OR_CERT, Certificate.class);
        PublicKey pk = null;
        if (cert == null) {
            pk = exchange.getIn().getHeader(DigitalSignatureConstants.SIGNATURE_PUBLIC_KEY_OR_CERT, PublicKey.class);
        }
        if (cert == null && pk == null) {
            cert = config.getCertificate(getAlias(exchange));
            if (cert == null) {
                pk = config.getPublicKey();
            }
        }
        if (cert != null) {
            signer.initVerify(cert);
        } else if (pk != null) {
            signer.initVerify(pk);
        } else {
            throw new IllegalStateException(
                    String.format("Cannot verify signature as no Public Key or Certificate has been supplied."
                                  + " Either supply one in the route definition or via the message header '%s'",
                            DigitalSignatureConstants.SIGNATURE_PUBLIC_KEY_OR_CERT));
        }

        if (exchange.getIn().getBody() == null) {
            throw new SignatureException("Cannot verify signature of exchange as the message has no body");
        }
        calculateSignature(exchange, signer);

        byte[] signature = getSignatureFromExchange(exchange);
        if (!signer.verify(signature)) {
            throw new SignatureException("Cannot verify signature of exchange");
        }
        clearMessageHeaders(exchange.getIn());
    }

    private byte[] getSignatureFromExchange(Exchange exchange) throws Exception {
        String encodedSignature = ExchangeHelper.getMandatoryHeader(exchange, config.getSignatureHeaderName(), String.class);
        if (encodedSignature == null) {
            throw new IllegalStateException(
                    "Cannot verify exchange as no " + config.getSignatureHeaderName() + " header is present.");
        }
        return new Base64().decode(encodedSignature);
    }
}
