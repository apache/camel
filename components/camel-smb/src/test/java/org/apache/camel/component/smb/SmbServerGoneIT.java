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
package org.apache.camel.component.smb;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import com.hierynomus.smbj.SmbConfig;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.smb.services.SmbService;
import org.apache.camel.test.infra.smb.services.SmbServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.StopWatch;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Stopping a route whose SMB server went away does not wait for the server to answer the disconnect, and stopping a
 * route logs no WARN for the directories smbj holds a lease on. Uses a server of its own, as it pauses it.
 */
class SmbServerGoneIT extends CamelTestSupport {

    // a server of its own, as the test pauses it
    @RegisterExtension
    static SmbService service = SmbServiceFactory.createService();

    private static final long SMB_TIMEOUT_MILLIS = 10000;

    private final List<String> warnings = new CopyOnWriteArrayList<>();
    private final AbstractAppender appender
            = new AbstractAppender("SmbServerGoneIT", null, null, true, Property.EMPTY_ARRAY) {
                @Override
                public void append(LogEvent event) {
                    if (event.getLevel().isMoreSpecificThan(Level.WARN)) {
                        warnings.add(event.getLoggerName() + ": " + event.getMessage().getFormattedMessage());
                    }
                }
            };

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @BeforeEach
    void captureWarnings() {
        appender.start();
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        ctx.getConfiguration().getRootLogger().addAppender(appender, Level.WARN, null);
        ctx.updateLoggers();
    }

    @AfterEach
    void removeAppender() {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        ctx.getConfiguration().getRootLogger().removeAppender(appender.getName());
        ctx.updateLoggers();
        appender.stop();
    }

    @Test
    void stopLogsNoWarnForLeasedDirectories() throws Exception {
        // noop=true: the directory does not change, so smbj keeps the lease on it until the route stops
        startRoute("leased", "noop=true");

        context.stop();

        assertTrue(warnings.isEmpty(), "warnings: " + warnings);
    }

    @Test
    void stopWhenTheServerDoesNotAnswer() throws Exception {
        assumeTrue(service instanceof SmbServiceFactory.SmbLocalContainerService, "needs a local container to pause");
        GenericContainer<?> container = ((SmbServiceFactory.SmbLocalContainerService) service).getContainer();

        startRoute("gone", "delete=true");
        container.getDockerClient().pauseContainerCmd(container.getContainerId()).exec();
        try {
            StopWatch watch = new StopWatch();
            context.stop();
            long taken = watch.taken();

            // a poll that is running waits up to the timeout of the SmbConfig for the server; before, the disconnect
            // then waited that long again for the close of the share, and again for the logoff of the session
            assertTrue(taken < SMB_TIMEOUT_MILLIS + SmbOperations.DISCONNECT_WAIT_MILLIS + 3000,
                    "the stop took " + taken + " ms");
        } finally {
            container.getDockerClient().unpauseContainerCmd(container.getContainerId()).exec();
        }
    }

    private void startRoute(String directory, String options) throws Exception {
        context.getRegistry().bind("smbConfig", SmbConfig.builder()
                .withTimeout(SMB_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                .build());
        String uri = String.format("smb:%s/%s/%s?username=%s&password=%s&delay=200&initialDelay=0&%s",
                service.address(), service.shareName(), directory, service.username(), service.password(), options);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(uri).to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        template.sendBodyAndHeader(uri, "Hello World", Exchange.FILE_NAME, "hello.txt");
        mock.assertIsSatisfied();
    }
}
