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
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.ScheduledPollConsumer;
import org.apache.camel.util.URISupport;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Hipchat consumer consumes messages from a list of users.
 */
public class HipchatConsumer extends ScheduledPollConsumer {
    public static final long DEFAULT_CONSUMER_DELAY = 5 * 1000;

    private static final Logger LOG = LoggerFactory.getLogger(HipchatConsumer.class);

    private static final MapType MAP_TYPE = TypeFactory.defaultInstance().constructMapType(Map.class, String.class, Object.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    private transient String hipchatConsumerToString;

    public HipchatConsumer(HipchatEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    protected int poll() throws Exception {        
        int messageCount = 0;
        for (String user : getConfig().consumableUsers()) {
            Exchange exchange = getEndpoint().createExchange();
            processExchangeForUser(user, exchange);
            messageCount++;
        }
        return messageCount;
    }

    private void processExchangeForUser(String user, Exchange exchange) throws Exception {
        String urlPath = String.format(getMostRecentMessageUrl(), user);
        LOG.debug("Polling HipChat Api " + urlPath + " for new messages at " + Calendar.getInstance(TimeZone.getTimeZone("UTC")).getTime());
        HttpGet httpGet = new HttpGet(getConfig().hipChatUrl() + urlPath);
        CloseableHttpResponse response = executeGet(httpGet);
        exchange.getIn().setHeader(HipchatConstants.FROM_USER, user);
        processApiResponse(exchange, response);
    }

    private void processApiResponse(Exchange exchange, CloseableHttpResponse response) throws Exception {
        try {
            Map<String, Object> jsonMap = MAPPER.readValue(response.getEntity().getContent(), MAP_TYPE);
            LOG.debug("Hipchat response " + response + ", json: " + MAPPER.writeValueAsString(jsonMap));
            if (jsonMap != null && jsonMap.size() > 0) {
                List<Map<String, Object>> items = (List<Map<String, Object>>) jsonMap.get(HipchatApiConstants.API_ITEMS);
                if (items != null && items.size() > 0) {
                    try {
                        Map<String, Object> item = items.get(0);
                        String date = (String) item.get(HipchatApiConstants.API_DATE);
                        String message = (String) item.get(HipchatApiConstants.API_MESSAGE);
                        LOG.debug("Setting exchange body: " + message + ", header " + HipchatConstants.MESSAGE_DATE + ": " + date);
                        exchange.getIn().setHeader(HipchatConstants.FROM_USER_RESPONSE_STATUS, response.getStatusLine());
                        exchange.getIn().setHeader(HipchatConstants.MESSAGE_DATE, date);
                        exchange.getIn().setBody(message);
                        getProcessor().process(exchange);
                    } catch (Exception e) {
                        throw new HipchatException("Error parsing Json response from Hipchat API", e);
                    }
                }
            }
        } finally {
            response.close();
        }
    }

    protected CloseableHttpResponse executeGet(HttpGet httpGet) throws IOException {
        return getConfig().getHttpClient().execute(httpGet);
    }

    private String getMostRecentMessageUrl() {
        return getConfig().withAuthToken(HipchatApiConstants.URI_PATH_USER_LATEST_PRIVATE_CHAT) + "&" + HipchatApiConstants.DEFAULT_MAX_RESULT;
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
        if (hipchatConsumerToString == null) {
            hipchatConsumerToString = "HipchatConsumer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return hipchatConsumerToString;
    }
}
