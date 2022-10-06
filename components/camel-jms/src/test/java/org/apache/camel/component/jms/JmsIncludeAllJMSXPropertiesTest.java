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
package org.apache.camel.component.jms;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.activemq.services.ActiveMQService;
import org.junit.jupiter.api.Test;

public class JmsIncludeAllJMSXPropertiesTest extends AbstractJMSTest {

    @Test
    public void testIncludeAll() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:result").expectedHeaderReceived("foo", "bar");
        getMockEndpoint("mock:result").expectedHeaderReceived("JMSXUserID", "Donald");
        getMockEndpoint("mock:result").expectedHeaderReceived("JMSXAppID", "MyApp");

        Map<String, Object> headers = new HashMap<>();
        headers.put("foo", "bar");
        headers.put("JMSXUserID", "Donald");
        headers.put("JMSXAppID", "MyApp");

        template.sendBodyAndHeaders("activemq:queue:JmsIncludeAllJMSXPropertiesTest", "Hello World", headers);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected String getComponentName() {
        return "activemq";
    }

    @Override
    protected JmsComponent setupComponent(CamelContext camelContext, ActiveMQService service, String componentName) {
        final JmsComponent jms = super.setupComponent(camelContext, service, componentName);
        jms.getConfiguration().setIncludeAllJMSXProperties(true);
        return jms;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("activemq:queue:JmsIncludeAllJMSXPropertiesTest")
                        .to("mock:result");
            }
        };
    }

}
