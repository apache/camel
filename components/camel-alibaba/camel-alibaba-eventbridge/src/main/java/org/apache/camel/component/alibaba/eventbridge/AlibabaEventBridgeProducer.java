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
package org.apache.camel.component.alibaba.eventbridge;

import java.util.List;

import com.aliyun.eventbridge.EventBridgeClient;
import com.aliyun.eventbridge.models.CloudEvent;
import com.aliyun.eventbridge.models.PutEventsResponse;
import org.apache.camel.Exchange;
import org.apache.camel.component.alibaba.eventbridge.constants.AlibabaEventBridgeHeaders;
import org.apache.camel.component.alibaba.eventbridge.constants.AlibabaEventBridgeOperations;
import org.apache.camel.component.alibaba.eventbridge.models.ClientConfigurations;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;

public class AlibabaEventBridgeProducer extends DefaultProducer {

    private EventBridgeClient eventBridgeClient;

    public AlibabaEventBridgeProducer(AlibabaEventBridgeEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        AlibabaEventBridgeEndpoint endpoint = getEndpoint();
        ClientConfigurations configuration = AlibabaEventBridgeUtils.createClientConfigurations(endpoint, exchange);

        if (ObjectHelper.isEmpty(configuration.getOperation())) {
            throw new IllegalArgumentException("Operation name not found");
        }

        if (eventBridgeClient == null) {
            eventBridgeClient = endpoint.initClient();
        }

        switch (configuration.getOperation()) {
            case AlibabaEventBridgeOperations.PUT_EVENTS -> putEvents(exchange, configuration);
            default -> throw new UnsupportedOperationException("Unsupported operation: " + configuration.getOperation());
        }
    }

    private void putEvents(Exchange exchange, ClientConfigurations configuration) {
        List<CloudEvent> events = AlibabaEventBridgeUtils.resolveCloudEvents(exchange, configuration);
        if (events.isEmpty()) {
            throw new IllegalArgumentException("At least one event is required for putEvents");
        }

        PutEventsResponse response = eventBridgeClient.putEvents(events);
        exchange.getMessage().setBody(AlibabaEventBridgeUtils.toPutEventsMap(response));
        if (ObjectHelper.isNotEmpty(response.getRequestId())) {
            exchange.getMessage().setHeader(AlibabaEventBridgeHeaders.REQUEST_ID, response.getRequestId());
        }
    }

    @Override
    public AlibabaEventBridgeEndpoint getEndpoint() {
        return (AlibabaEventBridgeEndpoint) super.getEndpoint();
    }
}
