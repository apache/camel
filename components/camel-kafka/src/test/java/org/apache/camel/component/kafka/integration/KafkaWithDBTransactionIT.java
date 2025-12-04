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

package org.apache.camel.component.kafka.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.core.annotations.ContextFixture;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

public class KafkaWithDBTransactionIT extends BaseKafkaTestSupport {

    private static final String TOPIC_TX_1 = "transaction-1";
    private static final String TOPIC_TX_2 = "transaction-2";
    private static final String TOPIC_TX_3 = "transaction-3";
    private static final String TOPIC_TX_4 = "transaction-4";
    private static final String TOPIC_TX_5 = "transaction-5";
    private static final String INSERT_SQL_1 = "insert into foo1(name) values (:#word)";
    private static final String INSERT_SQL_2 = "insert into foo2(name) values (:#word)";
    private static final String INSERT_SQL_3 = "insert into foo3(name) values (:#word)";
    private static final String INSERT_SQL_4 = "insert into foo4(name) values (:#word)";
    private static final String INSERT_SQL_5 = "insert into foo5(name) values (:#word)";
    private static KafkaConsumer<String, String> stringsConsumerConn;
    private static EmbeddedDatabase db;
    private static JdbcTemplate jdbc;
    protected volatile CamelContext context;

    @BeforeAll
    public static void before() {
        stringsConsumerConn = createStringKafkaConsumer("KafkaWithDBTransactionIT");
    }

    @ContextFixture
    public void configureContext(CamelContext context) {
        db = new EmbeddedDatabaseBuilder()
                .setName(getClass().getSimpleName())
                .setType(EmbeddedDatabaseType.H2)
                .build();
        context.getRegistry().bind("testdb", db);

        DataSourceTransactionManager txMgr = new DataSourceTransactionManager();
        txMgr.setDataSource(db);
        context.getRegistry().bind("txManager", txMgr);
        jdbc = new JdbcTemplate(db);
        jdbc.execute("create table foo1(id SERIAL PRIMARY KEY,name VARCHAR (10) NOT NULL unique);");
        jdbc.execute("create table foo2(id SERIAL PRIMARY KEY,name VARCHAR (10) NOT NULL unique);");
        jdbc.execute("create table foo3(id SERIAL PRIMARY KEY,name VARCHAR (10) NOT NULL unique);");
        jdbc.execute("create table foo4(id SERIAL PRIMARY KEY,name VARCHAR (10) NOT NULL unique);");
        jdbc.execute("create table foo5(id SERIAL PRIMARY KEY,name VARCHAR (10) NOT NULL unique);");
    }

    @AfterAll
    public static void after() {
        // clean all test topics
        final List<String> topics = new ArrayList<>();
        topics.add(TOPIC_TX_1);
        topics.add(TOPIC_TX_2);
        topics.add(TOPIC_TX_3);
        topics.add(TOPIC_TX_4);
        topics.add(TOPIC_TX_5);
        kafkaAdminClient.deleteTopics(topics);
        if (db != null) {
            db.shutdown();
        }
    }

