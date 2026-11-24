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

import com.aliyun.eventbridge.EventBridgeClient;
import com.aliyun.eventbridge.models.PutEventsResponse;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlibabaEventBridgeProducerTest extends CamelTestSupport {

    @Test
    void processReinitializesClientFromEndpointOnEachExchange() throws Exception {
        AlibabaEventBridgeEndpoint endpoint = mock(AlibabaEventBridgeEndpoint.class);
        when(endpoint.getOperation()).thenReturn("putEvents");
        when(endpoint.getEventBusName()).thenReturn("demo-bus");
        when(endpoint.getEventSource()).thenReturn("demo-source");
        when(endpoint.getEventType()).thenReturn("demo-type");
        when(endpoint.getCamelContext()).thenReturn(context);

        EventBridgeClient firstClient = mock(EventBridgeClient.class);
        EventBridgeClient secondClient = mock(EventBridgeClient.class);
        when(endpoint.initClient()).thenReturn(firstClient, secondClient);

        PutEventsResponse response = new PutEventsResponse();
        when(firstClient.putEvents(anyList())).thenReturn(response);
        when(secondClient.putEvents(anyList())).thenReturn(response);

        AlibabaEventBridgeProducer producer = new AlibabaEventBridgeProducer(endpoint);
        var exchange1 = new DefaultExchange(context);
        exchange1.getMessage().setBody("{\"key\":\"value1\"}");
        producer.process(exchange1);

        var exchange2 = new DefaultExchange(context);
        exchange2.getMessage().setBody("{\"key\":\"value2\"}");
        producer.process(exchange2);

        verify(firstClient).putEvents(anyList());
        verify(secondClient).putEvents(anyList());
    }
}
