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
package org.apache.camel.component.caffeine.cache;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.caffeine.CaffeineConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class CaffeineCacheProducerMultiOperationSameCacheTest extends CaffeineCacheTestSupport {

    @Test
    public void testSameCachePutAndGet() throws Exception {
        final Map<String, String> map = new HashMap<>();
        map.put("1", "1");

        fluentTemplate().withBody("1").to("direct://start").send();

        MockEndpoint mock1 = getMockEndpoint("mock:result");
        mock1.expectedMinimumMessageCount(1);
        mock1.expectedHeaderReceived(CaffeineConstants.ACTION_HAS_RESULT, true);
        mock1.expectedHeaderReceived(CaffeineConstants.ACTION_SUCCEEDED, true);
        assertEquals("1", mock1.getExchanges().get(0).getIn().getBody());

        assertMockEndpointsSatisfied();
    }


    // ****************************
    // Route
    // ****************************

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct://start").toF("caffeine-cache://%s?cache=#cache&action=PUT&key=1", "test")
                    .toF("caffeine-cache://%s?cache=#cache&key=1&action=GET", "test").to("log:org.apache.camel.component.caffeine?level=INFO&showAll=true&multiline=true").log("Test! ${body}")
                    .to("mock:result");
            }
        };
    }
}
