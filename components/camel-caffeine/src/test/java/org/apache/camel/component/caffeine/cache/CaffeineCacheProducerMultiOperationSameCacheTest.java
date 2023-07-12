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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.caffeine.CaffeineConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CaffeineCacheProducerMultiOperationSameCacheTest extends CaffeineCacheTestSupport {

    @Test
    void testSameCachePutAndGet() throws Exception {
        fluentTemplate().withBody("1").to("direct://start").send();

        MockEndpoint mock1 = getMockEndpoint("mock:result");
        mock1.expectedMinimumMessageCount(1);
        mock1.expectedHeaderReceived(CaffeineConstants.ACTION_HAS_RESULT, true);
        mock1.expectedHeaderReceived(CaffeineConstants.ACTION_SUCCEEDED, true);
        assertEquals("1", mock1.getExchanges().get(0).getIn().getBody());

        MockEndpoint.assertIsSatisfied(context);
    }

    // ****************************
    // Route
    // ****************************

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct://start")
                        .to("caffeine-cache://cache?action=PUT&key=1")
                        .to("caffeine-cache://cache?key=1&action=GET")
                        .to("log:org.apache.camel.component.caffeine?level=INFO&showAll=true&multiline=true")
                        .log("Test! ${body}")
                        .to("mock:result");
            }
        };
    }
}
