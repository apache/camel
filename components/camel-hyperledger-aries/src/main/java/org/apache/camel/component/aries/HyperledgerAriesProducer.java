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
package org.apache.camel.component.aries;

import io.nessus.aries.util.AssertState;
import org.apache.camel.Exchange;
import org.apache.camel.component.aries.handler.AbstractServiceHandler;
import org.apache.camel.component.aries.handler.ConnectionsServiceHandler;
import org.apache.camel.component.aries.handler.CredentialDefinitionsServiceHandler;
import org.apache.camel.component.aries.handler.CredentialsServiceHandler;
import org.apache.camel.component.aries.handler.DidExchangeServiceHandler;
import org.apache.camel.component.aries.handler.IssueCredentialV1ServiceHandler;
import org.apache.camel.component.aries.handler.MultitenancyServiceHandler;
import org.apache.camel.component.aries.handler.OutOfBandServiceHandler;
import org.apache.camel.component.aries.handler.PresentProofServiceHandler;
import org.apache.camel.component.aries.handler.RevocationServiceHandler;
import org.apache.camel.component.aries.handler.SchemasServiceHandler;
import org.apache.camel.component.aries.handler.WalletServiceHandler;
import org.apache.camel.support.DefaultProducer;

import static org.apache.camel.component.aries.Constants.HEADER_SERVICE;

public class HyperledgerAriesProducer extends DefaultProducer {

    public HyperledgerAriesProducer(HyperledgerAriesEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public HyperledgerAriesEndpoint getEndpoint() {
        return (HyperledgerAriesEndpoint) super.getEndpoint();
    }

    @Override
    public void process(Exchange exchange) throws Exception {

        AbstractServiceHandler serviceHandler;

        String service = getService(exchange);
        if (service.startsWith("/connections")) {
            serviceHandler = new ConnectionsServiceHandler(getEndpoint());

        } else if (service.startsWith("/credential-definitions")) {
            serviceHandler = new CredentialDefinitionsServiceHandler(getEndpoint());

        } else if (service.startsWith("/credentials")) {
            serviceHandler = new CredentialsServiceHandler(getEndpoint());

        } else if (service.startsWith("/didexchange")) {
            serviceHandler = new DidExchangeServiceHandler(getEndpoint());

        } else if (service.startsWith("/issue-credential")) {
            serviceHandler = new IssueCredentialV1ServiceHandler(getEndpoint());

        } else if (service.startsWith("/multitenancy")) {
            serviceHandler = new MultitenancyServiceHandler(getEndpoint());

        } else if (service.startsWith("/out-of-band")) {
            serviceHandler = new OutOfBandServiceHandler(getEndpoint());

        } else if (service.startsWith("/present-proof")) {
            serviceHandler = new PresentProofServiceHandler(getEndpoint());

        } else if (service.startsWith("/revocation")) {
            serviceHandler = new RevocationServiceHandler(getEndpoint());

        } else if (service.startsWith("/schemas")) {
            serviceHandler = new SchemasServiceHandler(getEndpoint());

        } else if (service.startsWith("/wallet")) {
            serviceHandler = new WalletServiceHandler(getEndpoint());

        } else {
            throw new UnsupportedServiceException(service);
        }

        serviceHandler.beforeProcess(exchange, service);
        serviceHandler.process(exchange, service);
        serviceHandler.afterProcess(exchange, service);
    }

    protected String getWalletName() {
        String walletName = getEndpoint().getWalletName();
        return walletName;
    }

    private String getService(Exchange exchange) {
        String service = exchange.getIn().getHeader(HEADER_SERVICE, String.class);
        if (service == null) {
            service = getEndpoint().getConfiguration().getService();
        }
        AssertState.notNull(service, "Cannot obtain API service");
        if (!service.startsWith("/")) {
            service = "/" + service;
        }
        return service;
    }
}
