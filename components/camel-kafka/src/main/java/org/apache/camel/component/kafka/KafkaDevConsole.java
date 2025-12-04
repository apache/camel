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
import org.apache.camel.component.kafka.consumer.devconsole.DefaultMetricsCollector;
import org.apache.camel.component.kafka.consumer.devconsole.DevConsoleMetricsCollector;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
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
                    final DevConsoleMetricsCollector metricsCollector = t.getMetricsCollector();
                    sb.append(String.format("\n        Worked Thread: %s", metricsCollector.getThreadId()));
                    sb.append(String.format("\n        Worker State: %s", t.getState()));
                    TaskHealthState hs = t.healthState();
                    if (!hs.isReady()) {
                        sb.append(String.format("\n        Worker Last Error: %s", hs.buildStateMessage()));
                    }
                    DefaultMetricsCollector.GroupMetadata meta = metricsCollector.getGroupMetadata();
                    if (meta != null) {
                        sb.append(String.format("\n        Group Id: %s", meta.groupId()));
                        sb.append(String.format("\n        Group Instance Id: %s", meta.groupInstanceId()));
                        sb.append(String.format("\n        Member Id: %s", meta.memberId()));
                        sb.append(String.format("\n        Generation Id: %d", meta.generationId()));
                    }
                    if (metricsCollector.getLastRecord() != null) {
                        sb.append(String.format(
                                "\n        Last Topic: %s",
                                metricsCollector.getLastRecord().topic()));
                        sb.append(String.format(
                                "\n        Last Partition: %d",
                                metricsCollector.getLastRecord().partition()));
                        sb.append(String.format(
                                "\n        Last Offset: %d",
                                metricsCollector.getLastRecord().offset()));
                    }
                    if (committed) {
                        List<DefaultMetricsCollector.KafkaTopicPosition> l = fetchCommitOffsets(kc, metricsCollector);
                        if (l != null) {
                            for (DefaultMetricsCollector.KafkaTopicPosition r : l) {
                                sb.append(String.format("\n        Commit Topic: %s", r.topic()));
                                sb.append(String.format("\n        Commit Partition: %s", r.partition()));
                                sb.append(String.format("\n        Commit Offset: %s", r.offset()));
                                if (r.epoch() > 0) {
                                    long delta = System.currentTimeMillis() - r.epoch();
                                    sb.append(String.format(
                                            "\n        Commit Offset Since: %s", TimeUtils.printDuration(delta, true)));
                                }
                            }
                        }
                    }
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    private static List<DefaultMetricsCollector.KafkaTopicPosition> fetchCommitOffsets(
            KafkaConsumer kc, DevConsoleMetricsCollector collector) {
        StopWatch watch = new StopWatch();

        CountDownLatch latch = collector.fetchCommitRecords();
        long timeout = Math.min(kc.getEndpoint().getConfiguration().getPollTimeoutMs(), COMMITTED_TIMEOUT);
        try {
            latch.await(timeout, TimeUnit.MILLISECONDS);
            var answer = collector.getCommitRecords();
            LOG.debug("Fetching commit offsets took: {} ms", watch.taken());
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
                    final DevConsoleMetricsCollector metricsCollector = t.getMetricsCollector();

                    JsonObject wo = new JsonObject();
                    arr.add(wo);
                    wo.put("threadId", metricsCollector.getThreadId());
                    wo.put("state", t.getState());
                    TaskHealthState hs = t.healthState();
                    if (!hs.isReady()) {
                        wo.put("lastError", hs.buildStateMessage());
                    }
                    DefaultMetricsCollector.GroupMetadata meta = metricsCollector.getGroupMetadata();
                    if (meta != null) {
                        wo.put("groupId", meta.groupId());
                        wo.put("groupInstanceId", meta.groupInstanceId());
                        wo.put("memberId", meta.memberId());
                        wo.put("generationId", meta.generationId());
                    }
                    if (metricsCollector.getLastRecord() != null) {
                        wo.put("lastTopic", metricsCollector.getLastRecord().topic());
                        wo.put("lastPartition", metricsCollector.getLastRecord().partition());
                        wo.put("lastOffset", metricsCollector.getLastRecord().offset());
                    }
                    if (committed) {
                        List<DefaultMetricsCollector.KafkaTopicPosition> l = fetchCommitOffsets(kc, metricsCollector);
                        if (l != null) {
                            JsonArray ca = new JsonArray();
                            for (DefaultMetricsCollector.KafkaTopicPosition r : l) {
                                JsonObject cr = new JsonObject();
                                cr.put("topic", r.topic());
                                cr.put("partition", r.partition());
                                cr.put("offset", r.offset());
                                cr.put("epoch", r.epoch());
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
