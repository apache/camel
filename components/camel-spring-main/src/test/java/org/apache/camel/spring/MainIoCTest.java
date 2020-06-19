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
package org.apache.camel.spring;

import org.apache.camel.CamelContext;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

public class MainIoCTest {

    @Test
    public void testMain() throws Exception {
        // lets make a simple route
        Main main = new Main();
        // add as class so we get IoC from its packages
        main.configure().addRoutesBuilder(MyMainIoCRouteBuilder.class);
        main.start();

        CamelContext camelContext = main.getCamelContext();

        MockEndpoint endpoint = camelContext.getEndpoint("mock:results", MockEndpoint.class);
        endpoint.expectedBodiesReceived("I am hello bean");

        camelContext.createProducerTemplate().sendBody("direct:start", "Hello World");

        endpoint.assertIsSatisfied();

        main.stop();
    }

}
