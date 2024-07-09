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
package org.apache.camel.component.kafka;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Route;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.kafka.shaded.io.opentelemetry.proto.collector.logs.v1.LogsServiceGrpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DevConsole(name = "kafka", displayName = "Kafka", description = "Apache Kafka")
public class KafkaDevConsole extends AbstractDevConsole {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaDevConsole.class);

    private static final long COMMITTED_TIMEOUT = 10000;

    /**
     * Whether to include committed offset (sync operation to Kafka broker)
     */
    public static final String COMMITTED = "committed";

    public KafkaDevConsole() {
        super("camel", "kafka", "Kafka", "Apache Kafka");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        final boolean committed = "true".equals(options.getOrDefault(COMMITTED, "false"));

        StringBuilder sb = new StringBuilder();
        for (Route route : getCamelContext().getRoutes()) {
            if (route.getConsumer() instanceof KafkaConsumer kc) {
                sb.append(String.format("\n    Route Id: %s", route.getRouteId()));
                sb.append(String.format("\n    From: %s", route.getEndpoint().getEndpointUri()));
                for (KafkaFetchRecords t : kc.tasks()) {
                    sb.append(String.format("\n        Worked Thread: %s", t.getThreadId()));
                    sb.append(String.format("\n        Worker State: %s", t.getState()));
                    if (t.getLastError() != null) {
                        sb.append(String.format("\n        Worker Last Error: %s", t.getLastError().getMessage()));
                    }
                    KafkaFetchRecords.GroupMetadata meta = t.getGroupMetadata();
                    if (meta != null) {
                        sb.append(String.format("\n        Group Id: %s", meta.groupId()));
                        sb.append(String.format("\n        Group Instance Id: %s", meta.groupInstanceId()));
                        sb.append(String.format("\n        Member Id: %s", meta.memberId()));
                        sb.append(String.format("\n        Generation Id: %d", meta.generationId()));
                    }
                    if (t.getLastRecord() != null) {
                        sb.append(String.format("\n        Last Topic: %s", t.getLastRecord().topic()));
                        sb.append(String.format("\n        Last Partition: %d", t.getLastRecord().partition()));
                        sb.append(String.format("\n        Last Offset: %d", t.getLastRecord().offset()));
                    }
                    if (committed) {
                        List<KafkaFetchRecords.KafkaTopicPosition> l = fetchCommitOffsets(kc, t);
                        if (l != null) {
                            for (KafkaFetchRecords.KafkaTopicPosition r : l) {
                                sb.append(String.format("\n        Commit Topic: %s", r.topic()));
                                sb.append(String.format("\n        Commit Partition: %s", r.partition()));
                                sb.append(String.format("\n        Commit Offset: %s", r.offset()));
                            }
                        }
                    }
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    private static List<KafkaFetchRecords.KafkaTopicPosition> fetchCommitOffsets(KafkaConsumer kc, KafkaFetchRecords task) {
        StopWatch watch = new StopWatch();

        CountDownLatch latch = task.fetchCommitRecords();
        long timeout = Math.min(kc.getEndpoint().getConfiguration().getPollTimeoutMs(), COMMITTED_TIMEOUT);
        try {
            latch.await(timeout, TimeUnit.MILLISECONDS);
            var answer = task.getCommitRecords();
            LOG.info("Fetching commit offsets took: {} ms", watch.taken());
            return answer;
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        final boolean committed = "true".equals(options.getOrDefault(COMMITTED, "false"));

        JsonObject root = new JsonObject();

        List<JsonObject> list = new ArrayList<>();
        root.put("kafkaConsumers", list);

        for (Route route : getCamelContext().getRoutes()) {
            if (route.getConsumer() instanceof KafkaConsumer kc) {
                JsonObject jo = new JsonObject();
                jo.put("routeId", route.getRouteId());
                jo.put("uri", route.getEndpoint().getEndpointUri());

                JsonArray arr = new JsonArray();
                jo.put("workers", arr);

                for (KafkaFetchRecords t : kc.tasks()) {
                    JsonObject wo = new JsonObject();
                    arr.add(wo);
                    wo.put("threadId", t.getThreadId());
                    wo.put("state", t.getState());
                    if (t.getLastError() != null) {
                        wo.put("lastError", t.getLastError().getMessage());
                    }
                    KafkaFetchRecords.GroupMetadata meta = t.getGroupMetadata();
                    if (meta != null) {
                        wo.put("groupId", meta.groupId());
                        wo.put("groupInstanceId", meta.groupInstanceId());
                        wo.put("memberId", meta.memberId());
                        wo.put("generationId", meta.generationId());
                    }
                    if (t.getLastRecord() != null) {
                        wo.put("lastTopic", t.getLastRecord().topic());
                        wo.put("lastPartition", t.getLastRecord().partition());
                        wo.put("lastOffset", t.getLastRecord().offset());
                    }
                    if (committed) {
                        List<KafkaFetchRecords.KafkaTopicPosition> l = fetchCommitOffsets(kc, t);
                        if (l != null) {
                            JsonArray ca = new JsonArray();
                            for (KafkaFetchRecords.KafkaTopicPosition r : l) {
                                JsonObject cr = new JsonObject();
                                cr.put("topic", r.topic());
                                cr.put("partition", r.partition());
                                cr.put("offset", r.offset());
                                ca.add(cr);
                            }
                            if (!ca.isEmpty()) {
                                wo.put("committed", ca);
                            }
                        }
                    }
                }
                list.add(jo);
            }
        }
        return root;
    }

}
