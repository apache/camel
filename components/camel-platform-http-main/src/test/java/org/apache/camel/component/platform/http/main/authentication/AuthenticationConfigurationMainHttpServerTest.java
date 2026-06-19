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
package org.apache.camel.component.platform.http.main.authentication;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.platform.http.main.DefaultMainHttpServerFactory;
import org.apache.camel.main.Main;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AuthenticationConfigurationMainHttpServerTest {

    @RegisterExtension
    static AvailablePortFinder.Port port = AvailablePortFinder.find();

    @Test
    public void testIncompleteAuthenticationConfiguration() {
        Main main = new Main();
        main.setPropertyPlaceholderLocations("incomplete-auth.properties");
        main.configure().addRoutesBuilder(new PlatformHttpRouteBuilder());
        main.enableTrace();

        assertThrows(RuntimeException.class, main::start);

        main.stop();
    }

    @Test
    public void testAuthenticationEnabledWithoutMechanism() {
        List<String> warnings = new CopyOnWriteArrayList<>();
        AbstractAppender appender = new AbstractAppender("CaptureWarn", null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                if (event.getLevel() == Level.WARN) {
                    warnings.add(event.getMessage().getFormattedMessage());
                }
            }
        };
        appender.start();
        Logger logger = (Logger) LogManager.getLogger(DefaultMainHttpServerFactory.class);
        logger.addAppender(appender);

        Main main = MainHttpServerAuthenticationTestSupport.createMain(
                "auth-no-mechanism.properties", port, new PlatformHttpRouteBuilder());
        try {
            // Unlike an incomplete JWT configuration, a missing mechanism must not fail startup: the server
            // still starts (unprotected) so the change stays backward compatible.
            assertDoesNotThrow(main::start);
            // ...but a clear warning must be logged so the misconfiguration is surfaced rather than silent.
            assertTrue(warnings.stream().anyMatch(m -> m.contains("no authentication mechanism is configured")),
                    "Expected a warning about the missing authentication mechanism, but got: " + warnings);
        } finally {
            logger.removeAppender(appender);
            appender.stop();
            MainHttpServerAuthenticationTestSupport.stopMain(main);
        }
    }

    private static class PlatformHttpRouteBuilder extends RouteBuilder {
        @Override
        public void configure() throws Exception {
            from("platform-http:/main-http-test")
                    .log("Received request with headers: ${headers}\nWith body: ${body}")
                    .setBody(simple("main-http-auth-basic-test-response"));
        }
    }
}
