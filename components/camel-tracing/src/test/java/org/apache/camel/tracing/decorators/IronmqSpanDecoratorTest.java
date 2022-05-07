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
package org.apache.camel.tracing.decorators;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class IronmqSpanDecoratorTest {

    @Test
    public void testGetMessageId() {
        String messageId = "abcd";
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(message.getHeader(IronmqSpanDecorator.CAMEL_IRON_MQ_MESSAGE_ID, String.class)).thenReturn(messageId);

        AbstractMessagingSpanDecorator decorator = new IronmqSpanDecorator();

        assertEquals(messageId, decorator.getMessageId(exchange));
    }

}
