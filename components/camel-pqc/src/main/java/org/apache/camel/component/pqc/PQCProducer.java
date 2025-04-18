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
package org.apache.camel.component.pqc;

import java.security.*;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.bouncycastle.jcajce.SecretKeyWithEncapsulation;
import org.bouncycastle.jcajce.spec.KEMExtractSpec;
import org.bouncycastle.jcajce.spec.KEMGenerateSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Producer which sign or verify a payload
 */
public class PQCProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(PQCProducer.class);

    private Signature signer;
    private KeyGenerator keyGenerator;

    public PQCProducer(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        switch (determineOperation(exchange)) {
            case sign:
                signature(exchange);
                break;
            case verify:
                verification(exchange);
                break;
            case generateSecretKeyEncapsulation:
                generateEncapsulation(exchange);
                break;
            case extractSecretKeyEncapsulation:
                extractEncapsulation(exchange);
                break;
            case extractSecretKeyFromEncapsulation:
                extractSecretKeyFromEncapsulation(exchange);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation");
        }
    }

    private PQCOperations determineOperation(Exchange exchange) {
        PQCOperations operation = exchange.getIn().getHeader(PQCConstants.OPERATION, PQCOperations.class);
        if (operation == null) {
            operation = getConfiguration().getOperation();
        }
        return operation;
    }

    protected PQCConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public PQCEndpoint getEndpoint() {
        return (PQCEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (getConfiguration().getOperation().equals(PQCOperations.sign)
                || getConfiguration().getOperation().equals(PQCOperations.verify)) {
            signer = getEndpoint().getConfiguration().getSigner();

            if (ObjectHelper.isEmpty(signer)) {
                signer = Signature
                        .getInstance(PQCSignatureAlgorithms.valueOf(getConfiguration().getSignatureAlgorithm()).getAlgorithm());
            }
        }

        if (getConfiguration().getOperation().equals(PQCOperations.generateSecretKeyEncapsulation)
                || getConfiguration().getOperation().equals(PQCOperations.extractSecretKeyEncapsulation)) {
            keyGenerator = getEndpoint().getConfiguration().getKeyGenerator();

            if (ObjectHelper.isEmpty(keyGenerator)) {
                keyGenerator = KeyGenerator
                        .getInstance(PQCKeyEncapsulationAlgorithms.valueOf(getConfiguration().getKeyEncapsulationAlgorithm())
                                .getAlgorithm());
            }
        }
    }

    private void signature(Exchange exchange)
            throws InvalidPayloadException, InvalidKeyException, SignatureException {
        String payload = exchange.getMessage().getMandatoryBody(String.class);

        signer.initSign(getEndpoint().getConfiguration().getKeyPair().getPrivate());
        signer.update(payload.getBytes());

        byte[] signature = signer.sign();
        exchange.getMessage().setHeader(PQCConstants.SIGNATURE, signature);
    }

    private void verification(Exchange exchange)
            throws InvalidPayloadException, InvalidKeyException, SignatureException {
        String payload = exchange.getMessage().getMandatoryBody(String.class);

        signer.initVerify(getEndpoint().getConfiguration().getKeyPair().getPublic());
        signer.update(payload.getBytes());
        if (signer.verify(exchange.getMessage().getHeader(PQCConstants.SIGNATURE, byte[].class))) {
            exchange.getMessage().setHeader(PQCConstants.VERIFY, true);
        } else {
            exchange.getMessage().setHeader(PQCConstants.VERIFY, false);
        }
    }

    private void generateEncapsulation(Exchange exchange)
            throws InvalidAlgorithmParameterException {
        // initialise for creating an encapsulation and shared secret.
        keyGenerator.init(
                new KEMGenerateSpec(
                        getEndpoint().getConfiguration().getKeyPair().getPublic(),
                        getEndpoint().getConfiguration().getSymmetricKeyAlgorithm(), 128),
                new SecureRandom());
        // SecretKeyWithEncapsulation is the class to use as the secret key, it has additional
        // methods on it for recovering the encapsulation as well.
        SecretKeyWithEncapsulation secEnc1 = (SecretKeyWithEncapsulation) keyGenerator.generateKey();

        exchange.getMessage().setBody(secEnc1, SecretKeyWithEncapsulation.class);
    }

    private void extractEncapsulation(Exchange exchange)
            throws InvalidAlgorithmParameterException,
            InvalidPayloadException {
        // initialise for creating an encapsulation and shared secret.
        SecretKeyWithEncapsulation payload = exchange.getMessage().getMandatoryBody(SecretKeyWithEncapsulation.class);

        if (ObjectHelper.isEmpty(getConfiguration().getSymmetricKeyAlgorithm())) {
            throw new IllegalArgumentException("Symmetric Algorithm needs to be specified");
        }

        keyGenerator.init(
                new KEMExtractSpec(
                        getEndpoint().getConfiguration().getKeyPair().getPrivate(), payload.getEncapsulation(),
                        PQCSymmetricAlgorithms.valueOf(getConfiguration().getSymmetricKeyAlgorithm()).getAlgorithm(), 128),
                new SecureRandom());

        // initialise for extracting the shared secret from the encapsulation.
        SecretKeyWithEncapsulation secEnc2 = (SecretKeyWithEncapsulation) keyGenerator.generateKey();

        exchange.getMessage().setBody(secEnc2, SecretKeyWithEncapsulation.class);
    }

    private void extractSecretKeyFromEncapsulation(Exchange exchange)
            throws InvalidPayloadException {
        // initialise for creating an encapsulation and shared secret.
        SecretKeyWithEncapsulation payload = exchange.getMessage().getMandatoryBody(SecretKeyWithEncapsulation.class);

        if (ObjectHelper.isEmpty(getConfiguration().getSymmetricKeyAlgorithm())) {
            throw new IllegalArgumentException("Symmetric Algorithm needs to be specified");
        }

        SecretKey restoredKey = new SecretKeySpec(payload.getEncoded(), getConfiguration().getSymmetricKeyAlgorithm());

        exchange.getMessage().setBody(restoredKey, SecretKey.class);
    }

}
