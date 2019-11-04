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
package org.apache.camel.example.debezium;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws.kinesis.KinesisConstants;
import org.apache.camel.component.debezium.DebeziumConstants;
import org.apache.camel.main.Main;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.kafka.connect.data.Struct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A simple example to consume data from Debezium and send it to Kinesis
 */
public final class DebeziumMySqlConsumerToKinesis {

    private static final Logger LOG = LoggerFactory.getLogger(DebeziumMySqlConsumerToKinesis.class);

    // use Camel Main to setup and run Camel
    private static Main main = new Main();

    private DebeziumMySqlConsumerToKinesis() {
    }

    public static void main(String[] args) throws Exception {

        LOG.debug("About to run Debezium integration...");

        // add route
        main.addRoutesBuilder(new RouteBuilder() {
            public void configure() {
                // Initial Debezium route that will run and listens to the changes,
                // first it will perform an initial snapshot using (select * from) in case there are no offsets
                // exists for the connector and then it will listens to MySQL binlogs for any DB events such as (UPDATE, INSERT and DELETE)
                from("debezium-mysql:{{debezium.mysql.name}}?"
                        + "databaseServerId={{debezium.mysql.databaseServerId}}"
                        + "&databaseHostname={{debezium.mysql.databaseHostName}}"
                        + "&databaseUser={{debezium.mysql.databaseUser}}"
                        + "&databasePassword={{debezium.mysql.databasePassword}}"
                        + "&databaseServerName={{debezium.mysql.databaseServerName}}"
                        + "&databaseHistoryFileFilename={{debezium.mysql.databaseHistoryFileName}}"
                        + "&databaseWhitelist={{debezium.mysql.databaseWhitelist}}"
                        + "&tableWhitelist={{debezium.mysql.tableWhitelist}}"
                        + "&offsetStorageFileName={{debezium.mysql.offsetStorageFileName}}")
                        .routeId("FromDebeziumMySql")
                        // We will need to prepare the data for Kinesis, however we need to mention here is that Kinesis is bit different from Kafka in terms
                        // of the key partition which only limited to 256 byte length, depending on the size of your key, that may not be optimal. Therefore, the safer option is to hash the key
                        // and convert it to string, but that means we need to preserve the key information into the message body in order not to lose these information downstream.
                        // Note: If you'd use Kafka, most probably you will not need these transformations as you can send the key as an object and Kafka will do
                        // the rest to hash it in the broker in order to place it in the correct topic's partition.
                        .setBody(exchange -> {
                            // Using Camel Data Format, we can retrieve our data in Map since Debezium component has a Type Converter from Struct to Map, you need to specify the Map.class
                            // in order to convert the data from Struct to Map
                            final Map key = exchange.getMessage().getHeader(DebeziumConstants.HEADER_KEY, Map.class);
                            final Map value = exchange.getMessage().getBody(Map.class);
                            // Also, we need the operation in order to determine when an INSERT, UPDATE or DELETE happens
                            final String operation = (String) exchange.getMessage().getHeader(DebeziumConstants.HEADER_OPERATION);
                            // We we will put everything as nested Map in order to utilize Camel's Type Format
                            final Map<String, Object> kinesisBody = new HashMap<>();

                            kinesisBody.put("key", key);
                            kinesisBody.put("value", value);
                            kinesisBody.put("operation", operation);

                            return kinesisBody;
                        })
                        // As we mentioned above, we will need to hash the key partition and set it into the headers
                        .process(exchange -> {
                            final Struct key = (Struct) exchange.getMessage().getHeader(DebeziumConstants.HEADER_KEY);
                            final String hash = String.valueOf(key.hashCode());

                            exchange.getMessage().setHeader(KinesisConstants.PARTITION_KEY, hash);
                        })
                        // Marshal everything to JSON, you can use any other data format such as Avro, Protobuf..etc, but in this example we will keep it to JSON for simplicity
                        .marshal().json(JsonLibrary.Jackson)
                        // Send our data to kinesis
                        .to("aws-kinesis:{{kinesis.streamName}}?accessKey=RAW({{kinesis.accessKey}})"
                                + "&secretKey=RAW({{kinesis.secretKey}})"
                                + "&region={{kinesis.region}}")
                        .end();
            }
        });

        // start and run Camel (block)
        main.run();
    }

}
