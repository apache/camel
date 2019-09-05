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
package org.apache.camel.example.kafka.avro;

import org.apache.camel.builder.RouteBuilder;

public class AvroRouteBuilder extends RouteBuilder {

    @Override
    public void configure() throws Exception {

        from("timer://foo?period={{period}}")
        .setBody(constant("Hi This is Avro example"))
        .process(new KafkaAvroMessageProcessor())
            .to("kafka:{{producer.topic}}?brokers={{kafka.bootstrap.url}}&keySerializerClass=org.apache.kafka.common.serialization.StringSerializer&serializerClass=org.apache.camel.example.kafka.avro.CustomKafkaAvroSerializer");

        from("timer://foo?period={{period}}")
        .from("kafka:{{consumer.topic}}?brokers={{kafka.bootstrap.url}}&keyDeserializer=org.apache.kafka.common.serialization.StringDeserializer&valueDeserializer=org.apache.camel.example.kafka.avro.CustomKafkaAvroDeserializer")
         .process(new KafkaAvroMessageConsumerProcessor())
            .log("${body}");
    }
}
