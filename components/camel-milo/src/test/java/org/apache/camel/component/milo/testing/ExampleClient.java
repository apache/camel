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
package org.apache.camel.component.milo.testing;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.milo.NodeIds;
import org.apache.camel.impl.DefaultCamelContext;

/**
 * An example application focusing on the OPC UA client endpoint
 */
public final class ExampleClient {

    private ExampleClient() {
    }

    public static void main(final String[] args) throws Exception {

        // camel conext

        final CamelContext context = new DefaultCamelContext();

        // add routes

        context.addRoutes(new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                // bridge item1 to item2
                final String item1 = NodeIds.nodeValue("urn:org:apache:camel", "items-MyItem");
                final String item2 = NodeIds.nodeValue("urn:org:apache:camel", "items-MyItem2");
                from("milo-client:tcp://foo:bar@localhost:12685?node=" + item1).log("From OPC UA: ${body}")
                    .to("milo-client:tcp://foo:bar@localhost:12685?node" + item2);
            }
        });

        // start

        context.start();

        // sleep

        while (true) {
            Thread.sleep(Long.MAX_VALUE);
        }
    }
}
