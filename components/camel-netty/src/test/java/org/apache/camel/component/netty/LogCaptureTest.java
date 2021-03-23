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
package org.apache.camel.component.netty;

import io.netty.util.ResourceLeakDetector;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.apache.camel.processor.errorhandler.DefaultErrorHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This test ensures LogCaptureAppender is configured properly
 */
@Isolated
public class LogCaptureTest {
    @Test
    public void testCapture() {
        InternalLoggerFactory.getInstance(ResourceLeakDetector.class).error("testError");
        assertFalse(LogCaptureAppender.getEvents(ResourceLeakDetector.class).isEmpty());
        assertTrue(LogCaptureAppender.hasEventsFor(ResourceLeakDetector.class));
        assertTrue(LogCaptureAppender.getEvents(DefaultErrorHandler.class).isEmpty());
        LogCaptureAppender.reset();
    }
}
