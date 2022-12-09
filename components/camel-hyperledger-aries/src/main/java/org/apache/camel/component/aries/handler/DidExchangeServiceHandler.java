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
package org.apache.camel.component.aries.handler;

import java.util.concurrent.TimeUnit;

import io.nessus.aries.util.AssertState;
import io.nessus.aries.websocket.WebSocketListener;
import org.apache.camel.Exchange;
import org.apache.camel.component.aries.HyperledgerAriesEndpoint;
import org.apache.camel.component.aries.UnsupportedServiceException;
import org.hyperledger.aries.api.connection.ConnectionRecord;
import org.hyperledger.aries.api.connection.ConnectionState;
import org.hyperledger.aries.api.did_exchange.DidExchangeAcceptInvitationFilter;

public class DidExchangeServiceHandler extends AbstractServiceHandler {

    public DidExchangeServiceHandler(HyperledgerAriesEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange, String service) throws Exception {

        if (service.startsWith("/didexchange")) {

            String connectionId = getServicePathToken(service, 1);
            AssertState.notNull(connectionId, "Null connectionId");

            if (service.endsWith("/accept-invitation")) {
                DidExchangeAcceptInvitationFilter filter = maybeHeader(exchange, DidExchangeAcceptInvitationFilter.class);
                ConnectionRecord conrec = adminClient().didExchangeAcceptInvitation(connectionId, filter).get();
                WebSocketListener wsevents = getAdminWebSocketListener();
                if (conrec == null && wsevents != null) {
                    conrec = wsevents.awaitConnection(
                            cr -> cr.getState() == ConnectionState.COMPLETED
                                    && cr.getConnectionId().equals(connectionId),
                            10, TimeUnit.SECONDS)
                            .findFirst().orElse(null);
                }
                AssertState.notNull(conrec, String.format("No ConnectionRecord for %s", connectionId));
                exchange.getIn().setBody(conrec);

            } else if (service.endsWith(connectionId)) {
                WebSocketListener wsevents = getAdminWebSocketListener();
                ConnectionRecord conrec = createClient().connectionsGetById(connectionId).orElse(null);
                if (conrec == null && wsevents != null) {
                    conrec = wsevents.awaitConnection(
                            cr -> cr.getState() == ConnectionState.COMPLETED
                                    && cr.getConnectionId().equals(connectionId),
                            10, TimeUnit.SECONDS)
                            .findFirst().orElse(null);
                }
                AssertState.notNull(conrec, String.format("No ConnectionRecord for %s", connectionId));
                exchange.getIn().setBody(conrec);

            } else {
                throw new UnsupportedServiceException(service);
            }

        } else {
            throw new UnsupportedServiceException(service);
        }
    }
}
