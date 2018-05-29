/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.consul.support;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.github.dockerjava.api.DockerClient;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.output.WaitingConsumer;
import org.testcontainers.containers.wait.strategy.AbstractWaitStrategy;
import org.testcontainers.utility.LogUtils;

public final class ConsulContainerWaitStrategy extends AbstractWaitStrategy {
    @Override
    protected void waitUntilReady() {
        final DockerClient client = DockerClientFactory.instance().client();
        final WaitingConsumer waitingConsumer = new WaitingConsumer();

        LogUtils.followOutput(client, waitStrategyTarget.getContainerId(), waitingConsumer);

        try {
            waitingConsumer.waitUntil(
                f -> f.getUtf8String().contains("Synced node info"),
                startupTimeout.getSeconds(),
                TimeUnit.SECONDS,
                1
            );
        } catch (TimeoutException e) {
            throw new ContainerLaunchException("Timed out");
        }
    }
}