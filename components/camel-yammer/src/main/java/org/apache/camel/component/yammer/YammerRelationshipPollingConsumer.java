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

import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.yammer.model.Relationships;
import org.apache.camel.impl.ScheduledPollConsumer;
import org.apache.camel.util.ObjectHelper;
import org.codehaus.jackson.map.ObjectMapper;

/**
 * A Yammer consumer that periodically polls relationships from Yammer's relationship API.
 */
public class YammerRelationshipPollingConsumer extends ScheduledPollConsumer {
    private final YammerEndpoint endpoint;
    private final String apiUrl;
    
    public YammerRelationshipPollingConsumer(YammerEndpoint endpoint, Processor processor) throws Exception {
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
        case RELATIONSHIPS:
            url.append(YammerConstants.YAMMER_BASE_API_URL);
            url.append(function);
            url.append(".json");
            break;
        default:
            throw new Exception(String.format("%s is not a valid Yammer relationship function type.", function));
        }        
        
        StringBuilder args = new StringBuilder();
        
        String userId = endpoint.getConfig().getUserId();
        if (ObjectHelper.isNotEmpty(userId)) {
            args.append("?user_id=");
            args.append(userId);
            url.append(args);
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
                Relationships relationships = jsonMapper.readValue(jsonBody, Relationships.class);
                
                exchange.getIn().setBody(relationships);
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
