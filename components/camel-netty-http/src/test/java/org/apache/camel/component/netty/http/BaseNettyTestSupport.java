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
package org.apache.camel.component.netty.http;

import java.util.Collection;
import java.util.Properties;

import io.netty.buffer.ByteBufAllocator;
import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.logging.log4j.core.LogEvent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base test class for Netty.
 */
public class BaseNettyTestSupport extends CamelTestSupport {
    protected static final Logger LOG = LoggerFactory.getLogger(BaseNettyTestSupport.class);

    @RegisterExtension
    AvailablePortFinder.Port port = AvailablePortFinder.find();

    @BeforeAll
    public static void startLeakDetection() {
        System.setProperty("io.netty.leakDetection.maxRecords", "100");
        System.setProperty("io.netty.leakDetection.acquireAndReleaseOnly", "true");
        System.setProperty("io.netty.leakDetection.targetRecords", "100");
        LogCaptureAppender.reset();
    }

    @AfterAll
    public static void verifyNoLeaks() {
        //Force GC to bring up leaks
        System.gc();
        //Kick leak detection logging
        ByteBufAllocator.DEFAULT.buffer(1).release();
        Collection<LogEvent> events = LogCaptureAppender.getEvents();
        if (!events.isEmpty()) {
            String message = "Leaks detected while running tests: " + events;
            // Just write the message into log to help debug
            for (LogEvent event : events) {
                LOG.info(event.getMessage().getFormattedMessage());
            }
            LogCaptureAppender.reset();
            throw new AssertionError(message);
        }
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getPropertiesComponent().setLocation("ref:prop");
        return context;
    }

    @BindToRegistry("prop")
    public Properties loadProp() {

        Properties prop = new Properties();
        prop.setProperty("port", Integer.toString(getPort()));

        return prop;
    }

    protected int getPort() {
        return port.getPort();
    }

}
