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
package org.apache.camel.component.rmi;

import java.net.URI;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class RmiRouteEndpointTest extends RmiRouteTest {

    @Override
    protected RouteBuilder getRouteBuilder(final CamelContext context) {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                RmiEndpoint say = new RmiEndpoint();
                say.setCamelContext(context);
                say.setUri(new URI("rmi://localhost:" + getPort() + "/bye"));

                from("direct:hello").to(say);

                // When exposing an RMI endpoint, the interfaces it exposes must
                // be configured.
                RmiEndpoint bye = new RmiEndpoint();
                bye.setCamelContext(context);
                bye.setRemoteInterfaces(ISay.class);
                bye.setUri(new URI("rmi://localhost:" + getPort() + "/bye"));

                from(bye).to("bean:bye");
            }
        };
    }
}