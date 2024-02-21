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
package org.apache.camel.component.plc4x;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

public class Plc4XProducerTest {

    private Plc4XProducer sut;

    private Exchange testExchange;

    @BeforeEach
    public void setUp() throws Exception {
        Plc4XEndpoint endpointMock = mock(Plc4XEndpoint.class, RETURNS_DEEP_STUBS);
        when(endpointMock.getEndpointUri()).thenReturn("plc4x:mock:10.10.10.1/1/1");
        when(endpointMock.canWrite()).thenReturn(true);

        sut = new Plc4XProducer(endpointMock);
        testExchange = mock(Exchange.class, RETURNS_DEEP_STUBS);
        Map<String, Map<String, Object>> tags = new HashMap();
        tags.put("test1", Collections.singletonMap("testAddress1", 0));
        tags.put("test1", Collections.singletonMap("testAddress2", true));
        tags.put("test1", Collections.singletonMap("testAddress3", "TestString"));
        when(testExchange.getIn().getBody())
                .thenReturn(tags);
    }

    @Test
    public void process() throws Exception {
        when(testExchange.getPattern()).thenReturn(ExchangePattern.InOnly);
        sut.process(testExchange);
        when(testExchange.getPattern()).thenReturn(ExchangePattern.InOut);
        sut.process(testExchange);
        when(testExchange.getIn().getBody()).thenReturn(2);

    }

    @Test
    public void processAsync() {
        sut.process(testExchange, doneSync -> {
        });
        when(testExchange.getPattern()).thenReturn(ExchangePattern.InOnly);
        sut.process(testExchange, doneSync -> {
        });
        when(testExchange.getPattern()).thenReturn(ExchangePattern.InOut);
        sut.process(testExchange, doneSync -> {
        });
    }

    @Test
    public void doStop() throws Exception {
        sut.doStop();
    }

    @Test
    public void doStopOpenRequest() throws Exception {
        sut.openRequests.incrementAndGet();
        sut.doStop();
    }
}
