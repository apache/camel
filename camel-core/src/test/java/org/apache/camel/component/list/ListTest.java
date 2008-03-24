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
package org.apache.camel.component.list;

import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.BrowsableEndpoint;
import org.apache.camel.util.CamelContextHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @version $Revision$
 */
public class ListTest extends ContextTestSupport {
    private static final transient Log LOG = LogFactory.getLog(ListTest.class);

    protected Object body1 = "one";
    protected Object body2 = "two";

    public void testListEndpoints() throws Exception {
        template.sendBody("list:foo", body1);
        template.sendBody("list:foo", body2);

        List<BrowsableEndpoint> list = CamelContextHelper.getSingletonEndpoints(context, BrowsableEndpoint.class);
        assertEquals("number of endpoints", 2, list.size());

        Thread.sleep(2000);

        for (BrowsableEndpoint endpoint : list) {
            List<Exchange> exchanges = endpoint.getExchanges();

            LOG.debug(">>>> " + endpoint + " has: " + exchanges);

            assertEquals("Exchanges received on " + endpoint, 2, exchanges.size());

            assertInMessageBodyEquals(exchanges.get(0), body1);
            assertInMessageBodyEquals(exchanges.get(1), body2);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("list:foo").to("list:bar");
            }
        };
    }
}
