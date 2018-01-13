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
package org.apache.camel.component.mongodb;

import java.io.IOException;
import java.net.UnknownHostException;

import com.mongodb.MongoClient;
import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.IMongodConfig;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.mongodb.ServerAddress.defaultHost;
import static de.flapdoodle.embed.mongo.distribution.Version.Main.PRODUCTION;
import static de.flapdoodle.embed.process.runtime.Network.localhostIsIPv6;
import static org.springframework.util.SocketUtils.findAvailableTcpPort;

@Configuration
public class EmbedMongoConfiguration {

    private static final int PORT = findAvailableTcpPort();

    static {
        try {
            IMongodConfig mongodConfig = new MongodConfigBuilder()
                    .version(PRODUCTION)
                    .net(new Net(PORT, localhostIsIPv6()))
                    .build();
            MongodExecutable mongodExecutable = MongodStarter.getDefaultInstance().prepare(mongodConfig);
            mongodExecutable.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Bean
    public MongoClient myDb() throws UnknownHostException {
        return new MongoClient(defaultHost(), PORT);
    }

}