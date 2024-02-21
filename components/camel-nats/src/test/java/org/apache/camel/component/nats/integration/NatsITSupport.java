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
package org.apache.camel.component.nats.integration;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.LogManager;

import org.apache.camel.CamelContext;
import org.apache.camel.component.nats.NatsComponent;
import org.apache.camel.test.infra.nats.services.NatsService;
import org.apache.camel.test.infra.nats.services.NatsServiceFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NatsITSupport extends CamelTestSupport {
    @RegisterExtension
    static NatsService service = NatsServiceFactory.createService();

    static {
        try (InputStream is = NatsITSupport.class.getClassLoader().getResourceAsStream("logging.properties")) {
            LogManager.getLogManager().readConfiguration(is);
        } catch (IOException e) {
            Logger logger = LoggerFactory.getLogger(NatsITSupport.class);

            logger.warn(
                    "Unable to setup JUL-to-slf4j logging bridge. The test execution should result in a log of bogus output. Error: {}",
                    e.getMessage(), e);
        }
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        NatsComponent nats = context.getComponent("nats", NatsComponent.class);
        nats.setServers(service.getServiceAddress());
        return context;
    }
}