    // No transaction - sends 1 message to kafka and 1 insert to the table
    @Test
    public void noTransactionProducerWithDBLast() throws Exception {
        contextExtension.getContext().addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:startNoTx").to("kafka:" + TOPIC_TX_1).to("sql:" + INSERT_SQL_1);
            }
        });

        ProducerTemplate producer = contextExtension.getProducerTemplate();
        String bodyContent = "foobar";
        producer.sendBodyAndHeader("direct:startNoTx", bodyContent, "word", bodyContent);

        ConsumerRecords<String, String> records = getMessagesFromTopic(stringsConsumerConn, TOPIC_TX_1);

        // verify kafka topic
        assertEquals(1, records.count());
        assertEquals(bodyContent, records.iterator().next().value());

        // verify sql content
        long count = jdbc.queryForObject("select count(*) from foo1 where name = '" + bodyContent + "'", Long.class);
        assertEquals(1, count);
    }

    /*
     * No transaction - sends two duplicate messages, for the second one the DB insert will fail, in the end there will
     * be 2 messages in the topic and 1 row in the table, because there is no transaction, there is no rollback of the
     * duplicated operation.
     */
    @Test
    public void noTransactionProducerDuplicatedWithDBLast() throws Exception {
        contextExtension.getContext().addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:startNoTx2")
                        .onException(Exception.class)
                        .handled(true)
                        .log("Expected error when trying to insert duplicate values in the unique column.")
                        .end()
                        .to("kafka:" + TOPIC_TX_2)
                        .to("sql:" + INSERT_SQL_2);
            }
        });

        ProducerTemplate producer = contextExtension.getProducerTemplate();
        String bodyContent = "foobar";
        producer.sendBodyAndHeader("direct:startNoTx2", bodyContent, "word", bodyContent);
        producer.sendBodyAndHeader("direct:startNoTx2", bodyContent, "word", bodyContent);

        ConsumerRecords<String, String> records = getMessagesFromTopic(stringsConsumerConn, TOPIC_TX_2);

        // verify kafka topic
        assertEquals(2, records.count());
        assertEquals(bodyContent, records.iterator().next().value());

        // verify sql content
        long count = jdbc.queryForObject("select count(*) from foo2 where name = '" + bodyContent + "'", Long.class);
        assertEquals(1, count);
    }

    /*
     * With transaction - sends two duplicate messages, for the second one the DB insert will fail and the rollback will
     * take place. in this case the SQL operation is the last endpoint after the message was sent to the kafka topic.
     */
    // @Test
    @ParameterizedTest
    @ValueSource(
            strings = {"transacted=true", "transactionalId=my-foo1", "additionalProperties[transactional.id]=my-foo2"})
    public void transactionProducerWithDBLast(String txParam) throws Exception {
        String startEndpoint = "direct:startTxDBLast";
        contextExtension.getContext().addRoutes(new RouteBuilder() {
            public void configure() {
                from(startEndpoint)
                        .routeId("tx-kafka-db-last")
                        .onException(Exception.class)
                        .handled(true)
                        .markRollbackOnly()
                        .end()
                        .to("kafka:" + TOPIC_TX_3 + "?" + txParam)
                        .to("sql:" + INSERT_SQL_3);
            }
        });

        ProducerTemplate producer = contextExtension.getProducerTemplate();
        String bodyContent = "foobar";
        producer.sendBodyAndHeader(startEndpoint, bodyContent, "word", bodyContent);
        producer.sendBodyAndHeader(startEndpoint, bodyContent, "word", bodyContent);

        ConsumerRecords<String, String> records = getMessagesFromTopic(stringsConsumerConn, TOPIC_TX_3);

        // verify kafka topic
        assertEquals(1, records.count());
        assertEquals(bodyContent, records.iterator().next().value());

        // verify sql content
        long count = jdbc.queryForObject("select count(*) from foo3 where name = '" + bodyContent + "'", Long.class);
        assertEquals(1, count);
    }

    /**
     * With transaction - Uses multiple kafka producers to send duplicate messages. One route with transacted=true and
     * the other with no transactions.
     */
    // @Test
    @Test
    public void transactionMultipleProducersWithDBLast() throws Exception {
        contextExtension.getContext().addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:startTxDBLast2a")
                        .onException(Exception.class)
                        .handled(true)
                        .markRollbackOnly()
                        .end()
                        .to("kafka:" + TOPIC_TX_5 + "?transacted=true")
                        .to("sql:" + INSERT_SQL_5);

                from("direct:startTxDBLast2b")
                        .onException(Exception.class)
                        .handled(true)
                        .markRollbackOnly()
                        .end()
                        .to("kafka:" + TOPIC_TX_5 + "?transacted=true")
                        .to("sql:" + INSERT_SQL_5);

                from("direct:startTxDBLast2c")
                        .onException(Exception.class)
                        .handled(true)
                        .markRollbackOnly()
                        .end()
                        .to("kafka:" + TOPIC_TX_5)
                        .to("sql:" + INSERT_SQL_5);
            }
        });

        ProducerTemplate producer = contextExtension.getProducerTemplate();
        String bodyContent1 = "foobar1";
        String bodyContent2 = "foobar2";
        String bodyContent3 = "foobar3";
        producer.sendBodyAndHeader("direct:startTxDBLast2a", bodyContent1, "word", bodyContent1);
        producer.sendBodyAndHeader("direct:startTxDBLast2a", bodyContent1, "word", bodyContent1);
        producer.sendBodyAndHeader("direct:startTxDBLast2b", bodyContent2, "word", bodyContent2);
        producer.sendBodyAndHeader("direct:startTxDBLast2b", bodyContent2, "word", bodyContent2);
        producer.sendBodyAndHeader("direct:startTxDBLast2c", bodyContent3, "word", bodyContent3);
        producer.sendBodyAndHeader("direct:startTxDBLast2c", bodyContent3, "word", bodyContent3);

        ConsumerRecords<String, String> records = getMessagesFromTopic(stringsConsumerConn, TOPIC_TX_5);

        // verify kafka topic
        Iterator<ConsumerRecord<String, String>> iter = records.iterator();
        assertEquals(4, records.count());
        assertEquals(bodyContent1, iter.next().value());
        assertEquals(bodyContent2, iter.next().value());
        assertEquals(bodyContent3, iter.next().value());
        assertEquals(bodyContent3, iter.next().value());

        // verify sql content
        long count = jdbc.queryForObject("select count(*) from foo5 where name like 'foobar%'", Long.class);
        assertEquals(3, count);
    }

    /**
     * With transaction - sends two duplicate messages, for the second one the DB insert will fail and the rollback will
     * take place. in this case the SQL operation is the first endpoint.
     */
    @ParameterizedTest
    @ValueSource(
            strings = {"transacted=true", "transactionalId=my-bar1", "additionalProperties[transactional.id]=my-bar2"})
    public void transactionProducerWithDBFirst(String txParam) throws Exception {
        String startEndpoint = "direct:startTxDBFirst";
        contextExtension.getContext().addRoutes(new RouteBuilder() {
            public void configure() {
                from(startEndpoint)
                        .routeId("tx-kafka-db-first")
                        .onException(Exception.class)
                        .handled(true)
                        .log("Expected error when trying to insert duplicate values in the unique column.")
                        .end()
                        .to("sql:" + INSERT_SQL_4)
                        .to("kafka:" + TOPIC_TX_4 + "?" + txParam);
            }
        });

        ProducerTemplate producer = contextExtension.getProducerTemplate();
        String bodyContent = "foobar";
        producer.sendBodyAndHeader(startEndpoint, bodyContent, "word", bodyContent);
        producer.sendBodyAndHeader(startEndpoint, bodyContent, "word", bodyContent);

        ConsumerRecords<String, String> records = getMessagesFromTopic(stringsConsumerConn, TOPIC_TX_4);

        // verify kafka topic
        assertEquals(1, records.count());
        assertEquals(bodyContent, records.iterator().next().value());

        // verify sql content
        long count = jdbc.queryForObject("select count(*) from foo4 where name = '" + bodyContent + "'", Long.class);
        assertEquals(1, count);
    }

    private ConsumerRecords<String, String> getMessagesFromTopic(
            KafkaConsumer<String, String> consumerConn, String topic) {
        consumerConn.subscribe(Arrays.asList(topic));
        ConsumerRecords<String, String> records = null;
        for (int i = 0; i < 5; i++) {
            records = consumerConn.poll(Duration.ofMillis(100));
            if (records.count() > 0) {
                break;
            }
        }
        consumerConn.unsubscribe();
        return records;
    }

    private static KafkaConsumer<String, String> createStringKafkaConsumer(final String groupId) {
        Properties stringsProps = new Properties();
        stringsProps.put(
                org.apache.kafka.clients.consumer.ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, getBootstrapServers());
        stringsProps.put(org.apache.kafka.clients.consumer.ConsumerConfig.GROUP_ID_CONFIG, groupId);
        stringsProps.put(org.apache.kafka.clients.consumer.ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        stringsProps.put(org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        stringsProps.put(org.apache.kafka.clients.consumer.ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "30000");
        stringsProps.put(
                org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringDeserializer");
        stringsProps.put(
                org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringDeserializer");
        stringsProps.put(org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        stringsProps.put(org.apache.kafka.clients.consumer.ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        return new KafkaConsumer<>(stringsProps);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // intentionally blank as we use the routes in each test
            }
        };
    }
}
