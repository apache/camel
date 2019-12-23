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
package org.apache.camel.component.browse;

import java.util.Collection;
import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BrowseTest extends ContextTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(BrowseTest.class);

    protected Object body1 = "one";
    protected Object body2 = "two";

    @Test
    public void testListEndpoints() throws Exception {
        template.sendBody("browse:foo", body1);
        template.sendBody("browse:foo", body2);

        Collection<Endpoint> list = context.getEndpoints();
        assertEquals("number of endpoints", 2, list.size());

        for (Endpoint endpoint : list) {
            List<Exchange> exchanges = ((BrowseEndpoint)endpoint).getExchanges();
            LOG.debug(">>>> {} has: {}", endpoint, exchanges);

            assertEquals("Exchanges received on " + endpoint, 2, exchanges.size());
            assertInMessageBodyEquals(exchanges.get(0), body1);
            assertInMessageBodyEquals(exchanges.get(1), body2);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("browse:foo").to("browse:bar");
            }
        };
    }
}
