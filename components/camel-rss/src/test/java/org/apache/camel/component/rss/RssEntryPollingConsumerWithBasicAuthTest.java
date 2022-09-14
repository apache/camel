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
package org.apache.camel.component.rss;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.infra.jetty.services.JettyConfiguration;
import org.apache.camel.test.infra.jetty.services.JettyConfigurationBuilder;
import org.apache.camel.test.infra.jetty.services.JettyEmbeddedService;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class RssEntryPollingConsumerWithBasicAuthTest extends CamelTestSupport {

    private static final int PORT = AvailablePortFinder.getNextAvailable();

    @RegisterExtension
    public JettyEmbeddedService service = new JettyEmbeddedService(
            JettyConfigurationBuilder.bareTemplate()
                    .withPort(PORT)
                    .withServletConfiguration()
                    .addServletConfiguration(new JettyConfiguration.ServletHandlerConfiguration.ServletConfiguration<>(
                            new MyHttpServlet(),
                            JettyConfiguration.ServletHandlerConfiguration.ServletConfiguration.ROOT_PATH_SPEC))
                    .addBasicAuthUser("camel", "camelPass", "Private!")
                    .build()
                    .build());

    @Test
    public void testListOfEntriesIsSplitIntoPieces() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(10);
        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                fromF("rss:http://localhost:%d/?splitEntries=true&sortEntries=true&delay=100&username=camel&password=camelPass",
                        PORT)
                                .to("mock:result");
            }
        };
    }
}
