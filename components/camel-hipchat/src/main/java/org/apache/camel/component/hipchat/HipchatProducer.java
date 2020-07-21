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
package org.apache.camel.component.hipchat;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.URISupport;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.util.UnsafeUriCharactersEncoder.encodeHttpURI;

/**
 * The Hipchat producer to send message to a user and/or a room.
 */
public class HipchatProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(HipchatProducer.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    private transient String hipchatProducerToString;

    public HipchatProducer(HipchatEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Message message = getMessageForResponse(exchange);
        String user = exchange.getIn().getHeader(HipchatConstants.TO_USER, String.class);
        if (user != null) {
            message.setHeader(HipchatConstants.TO_USER_RESPONSE_STATUS, sendUserMessage(user, exchange));
        }
        String room = exchange.getIn().getHeader(HipchatConstants.TO_ROOM, String.class);
        if (room != null) {
            message.setHeader(HipchatConstants.TO_ROOM_RESPONSE_STATUS, sendRoomMessage(room, exchange));
        }
    }

    private StatusLine sendRoomMessage(String room, Exchange exchange) throws IOException, InvalidPayloadException {
        String urlPath = String.format(getConfig().withAuthToken(HipchatApiConstants.URI_PATH_ROOM_NOTIFY), room);
        String backGroundColor = exchange.getIn().getHeader(HipchatConstants.MESSAGE_BACKGROUND_COLOR, String.class);
        Map<String, String> jsonParam = getCommonHttpPostParam(exchange);
        if (backGroundColor != null) {
            jsonParam.put(HipchatApiConstants.API_MESSAGE_COLOR, backGroundColor);
        }
        LOG.info("Sending message to room: " + room + ", " + MAPPER.writeValueAsString(jsonParam));
        StatusLine statusLine = post(encodeHttpURI(urlPath), jsonParam);
        LOG.debug("Response status for send room message: {}", statusLine);
        return statusLine;
    }

    private StatusLine sendUserMessage(String user, Exchange exchange) throws IOException, InvalidPayloadException {
        String urlPath = String.format(getConfig().withAuthToken(HipchatApiConstants.URI_PATH_USER_MESSAGE), user);
        Map<String, String> jsonParam = getCommonHttpPostParam(exchange);
        LOG.info("Sending message to user: " + user + ", " + MAPPER.writeValueAsString(jsonParam));
        StatusLine statusLine = post(urlPath, jsonParam);
        LOG.debug("Response status for send user message: {}", statusLine);
        return statusLine;
    }

    private Map<String, String> getCommonHttpPostParam(Exchange exchange) throws InvalidPayloadException {
        String format = exchange.getIn().getHeader(HipchatConstants.MESSAGE_FORMAT, "text", String.class);
        String notify = exchange.getIn().getHeader(HipchatConstants.TRIGGER_NOTIFY, String.class);
        Map<String, String> jsonMap = new HashMap<>(4);
        jsonMap.put(HipchatApiConstants.API_MESSAGE, exchange.getIn().getMandatoryBody(String.class));
        if (notify != null) {
            jsonMap.put(HipchatApiConstants.API_MESSAGE_NOTIFY, notify);
        }
        jsonMap.put(HipchatApiConstants.API_MESSAGE_FORMAT, format);
        return jsonMap;
    }

    protected StatusLine post(String urlPath, Map<String, String> postParam) throws IOException {
        HttpPost httpPost = new HttpPost(getConfig().hipChatUrl() + urlPath);
        httpPost.setEntity(new StringEntity(MAPPER.writeValueAsString(postParam), ContentType.APPLICATION_JSON));
        CloseableHttpResponse closeableHttpResponse = getConfig().getHttpClient().execute(httpPost);
        try {
            return closeableHttpResponse.getStatusLine();
        } finally {
            closeableHttpResponse.close();
        }
    }

    private Message getMessageForResponse(final Exchange exchange) {
        if (exchange.getPattern().isOutCapable()) {
            Message out = exchange.getOut();
            out.copyFrom(exchange.getIn());
            return out;
        }
        return exchange.getIn();
    }

    private HipchatConfiguration getConfig() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public HipchatEndpoint getEndpoint() {
        return (HipchatEndpoint)super.getEndpoint();
    }

    @Override
    public String toString() {
        if (hipchatProducerToString == null) {
            hipchatProducerToString = "HipchatProducer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return hipchatProducerToString;
    }
}
