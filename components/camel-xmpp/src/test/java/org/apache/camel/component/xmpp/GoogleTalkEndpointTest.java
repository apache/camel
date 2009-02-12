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
package org.apache.camel.component.xmpp;

import org.apache.camel.builder.RouteBuilder;

public class GoogleTalkEndpointTest extends GoogleTalkTest {

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() throws Exception {
                XmppEndpoint endpoint = new XmppEndpoint();
                endpoint.setCamelContext(context);
                endpoint.setHost("talk.google.com");
                endpoint.setPort(5222);
                endpoint.setUser("user");
                endpoint.setPassword("secret");
                endpoint.setServiceName("gmail.com");
                endpoint.setParticipant("touser@gmail.com");

                context.addEndpoint("talk", endpoint);

                from("direct:start").
                    to("talk").
                    to("mock:result");
            }
        };
    }
}