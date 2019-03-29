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
package org.apache.camel.component.pojo;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.ProxyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Assert;
import org.junit.Test;

public class PojoRouteTest extends Assert {

    @Test
    public void testPojoRoutes() throws Exception {
        CamelContext camelContext = new DefaultCamelContext();

        // START SNIPPET: route
        // lets add simple route
        camelContext.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:hello").transform().constant("Good Bye!");
            }
        });
        // END SNIPPET: route

        camelContext.start();

        // START SNIPPET: invoke
        ISay proxy = new ProxyBuilder(camelContext).endpoint("direct:hello").build(ISay.class);
        String rc = proxy.say();
        assertEquals("Good Bye!", rc);
        // END SNIPPET: invoke

        camelContext.stop();
    }
}
