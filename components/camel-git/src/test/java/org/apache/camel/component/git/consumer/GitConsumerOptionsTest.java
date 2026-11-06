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
package org.apache.camel.component.git.consumer;

import java.util.concurrent.TimeUnit;

import org.apache.camel.LoggingLevel;
import org.apache.camel.component.git.GitEndpoint;
import org.apache.camel.component.git.GitTestSupport;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.BridgeExceptionHandlerToErrorHandler;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The scheduler and error handler options of the endpoint are applied to the consumer of every type.
 */
public class GitConsumerOptionsTest extends GitTestSupport {

    private final RecordingExceptionHandler exceptionHandler = new RecordingExceptionHandler();

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("recordingExceptionHandler", exceptionHandler);
    }

    @ParameterizedTest
    @ValueSource(strings = { "commit", "tag", "branch" })
    public void schedulerOptionsReachTheConsumer(String type) throws Exception {
        AbstractGitConsumer consumer = createConsumer(type, "delay=60000&initialDelay=5000&timeUnit=SECONDS"
                                                            + "&startScheduler=false&useFixedDelay=false&greedy=true"
                                                            + "&backoffMultiplier=4&backoffIdleThreshold=2"
                                                            + "&backoffErrorThreshold=3&repeatCount=7"
                                                            + "&runLoggingLevel=DEBUG&sendEmptyMessageWhenIdle=true");

        assertEquals(60000, consumer.getDelay());
        assertEquals(5000, consumer.getInitialDelay());
        assertEquals(TimeUnit.SECONDS, consumer.getTimeUnit());
        assertFalse(consumer.isStartScheduler());
        assertFalse(consumer.isUseFixedDelay());
        assertTrue(consumer.isGreedy());
        assertEquals(4, consumer.getBackoffMultiplier());
        assertEquals(2, consumer.getBackoffIdleThreshold());
        assertEquals(3, consumer.getBackoffErrorThreshold());
        assertEquals(7, consumer.getRepeatCount());
        assertEquals(LoggingLevel.DEBUG, consumer.getRunLoggingLevel());
        assertTrue(consumer.isSendEmptyMessageWhenIdle());
    }

    @ParameterizedTest
    @ValueSource(strings = { "commit", "tag", "branch" })
    public void exceptionHandlerReachesTheConsumer(String type) throws Exception {
        AbstractGitConsumer consumer = createConsumer(type, "exceptionHandler=#recordingExceptionHandler");

        assertSame(exceptionHandler, consumer.getExceptionHandler());
    }

    @ParameterizedTest
    @ValueSource(strings = { "commit", "tag", "branch" })
    public void bridgeErrorHandlerReachesTheConsumer(String type) throws Exception {
        AbstractGitConsumer consumer = createConsumer(type, "bridgeErrorHandler=true");

        assertInstanceOf(BridgeExceptionHandlerToErrorHandler.class, consumer.getExceptionHandler());
    }

    private AbstractGitConsumer createConsumer(String type, String options) throws Exception {
        GitEndpoint endpoint = context.getEndpoint("git://" + getGitDir().getPath() + "?type=" + type + "&" + options,
                GitEndpoint.class);
        return (AbstractGitConsumer) endpoint.createConsumer(exchange -> {
        });
    }
}
