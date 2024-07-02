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
package org.apache.camel.updates.camel44;

import org.apache.camel.updates.CamelTestUtil;
import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.xml.Assertions.xml;

public class CamelUpdate43Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        CamelTestUtil.recipe(spec, CamelTestUtil.CamelVersion.v4_4)
                .parser(CamelTestUtil.parserFromClasspath(CamelTestUtil.CamelVersion.v4_0, "camel-api",
                        "camel-core-model", "camel-support", "camel-base-engine", "camel-endpointdsl", "camel-kafka"))
                .typeValidationOptions(TypeValidation.none());
    }

    /**
     * <p>
     * Moved class org.apache.camel.impl.engine.MemoryStateRepository from camel-base-engine to
     * org.apache.camel.support.processor.state.MemoryStateRepository in camel-support.
     * </p>
     *
     * <p>
     * </p>
     * Moved class org.apache.camel.impl.engine.FileStateRepository from camel-base-engine to
     * org.apache.camel.support.processor.state.FileStateRepository in camel-support.
     * </p>
     *
     * <p>
     * See the <a href=https://camel.apache.org/manual/camel-4x-upgrade-guide-4_3.html#_camel_core>documentation</a>
     * </p>
     */
    @Test
    void testStateRepository() {
        //language=java
        rewriteRun(java("""
                    import org.apache.camel.BindToRegistry;
                    import org.apache.camel.impl.engine.FileStateRepository;
                    import org.apache.camel.impl.engine.MemoryStateRepository;

                    import java.io.File;

                    public class CoreTest {

                        @BindToRegistry("stateRepository")
                        private static final MemoryStateRepository stateRepository = new MemoryStateRepository();

                        // Create the repository in which the Kafka offsets will be persisted
                        FileStateRepository repository = FileStateRepository.fileStateRepository(new File("/path/to/repo.dat"));
                    }
                """,
                """
                        import org.apache.camel.BindToRegistry;
                        import org.apache.camel.support.processor.state.FileStateRepository;
                        import org.apache.camel.support.processor.state.MemoryStateRepository;

                        import java.io.File;

                        public class CoreTest {

                            @BindToRegistry("stateRepository")
                            private static final MemoryStateRepository stateRepository = new MemoryStateRepository();

                            // Create the repository in which the Kafka offsets will be persisted
                            FileStateRepository repository = FileStateRepository.fileStateRepository(new File("/path/to/repo.dat"));
                        }
                            """));
    }

    /**
     * <p>
     * The configuration for batch and stream has been renamed from batch-config to batchConfig and stream-config to
     * streamConfig.
     * </p>
     *
     * <p>
     * For example before:
     *
     * <pre>
     *     &lt;resequence&gt;
     *         &lt;stream-config timeout=&quot;1000&quot; deliveryAttemptInterval=&quot;10&quot;/&gt;
     *         &lt;simple&gt;${header.seqnum}&lt;/simple&gt;
     *         &lt;to uri=&quot;mock:result&quot; /&gt;
     *     &lt;/resequence&gt;
     * </pre>
     * </p>
     *
     * <p>
     * And now after:
     *
     * <pre>
     *     &lt;resequence&gt;
     *         &lt;streamConfig timeout=&quot;1000&quot; deliveryAttemptInterval=&quot;10&quot;/&gt;
     *         &lt;simple&gt;${header.seqnum}&lt;/simple&gt;
     *         &lt;to uri=&quot;mock:result&quot; /&gt;
     *     &lt;/resequence&gt;
     * </pre>
     * </p>
     *
     * <p>
     * See the <a href=https://camel.apache.org/manual/camel-4x-upgrade-guide-4_3.html#_resequence_eip>documentation</a>
     * </p>
     */
    @Test
    void testResequenceStramConfig() {
        //language=xml
        rewriteRun(xml("""
                <routes>
                    <route>
                        <from uri="direct:start"/>
                        <resequence>
                            <stream-config timeout="1000" deliveryAttemptInterval="10"/>
                            <simple>${header.seqnum}</simple>
                            <to uri="mock:result" />
                        </resequence>
                    </route>
                </routes>
                                            """, """
                    <routes>
                        <route>
                            <from uri="direct:start"/>
                            <resequence>
                                <streamConfig timeout="1000" deliveryAttemptInterval="10"/>
                                <simple>${header.seqnum}</simple>
                                <to uri="mock:result" />
                            </resequence>
                        </route>
                    </routes>
                """));
    }

    /**
     * <p>
     * The configuration for batch and stream has been renamed from batch-config to batchConfig and stream-config to
     * streamConfig.
     * </p>
     * <p>
     * See the <a href=https://camel.apache.org/manual/camel-4x-upgrade-guide-4_3.html#_resequence_eip>documentation</a>
     * </p>
     */
    @Test
    void testResequenceBatchConfig() {
        //language=xml
        rewriteRun(xml("""
                <camelContext id="camel" xmlns="http://camel.apache.org/schema/spring">
                    <route>
                        <from uri="direct:start" />
                        <resequence>
                            <simple>body</simple>
                            <to uri="mock:result" />
                            <batch-config batchSize="300" batchTimeout="4000" />
                        </resequence>
                     </route>
                 </camelContext>
                                            """, """
                    <camelContext id="camel" xmlns="http://camel.apache.org/schema/spring">
                        <route>
                            <from uri="direct:start" />
                            <resequence>
                                <simple>body</simple>
                                <to uri="mock:result" />
                                <batchConfig batchSize="300" batchTimeout="4000" />
                            </resequence>
                         </route>
                     </camelContext>
                """));
    }

    /**
     * <p>
     * Throttle now uses the number of concurrent requests as the throttling measure instead of the number of requests
     * per period.
     * </p>
     *
     * <p>
     * Update throttle expressions configured with maxRequestsPerPeriod to use maxConcurrentRequests instead, and remove
     * any timePeriodMillis option.
     * </p>
     *
     * <p>
     * See the <a
     * href="https://camel.apache.org/manual/camel-4x-upgrade-guide-4_3.html#_throttle_eip"'>documentation</a>
     * </p>
     */
    @Test
    void testThrottleEIP() {
        //language=java
        rewriteRun(java("""
                    import org.apache.camel.builder.RouteBuilder;

                    public class ThrottleEIPTest extends RouteBuilder {
                        @Override
                        public void configure() {
                            long maxRequestsPerPeriod = 100L;
                            Long maxRequests = Long.valueOf(maxRequestsPerPeriod);

                            from("seda:a")
                                    .throttle(maxRequestsPerPeriod).timePeriodMillis(500).asyncDelayed()
                                    .to("seda:b");

                            from("seda:a")
                                    .throttle(maxRequestsPerPeriod).timePeriodMillis(500)
                                    .to("seda:b");

                            from("seda:c")
                                    .throttle(maxRequestsPerPeriod)
                                    .to("seda:d");

                            from("seda:a")
                                    .throttle(maxRequests).timePeriodMillis(500).asyncDelayed()
                                    .to("seda:b");

                            from("seda:a")
                                    .throttle(maxRequests).timePeriodMillis(500)
                                    .to("seda:b");

                            from("seda:c")
                                    .throttle(maxRequests)
                                    .to("seda:d");
                        }
                    }
                """,
                """
                        import org.apache.camel.builder.RouteBuilder;

                        public class ThrottleEIPTest extends RouteBuilder {
                            @Override
                            public void configure() {
                                long maxRequestsPerPeriod = 100L;
                                Long maxRequests = Long.valueOf(maxRequestsPerPeriod);

                                /* Throttle now uses the number of concurrent requests as the throttling measure instead of the number of requests per period.*/from("seda:a")
                                        .throttle(maxRequestsPerPeriod).asyncDelayed()
                                        .to("seda:b");

                                /* Throttle now uses the number of concurrent requests as the throttling measure instead of the number of requests per period.*/from("seda:a")
                                        .throttle(maxRequestsPerPeriod)
                                        .to("seda:b");

                                /* Throttle now uses the number of concurrent requests as the throttling measure instead of the number of requests per period.*/from("seda:c")
                                        .throttle(maxRequestsPerPeriod)
                                        .to("seda:d");

                                /* Throttle now uses the number of concurrent requests as the throttling measure instead of the number of requests per period.*/from("seda:a")
                                        .throttle(maxRequests).asyncDelayed()
                                        .to("seda:b");

                                /* Throttle now uses the number of concurrent requests as the throttling measure instead of the number of requests per period.*/from("seda:a")
                                        .throttle(maxRequests)
                                        .to("seda:b");

                                /* Throttle now uses the number of concurrent requests as the throttling measure instead of the number of requests per period.*/from("seda:c")
                                        .throttle(maxRequests)
                                        .to("seda:d");
                            }
                        }
                                """));
    }

    /**
     * <p>
     * The header name for the List<RecordMetadata> metadata has changed from
     * org.apache.kafka.clients.producer.RecordMetadata to kafka.RECORD_META, and the header constant from
     * KAFKA_RECORDMETA to KAFKA_RECORD_META.
     * </p>
     *
     * <p>
     * See the <a
     * href="https://camel.apache.org/manual/camel-4x-upgrade-guide-4_3.html#_camel_kafka_2"'>documentation</a>
     * </p>
     */
    @Test
    void testKafka() {
        //language=java
        rewriteRun(java(
                """
                            import org.apache.camel.CamelContext;
                            import org.apache.camel.Message;
                            import org.apache.camel.builder.endpoint.EndpointRouteBuilder;
                            import org.apache.camel.component.kafka.KafkaConstants;
                            import org.apache.kafka.clients.producer.RecordMetadata;
                            import org.apache.camel.support.DefaultMessage;

                            import java.util.List;

                            public class KafkaTest extends EndpointRouteBuilder {
                                private CamelContext context;
                                private final Message in = new DefaultMessage(context);

                                @Override
                                public void configure() throws Exception {

                                    List<RecordMetadata> recordMetaData1 = (List<RecordMetadata>) in.getHeader(KafkaConstants.KAFKA_RECORDMETA);


                                    from(kafka().orgApacheKafkaClientsProducerRecordmetadata()).to(mock("test"));
                                }
                            }
                        """,
                """
                        import org.apache.camel.CamelContext;
                        import org.apache.camel.Message;
                        import org.apache.camel.builder.endpoint.EndpointRouteBuilder;
                        import org.apache.camel.component.kafka.KafkaConstants;
                        import org.apache.kafka.clients.producer.RecordMetadata;
                        import org.apache.camel.support.DefaultMessage;

                        import java.util.List;

                        public class KafkaTest extends EndpointRouteBuilder {
                            private CamelContext context;
                            private final Message in = new DefaultMessage(context);

                            @Override
                            public void configure() throws Exception {

                                List<RecordMetadata> recordMetaData1 = (List<RecordMetadata>) in.getHeader(KafkaConstants.KAFKA_RECORD_META);


                                from(kafka().kafkaRecordMeta()).to(mock("test"));
                            }
                        }
                            """));
    }
}
