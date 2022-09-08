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
package org.apache.camel.component.ahc.ws;

import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.infra.jetty.services.JettyConfiguration;
import org.apache.camel.test.infra.jetty.services.JettyConfigurationBuilder;
import org.apache.camel.test.infra.jetty.services.JettyEmbeddedService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

@Timeout(10)
public class WsProducerUsingStreamingTest extends WsProducerTestBase {

    private final JettyConfiguration jettyConfiguration = JettyConfigurationBuilder
            .emptyTemplate()
            .withPort(AvailablePortFinder.getNextAvailable())
            .withContextPath(JettyConfiguration.ROOT_CONTEXT_PATH)
            .addServletConfiguration(new JettyConfiguration.ServletConfiguration(
                    TestServletFactory.class.getName(), JettyConfiguration.ServletConfiguration.ROOT_PATH_SPEC))
            .build();
    @RegisterExtension
    public JettyEmbeddedService service = new JettyEmbeddedService(jettyConfiguration);

    @Override
    protected void setUpComponent() {
    }

    @Disabled("Flaky test that was previously disabled")
    @Override
    public void testWriteBytesToWebsocket() {
        // NO-OP
    }

    @Override
    protected String getTextTestMessage() {
        return super.getTextTestMessage();
    }

    @Override
    protected byte[] getByteTestMessage() {
        return createLongByteTestMessage();
    }

    @Override
    protected String getTargetURL() {
        return "ahc-ws://localhost:" + service.getPort() + "?useStreaming=true";
    }
}
