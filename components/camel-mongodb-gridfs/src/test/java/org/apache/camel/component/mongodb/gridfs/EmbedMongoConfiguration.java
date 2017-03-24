/**
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
package org.apache.camel.component.mongodb.gridfs;

import java.io.IOException;
import java.net.UnknownHostException;

import com.mongodb.MongoClient;
import de.flapdoodle.embed.mongo.Command;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.IMongodConfig;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.config.RuntimeConfigBuilder;
import de.flapdoodle.embed.process.config.IRuntimeConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static de.flapdoodle.embed.mongo.distribution.Version.Main.PRODUCTION;
import static de.flapdoodle.embed.process.runtime.Network.localhostIsIPv6;
import static org.springframework.util.SocketUtils.findAvailableTcpPort;

@Configuration
public class EmbedMongoConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(EmbedMongoConfiguration.class);
    private static final int PORT = findAvailableTcpPort(18500); // 1024 is too low on CI servers to find free ports

    static {
        try {
            IRuntimeConfig runtimeConfig = new RuntimeConfigBuilder()
                .defaultsWithLogger(Command.MongoD, LOGGER)
                .build();

            IMongodConfig mongodConfig = new MongodConfigBuilder()
                .version(PRODUCTION)
                .net(new Net(PORT, localhostIsIPv6()))
                .build();

            MongodStarter.getInstance(runtimeConfig).prepare(mongodConfig).start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Bean
    public MongoClient myDb() throws UnknownHostException {
        return new MongoClient("0.0.0.0", PORT);
    }
}