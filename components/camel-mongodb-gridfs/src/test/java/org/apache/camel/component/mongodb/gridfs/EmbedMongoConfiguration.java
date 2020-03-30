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
package org.apache.camel.component.mongodb.gridfs;

import java.io.IOException;

import com.mongodb.MongoClient;
import de.flapdoodle.embed.mongo.Command;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.IMongodConfig;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.config.RuntimeConfigBuilder;
import de.flapdoodle.embed.process.config.IRuntimeConfig;
import org.apache.camel.test.AvailablePortFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.mongodb.ServerAddress.defaultHost;
import static de.flapdoodle.embed.mongo.distribution.Version.Main.PRODUCTION;
import static de.flapdoodle.embed.process.runtime.Network.localhostIsIPv6;

public final class EmbedMongoConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(EmbedMongoConfiguration.class);
    private static final int PORT = AvailablePortFinder.getNextAvailable(18500, 19000);
 
    private EmbedMongoConfiguration() {
    }

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

    public static MongoClient createMongoClient()  {
        return new MongoClient(defaultHost(), PORT);
    }
}
