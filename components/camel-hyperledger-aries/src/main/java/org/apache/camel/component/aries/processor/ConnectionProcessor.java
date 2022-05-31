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
package org.apache.camel.component.aries.processor;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.hyperledger.aries.AriesWebSocketClient;
import org.hyperledger.aries.api.connection.ConnectionRecord;
import org.hyperledger.aries.api.connection.ConnectionTheirRole;

import static org.hyperledger.aries.api.connection.ConnectionTheirRole.INVITEE;
import static org.hyperledger.aries.api.connection.ConnectionTheirRole.INVITER;

/**
 * Test RFC 0160: Connection Protocol with multitenant wallets
 * 
 * https://github.com/hyperledger/aries-rfcs/tree/main/features/0160-connection-protocol
 */
public class ConnectionProcessor extends AbstractAriesProcessor {

    private final String inviterName;
    private final String inviteeName;

    public ConnectionProcessor(String inviterName, String inviteeName) {
        this.inviterName = inviterName;
        this.inviteeName = inviteeName;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        AriesWebSocketClient inviterEvents = getAriesComponent(exchange).getWebSocketClient(inviterName);
        AriesWebSocketClient inviteeEvents = getAriesComponent(exchange).getWebSocketClient(inviteeName);
        exchange.getIn().setHeader(ConnectionProcessor.class.getSimpleName(), this);
        exchange.getIn().setHeader(getHeaderKey(INVITER), inviterEvents);
        exchange.getIn().setHeader(getHeaderKey(INVITEE), inviteeEvents);
    }

    public static String getHeaderKey(ConnectionTheirRole role) {
        return role + AriesWebSocketClient.class.getSimpleName();
    }

    public static AriesWebSocketClient getWebSocketClient(Exchange exchange, ConnectionTheirRole role) {
        return exchange.getIn().getHeader(getHeaderKey(role), AriesWebSocketClient.class);
    }

    public static boolean awaitConnected(Exchange exchange, long timeout, TimeUnit unit) {
        ConnectionRecord inviterRecord = getWebSocketClient(exchange, INVITER).connection()
                .filter(ConnectionRecord::stateIsActive)
                .blockFirst(Duration.ofMillis(unit.toMillis(timeout)));
        ConnectionRecord inviteeRecord = getWebSocketClient(exchange, INVITEE).connection()
                .filter(ConnectionRecord::stateIsActive)
                .blockFirst(Duration.ofMillis(unit.toMillis(timeout)));
        ConnectionProcessor proc = getHeader(exchange, ConnectionProcessor.class);
        ConnectionResult connectionResult = new ConnectionResult(inviterRecord, inviteeRecord);
        exchange.getIn().setHeader(proc.inviterName + proc.inviteeName + "ConnectionRecord",
                connectionResult.inviterConnection);
        exchange.getIn().setHeader(proc.inviteeName + proc.inviterName + "ConnectionRecord",
                connectionResult.inviteeConnection);
        exchange.getIn().setBody(connectionResult);
        return true;
    }

    public static class ConnectionResult {
        public final ConnectionRecord inviterConnection;
        public final ConnectionRecord inviteeConnection;

        public ConnectionResult(ConnectionRecord inviterConnection, ConnectionRecord inviteeConnection) {
            this.inviterConnection = inviterConnection;
            this.inviteeConnection = inviteeConnection;
        }
    }
}
