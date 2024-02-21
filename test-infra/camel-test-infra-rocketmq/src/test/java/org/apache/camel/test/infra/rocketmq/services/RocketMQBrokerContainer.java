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
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;

public class RocketMQBrokerContainer extends GenericContainer<RocketMQBrokerContainer> {

    public RocketMQBrokerContainer(Network network, String confName) {
        super(RocketMQContainer.ROCKETMQ_IMAGE);

        withNetwork(network);
        withExposedPorts(RocketMQProperties.ROCKETMQ_BROKER3_PORT,
                RocketMQProperties.ROCKETMQ_BROKER2_PORT,
                RocketMQProperties.ROCKETMQ_BROKER1_PORT);
        withEnv("NAMESRV_ADDR", "nameserver:9876");
        withClasspathResourceMapping(confName + "/" + confName + ".conf",
                "/opt/rocketmq-" + RocketMQContainer.ROCKETMQ_VERSION + "/conf/broker.conf",
                BindMode.READ_WRITE);

        withTmpFs(Collections.singletonMap("/home/rocketmq/store", "rw"));
        withTmpFs(Collections.singletonMap("/home/rocketmq/logs", "rw"));
        withCommand("sh", "mqbroker",
                "-c", "/opt/rocketmq-" + RocketMQContainer.ROCKETMQ_VERSION + "/conf/broker.conf");

        waitingFor(Wait.forListeningPort());
        withCreateContainerCmdModifier(cmd -> cmd.withName(confName));
    }
}
