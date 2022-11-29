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

package org.apache.camel.component.rocketmq.infra;

import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.rocketmq.broker.BrokerController;
import org.apache.rocketmq.common.BrokerConfig;
import org.apache.rocketmq.common.namesrv.NamesrvConfig;
import org.apache.rocketmq.namesrv.NamesrvController;
import org.apache.rocketmq.remoting.netty.NettyClientConfig;
import org.apache.rocketmq.remoting.netty.NettyServerConfig;
import org.apache.rocketmq.store.config.MessageStoreConfig;
import org.apache.rocketmq.test.util.MQAdmin;
import org.apache.rocketmq.test.util.TestUtils;

public final class EmbeddedRocketMQServer {

    private static final AtomicInteger BROKER_INDEX = new AtomicInteger();

    private static final AtomicInteger BROKER_PORTS = new AtomicInteger(61000);

    private EmbeddedRocketMQServer() {
    }

    public static NamesrvController createAndStartNamesrv(int port) throws Exception {
        NettyServerConfig serverConfig = new NettyServerConfig();
        serverConfig.setListenPort(port);
        NamesrvController result = new NamesrvController(new NamesrvConfig(), serverConfig);
        result.initialize();
        result.start();
        return result;
    }

    public static BrokerController createAndStartBroker(String nsAddr) throws Exception {
        NettyServerConfig nettyServerConfig = new NettyServerConfig();
        nettyServerConfig.setListenPort(BROKER_PORTS.getAndIncrement());
        BrokerController result = new BrokerController(
                prepareBrokerConfig(nsAddr), nettyServerConfig, new NettyClientConfig(), prepareMessageStoreConfig());
        result.initialize();
        result.start();
        return result;
    }

    private static BrokerConfig prepareBrokerConfig(final String nsAddr) {
        BrokerConfig brokerConfig = new BrokerConfig();
        brokerConfig.setNamesrvAddr(nsAddr);
        brokerConfig.setBrokerName("CamelRocketMQBroker" + BROKER_INDEX.getAndIncrement());
        brokerConfig.setBrokerIP1("127.0.0.1");
        brokerConfig.setSendMessageThreadPoolNums(1);
        brokerConfig.setPutMessageFutureThreadPoolNums(1);
        brokerConfig.setPullMessageThreadPoolNums(1);
        brokerConfig.setProcessReplyMessageThreadPoolNums(1);
        brokerConfig.setQueryMessageThreadPoolNums(1);
        brokerConfig.setAdminBrokerThreadPoolNums(1);
        brokerConfig.setClientManageThreadPoolNums(1);
        brokerConfig.setConsumerManageThreadPoolNums(1);
        brokerConfig.setHeartbeatThreadPoolNums(1);
        return brokerConfig;
    }

    private static MessageStoreConfig prepareMessageStoreConfig() {
        String baseDir = System.getProperty("java.io.tmpdir") + "/embedded_rocketmq/" + System.currentTimeMillis();
        MessageStoreConfig storeConfig = new MessageStoreConfig();
        storeConfig.setStorePathRootDir(baseDir);
        storeConfig.setStorePathCommitLog(baseDir + "/commitlog");
        storeConfig.setMappedFileSizeCommitLog(1024 * 1024 * 10);
        storeConfig.setMaxIndexNum(100);
        storeConfig.setMaxHashSlotNum(400);
        storeConfig.setHaListenPort(BROKER_PORTS.getAndIncrement());
        return storeConfig;
    }

    public static void createTopic(String namesrvAddr, String defaultCluster, String topic) throws TimeoutException {
        long startTime = System.currentTimeMillis();
        while (!MQAdmin.createTopic(namesrvAddr, defaultCluster, topic, 4, 3)) {
            if (System.currentTimeMillis() - startTime > 30 * 1000) {
                throw new TimeoutException(
                        String.format("Failed to create topic [%s] after %d ms", topic,
                                System.currentTimeMillis() - startTime));
            }
            TestUtils.waitForMoment(500);
        }
    }
}
