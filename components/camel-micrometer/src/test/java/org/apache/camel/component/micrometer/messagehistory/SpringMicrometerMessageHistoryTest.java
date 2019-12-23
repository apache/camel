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
package org.apache.camel.component.micrometer.messagehistory;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.apache.camel.component.micrometer.MicrometerConstants.DEFAULT_CAMEL_MESSAGE_HISTORY_METER_NAME;
import static org.apache.camel.component.micrometer.MicrometerConstants.NODE_ID_TAG;

public class SpringMicrometerMessageHistoryTest extends CamelSpringTestSupport {

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/micrometer/messagehistory/SpringMetricsMessageHistoryTest.xml");
    }

    @Test
    public void testMetricsHistory() throws Exception {
        int count = 10;

        getMockEndpoint("mock:foo").expectedMessageCount(count / 2);
        getMockEndpoint("mock:bar").expectedMessageCount(count / 2);
        getMockEndpoint("mock:baz").expectedMessageCount(count / 2);

        for (int i = 0; i < count; i++) {
            if (i % 2 == 0) {
                template.sendBody("direct:foo", "Hello " + i);
            } else {
                template.sendBody("direct:bar", "Hello " + i);
            }
        }

        assertMockEndpointsSatisfied();

        // there should be 3 names
        MeterRegistry registry = context.getRegistry().findByType(MeterRegistry.class).iterator().next();
        assertEquals(3, registry.getMeters().size());

        Timer fooTimer = registry.find(DEFAULT_CAMEL_MESSAGE_HISTORY_METER_NAME).tag(NODE_ID_TAG, "foo").timer();
        assertEquals(count / 2, fooTimer.count());
        Timer barTimer = registry.find(DEFAULT_CAMEL_MESSAGE_HISTORY_METER_NAME).tag(NODE_ID_TAG, "bar").timer();
        assertEquals(count / 2, barTimer.count());
        Timer bazTimer = registry.find(DEFAULT_CAMEL_MESSAGE_HISTORY_METER_NAME).tag(NODE_ID_TAG, "baz").timer();
        assertEquals(count / 2, bazTimer.count());

        // get the message history service
        MicrometerMessageHistoryService service = context.hasService(MicrometerMessageHistoryService.class);
        assertNotNull(service);
        String json = service.dumpStatisticsAsJson();
        assertNotNull(json);
        log.info(json);

        assertTrue(json.contains("\"nodeId\" : \"foo\""));
        assertTrue(json.contains("\"nodeId\" : \"bar\""));
        assertTrue(json.contains("\"nodeId\" : \"baz\""));
    }

}
