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

import io.netty.buffer.ByteBufAllocator;
import org.apache.logging.log4j.core.LogEvent;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LeakDetection implements BeforeAllCallback, AfterAllCallback {

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        System.setProperty("io.netty.leakDetection.maxRecords", "100");
        System.setProperty("io.netty.leakDetection.acquireAndReleaseOnly", "true");
        System.setProperty("io.netty.leakDetection.targetRecords", "100");
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        // Force GC to bring up leaks
        System.gc();
        // Kick leak detection logging
        ByteBufAllocator.DEFAULT.buffer(1).release();
        final Collection<LogEvent> events = LogCaptureAppender.getEvents();
        if (!events.isEmpty()) {
            final String message = "Leaks detected while running tests: " + events;
            // Just write the message into log to help debug
            Logger log = LoggerFactory.getLogger(context.getRequiredTestClass());
            for (final LogEvent event : events) {
                log.info(event.getMessage().getFormattedMessage());
            }
            LogCaptureAppender.reset();
            throw new AssertionError(message);
        }
    }

}
