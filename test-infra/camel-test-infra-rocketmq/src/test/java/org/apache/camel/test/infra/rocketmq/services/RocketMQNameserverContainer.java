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
package org.apache.camel.test.infra.rocketmq.services;

import java.util.Collections;

import org.apache.camel.test.infra.rocketmq.common.RocketMQProperties;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;

public class RocketMQNameserverContainer extends GenericContainer<RocketMQNameserverContainer> {
    public RocketMQNameserverContainer(Network network) {
        super(RocketMQContainer.ROCKETMQ_IMAGE);

        withNetwork(network);
        withNetworkAliases("nameserver");
        addExposedPort(RocketMQProperties.ROCKETMQ_NAMESRV_PORT);
        withTmpFs(Collections.singletonMap("/home/rocketmq/logs", "rw"));
        withCommand("sh", "mqnamesrv");
        withCreateContainerCmdModifier(cmd -> cmd.withName("nameserver"));

        waitingFor(Wait.forListeningPort());
    }
}
