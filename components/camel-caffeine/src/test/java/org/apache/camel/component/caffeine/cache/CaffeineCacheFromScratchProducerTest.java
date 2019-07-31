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
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class CaffeineCacheFromScratchProducerTest extends CamelTestSupport {

    @Test
    public void testCacheGet() throws Exception {
        final String key = "1";
        final String val = "1";

        fluentTemplate().withHeader(CaffeineConstants.ACTION, CaffeineConstants.ACTION_PUT).withHeader(CaffeineConstants.KEY, key).withBody(val).to("direct://start").send();

        MockEndpoint mock1 = getMockEndpoint("mock:result-get");
        mock1.expectedMinimumMessageCount(1);
        mock1.expectedBodiesReceived(val);
        mock1.expectedHeaderReceived(CaffeineConstants.ACTION_HAS_RESULT, true);
        mock1.expectedHeaderReceived(CaffeineConstants.ACTION_SUCCEEDED, true);

        fluentTemplate().withHeader(CaffeineConstants.ACTION, CaffeineConstants.ACTION_GET).withHeader(CaffeineConstants.KEY, key).withBody(val).to("direct://get").send();

        fluentTemplate().withHeader(CaffeineConstants.ACTION, CaffeineConstants.ACTION_PUT).withHeader(CaffeineConstants.KEY, key).withBody(val).to("direct://start-1").send();

        MockEndpoint mock2 = getMockEndpoint("mock:result-get-1");
        mock2.expectedMinimumMessageCount(1);
        mock2.expectedBodiesReceived(val);
        mock2.expectedHeaderReceived(CaffeineConstants.ACTION_HAS_RESULT, true);
        mock2.expectedHeaderReceived(CaffeineConstants.ACTION_SUCCEEDED, true);

        fluentTemplate().withHeader(CaffeineConstants.ACTION, CaffeineConstants.ACTION_GET).withHeader(CaffeineConstants.KEY, key).withBody(val).to("direct://get-1").send();

        assertMockEndpointsSatisfied();
    }

    // ****************************
    // Route
    // ****************************

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct://start").toF("caffeine-cache://%s?statsEnabled=true", "test").to("log:org.apache.camel.component.caffeine?level=INFO&showAll=true&multiline=true")
                    .to("mock:result");
                from("direct://get").toF("caffeine-cache://%s?statsEnabled=true", "test").to("log:org.apache.camel.component.caffeine?level=INFO&showAll=true&multiline=true")
                    .to("mock:result-get");
                from("direct://start-1").toF("caffeine-cache://%s?statsEnabled=true", "test").to("log:org.apache.camel.component.caffeine?level=INFO&showAll=true&multiline=true")
                    .to("mock:result");
                from("direct://get-1").toF("caffeine-cache://%s?statsEnabled=true", "test").to("log:org.apache.camel.component.caffeine?level=INFO&showAll=true&multiline=true")
                    .to("mock:result-get-1");
            }
        };
    }
}
