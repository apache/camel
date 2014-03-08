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

import java.net.URLEncoder;

import org.apache.camel.Exchange;
import org.apache.camel.component.yammer.model.Messages;
import org.apache.camel.impl.DefaultProducer;
import org.codehaus.jackson.map.ObjectMapper;

public class YammerMessageProducer extends DefaultProducer {

    private final YammerEndpoint endpoint;
    private final String apiUrl;
    
    public YammerMessageProducer(YammerEndpoint endpoint) throws Exception {
        super(endpoint);
        this.endpoint = endpoint;
        apiUrl = getApiUrl();
    }

    private String getApiUrl() throws Exception {    
        StringBuilder url = new StringBuilder();
        
        String function = endpoint.getConfig().getFunction();
        switch (YammerFunctionType.fromUri(function)) {
        case MESSAGES:
            url.append(YammerConstants.YAMMER_BASE_API_URL);
            url.append(function);
            url.append(".json");
            break;
        default:
            throw new Exception(String.format("%s is not a valid Yammer message producer function type.", function));
        }        
        
        return url.toString();
    }
    
    @Override
    public void process(Exchange exchange) throws Exception {
        String body = exchange.getIn().getBody(String.class);               
       
        String jsonBody = endpoint.getConfig().getRequestor(apiUrl).post("?body=" + URLEncoder.encode(body, "UTF-8"));   
        
        // we set the body to the message that was created on the server
        if (!endpoint.getConfig().isUseJson()) {
            ObjectMapper jsonMapper = new ObjectMapper();
            Messages messages = jsonMapper.readValue(jsonBody, Messages.class);
            exchange.getIn().setBody(messages);
        } else {
            exchange.getIn().setBody(jsonBody);
        }
    }

}
