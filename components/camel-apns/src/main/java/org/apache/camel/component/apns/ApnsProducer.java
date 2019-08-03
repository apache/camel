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
package org.apache.camel.component.apns;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import com.notnoop.apns.APNS;
import com.notnoop.apns.ApnsNotification;
import com.notnoop.exceptions.ApnsException;
import org.apache.camel.Exchange;
import org.apache.camel.component.apns.model.ApnsConstants;
import org.apache.camel.component.apns.model.MessageType;
import org.apache.camel.component.apns.util.StringUtils;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;

public class ApnsProducer extends DefaultProducer {

    private List<String> tokenList;

    public ApnsProducer(ApnsEndpoint endpoint) {
        super(endpoint);
        initiate(endpoint);
    }

    @Override
    public ApnsEndpoint getEndpoint() {
        return (ApnsEndpoint) super.getEndpoint();
    }

    private void initiate(ApnsEndpoint apnsEndpoint) {
        configureTokens(apnsEndpoint);
    }

    private void configureTokens(ApnsEndpoint apnsEndpoint) {
        if (ObjectHelper.isNotEmpty(apnsEndpoint.getTokens())) {
            this.tokenList = extractTokensFromString(apnsEndpoint.getTokens());
        }
    }

    public boolean isTokensConfiguredUsingUri() {
        return tokenList != null;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        notify(exchange);
    }

    private void notify(Exchange exchange) throws ApnsException {
        MessageType messageType = getHeaderMessageType(exchange, MessageType.STRING);

        if (messageType == MessageType.APNS_NOTIFICATION) {
            ApnsNotification apnsNotification = exchange.getIn().getBody(ApnsNotification.class);
            getEndpoint().getApnsService().push(apnsNotification);
        } else {
            constructNotificationAndNotify(exchange, messageType);
        }
    }

    private void constructNotificationAndNotify(Exchange exchange, MessageType messageType) {
        String payload;
        Collection<String> tokens;
        if (isTokensConfiguredUsingUri()) {
            if (hasTokensHeader(exchange)) {
                throw new IllegalArgumentException("Tokens already configured on endpoint " + ApnsConstants.HEADER_TOKENS);
            }
            tokens = new ArrayList<>(tokenList);
        } else {
            String tokensHeader = getHeaderTokens(exchange);
            tokens = extractTokensFromString(tokensHeader);
        }
        if (messageType == MessageType.STRING) {
            String message = exchange.getIn().getBody(String.class);
            payload = APNS.newPayload().alertBody(message).build();
        } else {
            String message = exchange.getIn().getBody(String.class);
            payload = message;
        }
        Date expiry = exchange.getIn().getHeader(ApnsConstants.HEADER_EXPIRY, Date.class);
        if (expiry != null) {
            getEndpoint().getApnsService().push(tokens, payload, expiry);
        } else {
            getEndpoint().getApnsService().push(tokens, payload);
        }
    }

    public String getHeaderTokens(Exchange exchange) {
        return exchange.getIn().getHeader(ApnsConstants.HEADER_TOKENS, String.class);
    }

    public MessageType getHeaderMessageType(Exchange exchange, MessageType defaultMessageType) {
        String messageTypeStr = (String)exchange.getIn().getHeader(ApnsConstants.HEADER_MESSAGE_TYPE);

        if (messageTypeStr == null) {
            return defaultMessageType;
        }

        MessageType messageType = MessageType.valueOf(messageTypeStr);
        return messageType;
    }

    private boolean hasTokensHeader(Exchange exchange) {
        return getHeaderTokens(exchange) != null;
    }

    private List<String> extractTokensFromString(String tokensStr) {
        tokensStr = StringUtils.trim(tokensStr);
        if (tokensStr.isEmpty()) {
            throw new IllegalArgumentException("No token specified");
        }

        String[] tokenArray = tokensStr.split(";");
        int tokenArrayLength = tokenArray.length;
        for (int i = 0; i < tokenArrayLength; i++) {
            String token = tokenArray[i];
            tokenArray[i] = token.trim();
            int tokenLength = token.length();
            if (tokenLength != 64) {
                throw new IllegalArgumentException("Token has wrong size['" + tokenLength + "']: " + token);
            }
        }
        List<String> tokens = Arrays.asList(tokenArray);
        return tokens;
    }

}
