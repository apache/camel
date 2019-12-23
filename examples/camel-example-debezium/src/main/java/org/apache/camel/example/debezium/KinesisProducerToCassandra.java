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

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.main.Main;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A simple example to sink data from Kinesis that produced by Debezium into Cassandra
 */
public final class KinesisProducerToCassandra {

    private static final Logger LOG = LoggerFactory.getLogger(KinesisProducerToCassandra.class);

    // use Camel Main to setup and run Camel
    private static Main main = new Main();

    private KinesisProducerToCassandra() {
    }

    public static void main(String[] args) throws Exception {

        LOG.debug("About to run Kinesis to Cassandra integration...");

        // add route
        main.addRouteBuilder(new RouteBuilder() {
            public void configure() {
                // We set the CQL templates we need, note that an UPDATE in Cassandra means an UPSERT which is what we need
                final String cqlUpdate = "update products set name = ?, description = ?, weight = ? where id = ?";
                final String cqlDelete = "delete from products where id = ?";

                from("aws-kinesis:{{kinesis.streamName}}?accessKey=RAW({{kinesis.accessKey}})"
                        + "&secretKey=RAW({{kinesis.secretKey}})"
                        + "&region={{kinesis.region}}")
                        // Since we expect the data of the body to be ByteArr, we convert it to String using Kinesis
                        // Type Converter, in order to unmarshal later from JSON to Map
                        .convertBodyTo(String.class)
                        // Unmarshal our body, it will convert it from JSON to Map
                        .unmarshal().json(JsonLibrary.Jackson)
                        // In order not to lose the operation that we set in Debezium, we set it as a property or you can as
                        // as well set it to a header
                        .setProperty("DBOperation", simple("${body[operation]}"))
                        .choice()
                            // If we have a INSERT or UPDATE, we will need to set the body with with the CQL query parameters since we are using
                            // camel-cassandraql component
                            .when(exchangeProperty("DBOperation").in("c", "u"))
                                .setBody(exchange -> {
                                    final Map body = (Map) exchange.getMessage().getBody();
                                    final Map value = (Map) body.get("value");
                                    final Map key = (Map) body.get("key");

                                    // We as well check for nulls
                                    final String name = value.get("name") != null ? value.get("name").toString() : "";
                                    final String description = value.get("description") != null ? value.get("description").toString() : "";
                                    final float weight = value.get("weight") != null ? Float.parseFloat(value.get("weight").toString()) : 0;

                                    return Arrays.asList(name, description, weight, key.get("id"));
                                })
                                // We set the appropriate query in the header so we don't run the same route twice
                                .setHeader("CQLQuery", constant(cqlUpdate))
                            // If we have a DELETE, then we just set the id as a query parameter in the body
                            .when(exchangeProperty("DBOperation").isEqualTo("d"))
                                .setBody(exchange -> {
                                    final Map body = (Map) exchange.getMessage().getBody();
                                    final Map key = (Map) body.get("key");

                                    return Collections.singletonList(key.get("id"));
                                })
                                // We set the appropriate query in the header so we don't run the same route twice
                                .setHeader("CQLQuery", constant(cqlDelete))
                        .end()
                        .choice()
                            // We just make sure we ONLY handle INSERT, UPDATE and DELETE and nothing else
                            .when(exchangeProperty("DBOperation").in("c", "u", "d"))
                                // Send query to Cassandra
                                .recipientList(simple("cql:{{cassandra.host}}/{{cassandra.keyspace}}?cql=RAW(${header.CQLQuery})"))
                        .end();
            }
        });

        // start and run Camel (block)
        main.run();
    }

}
