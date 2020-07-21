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
package org.apache.camel.component.mongodb;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

public class MongoDbContainer extends GenericContainer {
    private static final Logger LOGGER = LoggerFactory.getLogger(MongoDbContainer.class);
    private static final String CONTAINER_NAME = "mongo";
    private static final int MONGODB_PORT = 27017;
    private static final String MONGO_IMAGE = "mongo:4.0";

    public MongoDbContainer() {
        super(MONGO_IMAGE);

        setWaitStrategy(Wait.forListeningPort());

        withNetworkAliases(CONTAINER_NAME);
        withExposedPorts(MONGODB_PORT);
        withLogConsumer(new Slf4jLogConsumer(LOGGER));
        withCommand(
            "--replSet", "replicationName",
            "--oplogSize", "5000",
            "--syncdelay", "0",
            "--noauth",
            "--noprealloc",
            "--smallfiles");
    }

    @Override
    public void start() {
        super.start();

        Document d = MongoClients.create(getConnectionURI())
            .getDatabase("admin")
            .runCommand(new Document("replSetInitiate", new Document()));

        LOGGER.info("replSetInitiate: {}", d);
        LOGGER.info("waiting to become master");

        try {
            execInContainer(
                "/bin/bash",
                "-c",
                "until mongo --eval \"printjson(rs.isMaster())\" | grep ismaster | grep true > /dev/null 2>&1; do sleep 1; done");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        LOGGER.info("started");
    }

    public String getConnectionAddress() {
        return getContainerIpAddress() + ":" + getMappedPort(MONGODB_PORT);
    }

    public String getConnectionURI() {
        return "mongodb://" + getConnectionAddress();
    }

    public MongoClient createClient() {
        return MongoClients.create(getConnectionURI());
    }
}
