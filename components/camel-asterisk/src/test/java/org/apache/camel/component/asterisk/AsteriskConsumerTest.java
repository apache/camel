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
package org.apache.camel.component.asterisk;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Disabled("CAMEL-10321: Set host, username and password test asterisk consumer.")
public class AsteriskConsumerTest extends CamelTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(AsteriskConsumerTest.class);

    private String hostname = "192.168.0.254";
    private String username = "username";
    private String password = "password";

    @Disabled
    @Test
    void testReceiveTraps() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.assertIsSatisfied();
        List<Exchange> events = mock.getExchanges();
        if (LOG.isInfoEnabled()) {
            for (Exchange e : events) {
                LOG.info("ASTERISK EVENTS: {}", e.getIn().getBody(String.class));
            }
        }
    }

    @Test
    void testStartRoute() {
        // do nothing here , just make sure the camel route can started.
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("asterisk:myVoIP?hostname=" + hostname + "&username=" + username + "&password=" + password).id("route1")
                        .transform(body().convertToString()).to("mock:result");
            }
        };
    }
}
