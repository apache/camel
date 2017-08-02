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
import org.apache.camel.component.milo.server.MiloServerComponent;
import org.apache.camel.impl.DefaultCamelContext;

/**
 * This is a simple example application which tests a few ways of mapping data
 * to an OPC UA server instance.
 */
public final class ExampleServer {
    private ExampleServer() {
    }

    public static void main(final String[] args) throws Exception {

        // camel conext

        final CamelContext context = new DefaultCamelContext();

        // configure milo

        ((MiloServerComponent)context.getComponent("milo-server"))
            .setUserAuthenticationCredentials("foo:bar");

        // add routes

        context.addRoutes(new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                /*
                 * Take an MQTT topic and forward its content to an OPC UA
                 * server item. You can e.g. take some MQTT application and an
                 * OPC UA client, connect with both applications to their
                 * topics/items. When you write on the MQTT item it will pop up
                 * on the OPC UA item.
                 */
                from("paho:my/foo/bar?brokerUrl=tcp://iot.eclipse.org:1883").log("Temp update: ${body}")
                    .convertBodyTo(String.class).to("milo-server:MyItem");

                /*
                 * Creating a simple item which has not data but logs anything
                 * which gets written to by an OPC UA write call
                 */
                from("milo-server:MyItem").log("MyItem: ${body}");

                /*
                 * Creating an item which takes write command and forwards them
                 * to an MQTT topic
                 */
                from("milo-server:MyItem2").log("MyItem2: ${body}").convertBodyTo(String.class)
                    .to("paho:de/dentrassi/camel/milo/temperature?brokerUrl=tcp://iot.eclipse.org:1883");

                /*
                 * Re-read the output from the previous route from MQTT to the
                 * local logging
                 */
                from("paho:de/dentrassi/camel/milo/temperature?brokerUrl=tcp://iot.eclipse.org:1883")
                    .log("Back from MQTT: ${body}");
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
