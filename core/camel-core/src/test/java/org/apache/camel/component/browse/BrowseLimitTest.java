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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collection;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.BrowsableEndpoint;
import org.junit.jupiter.api.Test;

public class BrowseLimitTest extends ContextTestSupport {

    protected final Object body1 = "one";
    protected final Object body2 = "two";
    protected final Object body3 = "three";
    protected final Object body4 = "four";
    protected final Object body5 = "five";

    @Test
    public void testLimit() throws Exception {
        template.sendBody("browse:foo?browseLimit=1", body1);
        template.sendBody("browse:foo?browseLimit=1", body2);
        template.sendBody("browse:foo?browseLimit=1", body3);
        template.sendBody("browse:foo?browseLimit=1", body4);
        template.sendBody("browse:foo?browseLimit=1", body5);

        Collection<Endpoint> list = context.getEndpoints();
        assertEquals(2, list.size(), "number of endpoints");

        BrowsableEndpoint be1 = context.getEndpoint("browse:foo?browseLimit=1", BrowsableEndpoint.class);
        assertEquals(1, be1.getExchanges().size());
        assertEquals("five", be1.getExchanges().get(0).getMessage().getBody());

        BrowsableEndpoint be2 = context.getEndpoint("browse:bar?browseLimit=5", BrowsableEndpoint.class);
        assertEquals(5, be2.getExchanges().size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("browse:foo?browseLimit=1").to("browse:bar?browseLimit=5");
            }
        };
    }
}
