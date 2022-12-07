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

import java.io.IOException;

import io.nessus.aries.util.AssertState;
import io.nessus.aries.websocket.WebSocketListener;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.aries.HyperledgerAriesComponent;
import org.apache.camel.component.aries.HyperledgerAriesConfiguration;
import org.apache.camel.component.aries.HyperledgerAriesEndpoint;
import org.hyperledger.aries.AriesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.aries.Constants.HEADER_WALLET_NAME;
import static org.apache.camel.component.aries.Constants.HEADER_WALLET_RECORD;
import static org.apache.camel.component.aries.Constants.PROPERTY_HYPERLEDGER_ARIES_COMPONENT;

public abstract class AbstractServiceHandler implements ServiceHandler {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final HyperledgerAriesEndpoint endpoint;

    public AbstractServiceHandler(HyperledgerAriesEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    public void beforeProcess(Exchange exchange, String service) {
        String walletName = endpoint.getWalletName();
        exchange.setProperty(PROPERTY_HYPERLEDGER_ARIES_COMPONENT, endpoint.getComponent());
        Message message = exchange.getIn();
        message.setHeader(HEADER_WALLET_NAME, walletName);
        message.setHeader(HEADER_WALLET_RECORD, endpoint.getWallet());
        log.debug("{}: Before [service={}, body={}, headers={}]", walletName, service, message.getBody(), message.getHeaders());
    }

    public void afterProcess(Exchange exchange, String service) {
        Message message = exchange.getIn();
        String walletName = endpoint.getWalletName();
        log.debug("{}: After [service={}, body={}, headers={}]", walletName, service, message.getBody(), message.getHeaders());
    }

    protected String getServicePathToken(String service, int idx) {
        return service.split("/")[idx + 1];
    }

    public <T> boolean hasBody(Exchange exchange, Class<T> type) {
        return maybeBody(exchange, type) != null;
    }

    public <T> T maybeBody(Exchange exchange, Class<T> type) {
        return exchange.getIn().getBody(type);
    }

    public <T> T assertBody(Exchange exchange, Class<T> type) {
        T body = exchange.getIn().getBody(type);
        AssertState.notNull(body, "Cannot obtain body of type: " + type.getName());
        return body;
    }

    public boolean hasHeader(Exchange exchange, String key) {
        return exchange.getIn().getHeader(key) != null;
    }

    public <T> T maybeHeader(Exchange exchange, Class<T> type) {
        T value = maybeHeader(exchange, type.getSimpleName(), type);
        if (value == null) {
            value = maybeHeader(exchange, type.getName(), type);
        }
        return value;
    }

    public <T> T maybeHeader(Exchange exchange, String key, Class<T> type) {
        return exchange.getIn().getHeader(key, type);
    }

    public <T> T assertHeader(Exchange exchange, Class<T> type) {
        T value = maybeHeader(exchange, type);
        AssertState.notNull(value, "Cannot obtain header of type: " + type.getName());
        return value;
    }

    public <T> T assertHeader(Exchange exchange, String key, Class<T> type) {
        T value = maybeHeader(exchange, key, type);
        AssertState.notNull(value, "Cannot obtain header '" + key + "' of type: " + type.getName());
        return value;
    }

    public HyperledgerAriesConfiguration getConfiguration() {
        return endpoint.getConfiguration();
    }

    public HyperledgerAriesComponent getComponent() {
        return endpoint.getComponent();
    }

    public AriesClient adminClient() {
        return getComponent().adminClient();
    }

    public AriesClient createClient() throws IOException {
        return endpoint.createClient();
    }

    public WebSocketListener getAdminWebSocketListener() {
        return getComponent().getAdminWebSocketListener();
    }
}
