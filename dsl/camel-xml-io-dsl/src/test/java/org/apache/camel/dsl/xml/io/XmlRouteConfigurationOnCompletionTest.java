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
package org.apache.camel.dsl.xml.io;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResourceHelper;
import org.junit.jupiter.api.Test;

/**
 * CAMEL-25420: an onCompletion of a route configuration loaded from XML fires once per exchange, as with the Java DSL,
 * when the exchange goes through several routes that the configuration applies to.
 */
public class XmlRouteConfigurationOnCompletionTest {

    private static final String ROUTES = """
            <routes xmlns="http://camel.apache.org/schema/xml-io">
              <route routeConfigurationId="%s">
                <from uri="direct:consumer"/>
                <to uri="direct:processor"/>
              </route>
              <route routeConfigurationId="%s">
                <from uri="direct:processor"/>
                <to uri="mock:result"/>
              </route>
            </routes>
            """;

    private static void load(DefaultCamelContext context, String configurations, String routes) throws Exception {
        PluginHelper.getRoutesLoader(context).loadRoutes(
                ResourceHelper.fromString("configurations.xml", configurations),
                ResourceHelper.fromString("routes.xml", routes));
    }

    @Test
    public void testNamedConfigurationOnBothRoutesFiresOnce() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            load(context, """
                    <routeConfigurations xmlns="http://camel.apache.org/schema/xml-io">
                      <routeConfiguration id="myconfig">
                        <onCompletion>
                          <to uri="mock:completion"/>
                        </onCompletion>
                      </routeConfiguration>
                    </routeConfigurations>
                    """, ROUTES.formatted("myconfig", "myconfig"));
            context.start();

            MockEndpoint completion = context.getEndpoint("mock:completion", MockEndpoint.class);
            completion.expectedMessageCount(1);
            MockEndpoint result = context.getEndpoint("mock:result", MockEndpoint.class);
            result.expectedBodiesReceived("Hello World");

            context.createProducerTemplate().sendBody("direct:consumer", "Hello World");

            MockEndpoint.assertIsSatisfied(context);
        }
    }

    @Test
    public void testGlobalConfigurationFiresOnce() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            load(context, """
                    <routeConfigurations xmlns="http://camel.apache.org/schema/xml-io">
                      <routeConfiguration>
                        <onCompletion>
                          <to uri="mock:completion"/>
                        </onCompletion>
                      </routeConfiguration>
                    </routeConfigurations>
                    """, """
                    <routes xmlns="http://camel.apache.org/schema/xml-io">
                      <route>
                        <from uri="direct:consumer"/>
                        <to uri="direct:processor"/>
                      </route>
                      <route>
                        <from uri="direct:processor"/>
                        <to uri="mock:result"/>
                      </route>
                    </routes>
                    """);
            context.start();

            MockEndpoint completion = context.getEndpoint("mock:completion", MockEndpoint.class);
            completion.expectedMessageCount(1);
            MockEndpoint result = context.getEndpoint("mock:result", MockEndpoint.class);
            result.expectedBodiesReceived("Hello World");

            context.createProducerTemplate().sendBody("direct:consumer", "Hello World");

            MockEndpoint.assertIsSatisfied(context);
        }
    }
}
