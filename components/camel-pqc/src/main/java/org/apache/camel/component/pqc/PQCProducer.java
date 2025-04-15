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

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Producer which sign or verify a payload
 */
public class PQCProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(PQCProducer.class);

    private Signature signer;

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

        signer = getEndpoint().getConfiguration().getSigner();

        if (ObjectHelper.isEmpty(signer)) {
            signer = Signature
                    .getInstance(PQCSignatureAlgorithms.valueOf(getConfiguration().getSignatureAlgorithm()).getAlgorithm());
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

}
