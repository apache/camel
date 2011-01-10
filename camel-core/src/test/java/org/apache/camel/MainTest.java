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
package org.apache.camel;

import java.util.List;

import junit.framework.TestCase;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version $Revision: 909028 $
 */
public class MainTest extends TestCase {

    public void testMain() throws Exception {
        // lets make a simple route
        Main main = new Main();
        main.addRouteBuilder(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("mock:results");
            }
        });
        main.start();

        main.getCamelTemplate().sendBody("direct:start", "<message>1</message>");
        
        List<CamelContext> contextList = main.getCamelContexts();
        assertNotNull(contextList);
        assertEquals("size", 1, contextList.size());
        CamelContext camelContext = contextList.get(0);

        MockEndpoint endpoint = camelContext.getEndpoint("mock:results", MockEndpoint.class);
        endpoint.expectedMinimumMessageCount(1);
        endpoint.assertIsSatisfied();

        main.stop();
    }
}
