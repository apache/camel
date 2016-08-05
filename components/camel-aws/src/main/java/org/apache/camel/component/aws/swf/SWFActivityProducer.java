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
package org.apache.camel.component.aws.swf;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SWFActivityProducer extends DefaultProducer {
    private static final transient Logger LOGGER = LoggerFactory.getLogger(SWFActivityProducer.class);
    private final CamelSWFActivityClient camelSWFClient;
    private SWFEndpoint endpoint;
    private SWFConfiguration configuration;
    
    private transient String swfActivityProducerToString;

    public SWFActivityProducer(SWFEndpoint endpoint, CamelSWFActivityClient camelSWFActivityClient) {
        super(endpoint);
        this.endpoint = endpoint;
        this.configuration = endpoint.getConfiguration();
        this.camelSWFClient = camelSWFActivityClient;
    }

    public void process(Exchange exchange) throws Exception {
        String eventName = getEventName(exchange);
        String version = getVersion(exchange);
        LOGGER.debug("scheduleActivity : " + eventName + " : " + version);

        Object result = camelSWFClient.scheduleActivity(eventName, version, exchange.getIn().getBody());
        endpoint.setResult(exchange, result);
    }

    private String getEventName(Exchange exchange) {
        String eventName = exchange.getIn().getHeader(SWFConstants.EVENT_NAME, String.class);
        return eventName != null ? eventName : configuration.getEventName();
    }

    private String getVersion(Exchange exchange) {
        String version = exchange.getIn().getHeader(SWFConstants.VERSION, String.class);
        return version != null ? version : configuration.getVersion();
    }

    @Override
    public String toString() {
        if (swfActivityProducerToString == null) {
            swfActivityProducerToString = "SWFActivityProducer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return swfActivityProducerToString;
    }
}
