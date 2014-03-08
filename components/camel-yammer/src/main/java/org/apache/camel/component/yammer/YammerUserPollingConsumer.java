/**
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
package org.apache.camel.component.yammer;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.yammer.model.User;
import org.apache.camel.impl.ScheduledPollConsumer;
import org.codehaus.jackson.map.ObjectMapper;

/**
 * A Yammer consumer that periodically polls messages from Yammer's user API.
 */
public class YammerUserPollingConsumer extends ScheduledPollConsumer {
    private final YammerEndpoint endpoint;
    private final String apiUrl;
    
    public YammerUserPollingConsumer(YammerEndpoint endpoint, Processor processor) throws Exception {
        super(endpoint, processor);
        this.endpoint = endpoint;

        long delay = endpoint.getConfig().getDelay();
        setDelay(delay);
        setTimeUnit(TimeUnit.MILLISECONDS);
        apiUrl = getApiUrl();
    }

    private String getApiUrl() throws Exception {    
        StringBuilder url = new StringBuilder();
        
        String function = endpoint.getConfig().getFunction();
        switch (YammerFunctionType.fromUri(function)) {
        case USERS:
            url.append(YammerConstants.YAMMER_BASE_API_URL);
            url.append(function);
            url.append(".json");
            break;
        case CURRENT:
            url.append(YammerConstants.YAMMER_BASE_API_URL);
            url.append("users/");
            url.append(function);
            url.append(".json");
            break;
        default:
            throw new Exception(String.format("%s is not a valid Yammer user function type.", function));
        }        
        
        return url.toString();
    }

    
    @Override
    protected int poll() throws Exception {
        Exchange exchange = endpoint.createExchange();

        try {
            String jsonBody = endpoint.getConfig().getRequestor(apiUrl).get();

            if (!endpoint.getConfig().isUseJson()) {
                ObjectMapper jsonMapper = new ObjectMapper();
                String function = endpoint.getConfig().getFunction();
                switch (YammerFunctionType.fromUri(function)) {
                case USERS:
                    List<User> users = jsonMapper.readValue(jsonBody, jsonMapper.getTypeFactory().constructCollectionType(List.class, User.class));
                    exchange.getIn().setBody(users);                   
                    break;
                case CURRENT:
                    User user = jsonMapper.readValue(jsonBody, jsonMapper.getTypeFactory().constructType(User.class));
                    exchange.getIn().setBody(user);                                       
                    break;
                default:
                    throw new Exception(String.format("%s is not a valid Yammer user function type.", function));
                }    
                
            } else {
                exchange.getIn().setBody(jsonBody);
            }

            // send message to next processor in the route
            getProcessor().process(exchange);

            return 1; // number of messages polled
        } finally {
            // log exception if an exception occurred and was not handled
            if (exchange.getException() != null) {
                getExceptionHandler().handleException("Error processing exchange", exchange, exchange.getException());
            }
        }
    }


}
