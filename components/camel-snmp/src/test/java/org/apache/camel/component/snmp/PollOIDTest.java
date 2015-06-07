/**
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
package org.apache.camel.component.snmp;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PollOIDTest extends CamelTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(PollOIDTest.class);

    // a disabled test... before enabling you must fill in a working IP, Port
    // and maybe oids in the route below
    @Ignore
    @Test
    public void testOIDPolling() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.assertIsSatisfied();
        List<Exchange> oids = mock.getExchanges();
        if (LOG.isInfoEnabled()) {
            for (Exchange e : oids) {
                LOG.info("OID: " + e.getIn().getBody(String.class));
            }
        }
    }
    
    @Test
    public void testStartRoute() throws Exception {
        // do nothing here , just make sure the camel route can started.
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: e1
                from("snmp:192.168.178.23:161?protocol=udp&type=POLL&oids=1.3.6.1.2.1.1.5.0").transform(body().convertToString()).to("mock:result");
                // END SNIPPET: e1
            }
        };
    }
}
