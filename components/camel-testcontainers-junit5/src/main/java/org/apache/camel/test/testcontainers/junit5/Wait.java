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
package org.apache.camel.test.testcontainers.junit5;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

import com.github.dockerjava.api.DockerClient;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.output.WaitingConsumer;
import org.testcontainers.containers.wait.strategy.AbstractWaitStrategy;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.utility.LogUtils;

public class Wait extends org.testcontainers.containers.wait.strategy.Wait {
    /**
     * Convenience method to return a WaitStrategy for log messages using a
     * predicate.
     *
     * @param predicate the predicate to apply to log messages
     * @param times the number of times the pattern is expected
     * @return WaitStrategy
     */
    public static WaitStrategy forLogPredicate(Predicate<OutputFrame> predicate, int times) {
        return new AbstractWaitStrategy() {
            @Override
            protected void waitUntilReady() {
                final DockerClient client = DockerClientFactory.instance().client();
                final WaitingConsumer waitingConsumer = new WaitingConsumer();

                LogUtils.followOutput(client, waitStrategyTarget.getContainerId(), waitingConsumer);

                try {
                    waitingConsumer.waitUntil(predicate, startupTimeout.getSeconds(), TimeUnit.SECONDS, times);
                } catch (TimeoutException e) {
                    throw new ContainerLaunchException("Timed out");
                }
            }
        };
    }

    /**
     * Convenience method to return a WaitStrategy for log messages.
     *
     * @param text the text to find
     * @param times the number of times the pattern is expected
     * @return WaitStrategy
     */
    public static WaitStrategy forLogMessageContaining(String text, int times) {
        return forLogPredicate(u -> u.getUtf8String().contains(text), times);
    }
}
