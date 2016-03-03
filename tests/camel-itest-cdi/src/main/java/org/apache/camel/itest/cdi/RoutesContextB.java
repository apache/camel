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
package org.apache.camel.itest.cdi;

import javax.inject.Inject;

import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cdi.ContextName;
import org.apache.camel.cdi.Uri;
import org.apache.camel.component.mock.MockEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ContextName("contextB")
public class RoutesContextB extends RouteBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(RoutesContextB.class);

    @Inject
    @ContextName("contextB")
    @Uri(value = "seda:B.a")
    Endpoint a;

    @EndpointInject(uri = "mock:B.b", context = "contextB")
    MockEndpoint b;

    @Inject
    @ContextName("contextB")
    @Uri(value = "seda:B.a")
    ProducerTemplate producer;

    @Override
    public void configure() {
        LOG.info("Adding route from " + a + " to " + b);
        from(a).to(b);
    }

    void sendMessages() {
        for (Object expectedBody : Constants.EXPECTED_BODIES_B) {
            LOG.info("Sending " + expectedBody + " to " + producer.getDefaultEndpoint());
            producer.sendBody(expectedBody);
        }
    }
}
