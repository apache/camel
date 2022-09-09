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
package org.apache.camel.component.atom;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.infra.jetty.services.JettyConfiguration;
import org.apache.camel.test.infra.jetty.services.JettyConfigurationBuilder;
import org.apache.camel.test.infra.jetty.services.JettyEmbeddedService;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.RegisterExtension;

@DisabledOnOs(OS.AIX)
public class AtomEntryPollingConsumerWithBasicAuthTest extends AtomEntryPollingConsumerTest {
    private static final int PORT = AvailablePortFinder.getNextAvailable();

    @RegisterExtension
    public JettyEmbeddedService service = new JettyEmbeddedService(
            JettyConfigurationBuilder.bareTemplate()
                    .withPort(PORT)
                    .addBasicUser("camel", "camelPass", "Private!")
                    .addServletConfiguration(new JettyConfiguration.ServletConfiguration<>(
                            new MyHttpServlet(), JettyConfiguration.ServletConfiguration.ROOT_PATH_SPEC))
                    .build());

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("atom:http://localhost:" + PORT
                     + "/?splitEntries=true&delay=500&username=camel&password=camelPass")
                             .to("mock:result1");

                from("atom:http://localhost:" + PORT
                     + "/?splitEntries=true&filter=false&delay=500&username=camel&password=camelPass")
                             .to("mock:result2");
            }
        };
    }
}
