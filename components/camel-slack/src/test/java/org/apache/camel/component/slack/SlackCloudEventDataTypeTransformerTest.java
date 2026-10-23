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
package org.apache.camel.component.slack;

import com.slack.api.model.Message;
import org.apache.camel.Exchange;
import org.apache.camel.cloudevents.CloudEvent;
import org.apache.camel.component.slack.transform.SlackCloudEventDataTypeTransformer;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.DataType;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class SlackCloudEventDataTypeTransformerTest {

    private final SlackCloudEventDataTypeTransformer transformer = new SlackCloudEventDataTypeTransformer();

    @Test
    public void testSourceAndSubjectFromTheMessage() {
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        Message message = new Message();
        message.setAppId("A123");
        message.setChannel("C456");
        exchange.getMessage().setBody(message);

        transformer.transform(exchange.getMessage(), DataType.ANY, new DataType("slack:application-cloudevents"));

        assertEquals("org.apache.camel.event.slack.consume",
                exchange.getMessage().getHeader(CloudEvent.CAMEL_CLOUD_EVENT_TYPE));
        assertEquals("slack.A123", exchange.getMessage().getHeader(CloudEvent.CAMEL_CLOUD_EVENT_SOURCE));
        assertEquals("C456", exchange.getMessage().getHeader(CloudEvent.CAMEL_CLOUD_EVENT_SUBJECT));
    }

    @Test
    public void testNoBody() {
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());

        assertDoesNotThrow(() -> transformer.transform(exchange.getMessage(), DataType.ANY,
                new DataType("slack:application-cloudevents")));

        assertEquals("org.apache.camel.event.slack.consume",
                exchange.getMessage().getHeader(CloudEvent.CAMEL_CLOUD_EVENT_TYPE));
        assertFalse(exchange.getMessage().getHeaders().containsKey(CloudEvent.CAMEL_CLOUD_EVENT_SOURCE));
        assertFalse(exchange.getMessage().getHeaders().containsKey(CloudEvent.CAMEL_CLOUD_EVENT_SUBJECT));
    }
}
