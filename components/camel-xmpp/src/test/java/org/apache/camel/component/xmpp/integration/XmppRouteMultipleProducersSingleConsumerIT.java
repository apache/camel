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
package org.apache.camel.component.xmpp.integration;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.xmpp.XmppConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@DisabledIfSystemProperty(named = "ci.env.name", matches = "github.com",
                          disabledReason = "Github environment has trouble running the XMPP test container and/or component")
public class XmppRouteMultipleProducersSingleConsumerIT extends XmppBaseIT {
    protected MockEndpoint goodEndpoint;
    protected MockEndpoint badEndpoint;

    @Test
    public void testProducerGetsEverything() throws Exception {

        goodEndpoint = context.getEndpoint("mock:good", MockEndpoint.class);
        badEndpoint = context.getEndpoint("mock:bad", MockEndpoint.class);

        goodEndpoint.expectedMessageCount(4);
        badEndpoint.expectedMessageCount(0);

        template.sendBody("direct:toProducer1", "From producer");
        template.sendBody("direct:toProducer1", "From producer");

        template.sendBody("direct:toProducer2", "From producer1");
        template.sendBody("direct:toProducer2", "From producer1");

        goodEndpoint.assertIsSatisfied();
        badEndpoint.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {

                from("direct:toProducer1")
                        .to(getProducer1Uri());

                from("direct:toProducer2")
                        .to(getProducer2Uri());

                from(getConsumerUri())
                        .removeHeader(XmppConstants.TO)
                        .to(getConsumerUri());

                from(getProducer1Uri())
                        .to("mock:good");

                from(getProducer2Uri())
                        .to("mock:bad");
            }
        };
    }

    protected String getProducer1Uri() {
        return "xmpp://localhost:" + getUrl()
               + "/camel_consumer@apache.camel?connectionConfig=#customConnectionConfig&room=camel-test-room@conference.apache.camel&user=camel_producer&password=secret&serviceName=apache.camel";
    }

    protected String getProducer2Uri() {
        return "xmpp://localhost:" + getUrl()
               + "/camel_consumer@apache.camel?connectionConfig=#customConnectionConfig&user=camel_producer1&password=secret&serviceName=apache.camel";
    }

    protected String getConsumerUri() {
        return "xmpp://localhost:" + getUrl()
               + "/camel_producer@apache.camel?connectionConfig=#customConnectionConfig&room=camel-test-room@conference.apache.camel&user=camel_consumer&password=secret&serviceName=apache.camel";
    }
}
