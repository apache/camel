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

import io.nessus.aries.util.AssertState;
import org.apache.camel.Exchange;
import org.apache.camel.component.aries.HyperledgerAriesEndpoint;
import org.apache.camel.component.aries.UnsupportedServiceException;
import org.hyperledger.aries.api.connection.ConnectionRecord;
import org.hyperledger.aries.api.connection.ConnectionState;
import org.hyperledger.aries.api.out_of_band.InvitationMessage;
import org.hyperledger.aries.api.out_of_band.InvitationMessage.InvitationMessageService;
import org.hyperledger.aries.api.out_of_band.ReceiveInvitationFilter;

public class OutOfBandServiceHandler extends AbstractServiceHandler {

    public OutOfBandServiceHandler(HyperledgerAriesEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange, String service) throws Exception {

        if (service.equals("/out-of-band/receive-invitation")) {
            @SuppressWarnings("unchecked")
            InvitationMessage<InvitationMessageService> reqObj = assertBody(exchange, InvitationMessage.class);
            ReceiveInvitationFilter filter = maybeHeader(exchange, ReceiveInvitationFilter.class);
            if (filter == null) {
                filter = ReceiveInvitationFilter.builder()
                        .useExistingConnection(false)
                        .autoAccept(false)
                        .build();
            }
            ConnectionRecord oobRecord = createClient().outOfBandReceiveInvitation(reqObj, filter).get();
            String connectionId = oobRecord.getConnectionId();
            AssertState.notNull(connectionId);
            ConnectionRecord resObj = adminClient().connections().get().stream()
                    .filter(cr -> cr.getState() == ConnectionState.INVITATION)
                    .filter(cr -> cr.getConnectionId().equals(connectionId))
                    .findFirst().orElse(null);
            AssertState.notNull(resObj, String.format("No ConnectionRecord for %s", connectionId));
            exchange.getMessage().setBody(resObj);

        } else {
            throw new UnsupportedServiceException(service);
        }
    }
}
