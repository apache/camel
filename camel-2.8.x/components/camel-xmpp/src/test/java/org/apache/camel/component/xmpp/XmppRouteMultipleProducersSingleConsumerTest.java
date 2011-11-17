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
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Ignore;
import org.junit.Test;

/**
 * @version 
 */
public class XmppRouteMultipleProducersSingleConsumerTest extends CamelTestSupport {
    protected MockEndpoint goodEndpoint;
    protected MockEndpoint badEndpoint;

    @Ignore
    @Test
    public void testProducerGetsEverything() throws Exception {

        goodEndpoint = (MockEndpoint)context.getEndpoint("mock:good");
        badEndpoint = (MockEndpoint)context.getEndpoint("mock:bad");

        goodEndpoint.expectedMessageCount(4);
        badEndpoint.expectedMessageCount(0);

        template.sendBody("direct:toProducer1", "From producer");
        template.sendBody("direct:toProducer1", "From producer");

        template.sendBody("direct:toProducer2", "From producer1");
        template.sendBody("direct:toProducer2", "From producer1");

        goodEndpoint.assertIsSatisfied();
        badEndpoint.assertIsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {

                //getContext().setTracing(true);

                from("direct:toProducer1")
                    .to(getProducer1Uri());

                from("direct:toProducer2")
                    .to(getProducer2Uri());

                from(getConsumerUri())
                    .to(getConsumerUri());

                from(getProducer1Uri())
                    .to("mock:good");

                from(getProducer2Uri())
                    .to("mock:bad");
            }
        };
    }

    protected String getProducer1Uri() {
        return "xmpp://jabber.org:5222/camel_consumer@jabber.org?user=camel_producer&password=secret&serviceName=jabber.org";
    }

    protected String getProducer2Uri() {
        return "xmpp://jabber.org:5222/camel_consumer@jabber.org?user=camel_producer1&password=secret&serviceName=jabber.org";
    }
    
    protected String getConsumerUri() {
        return "xmpp://jabber.org:5222/camel_producer@jabber.org?user=camel_consumer&password=secret&serviceName=jabber.org";
    }

}
