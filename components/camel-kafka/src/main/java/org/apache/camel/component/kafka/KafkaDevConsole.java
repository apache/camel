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
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonRecordSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DevConsole(name = "kafka", displayName = "Kafka", description = "Apache Kafka")
public class KafkaDevConsole extends AbstractDevConsole {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaDevConsole.class);

    private static final long COMMITTED_TIMEOUT = 10000;

    @Metadata(label = "query", description = "Whether to include committed offset (sync operation to Kafka broker)",
              defaultValue = "false", javaType = "java.lang.Boolean")
    public static final String COMMITTED = "committed";

    public record CommittedEntry(
            @Metadata(description = "The topic") String topic,
            @Metadata(description = "The partition") int partition,
            @Metadata(description = "The committed offset") long offset,
            @Metadata(description = "The epoch") int epoch) {
    }

    public record WorkerEntry(
            @Metadata(description = "The worker thread id") String threadId,
            @Metadata(description = "The worker state") String state,
            @Metadata(description = "The worker last error (only present when not ready)") String lastError,
            @Metadata(description = "The consumer group id (only present when known)") String groupId,
            @Metadata(description = "The consumer group instance id (only present when known)") String groupInstanceId,
            @Metadata(description = "The consumer member id (only present when known)") String memberId,
            @Metadata(description = "The consumer generation id (only present when known)") Integer generationId,
            @Metadata(description = "The last consumed topic (only present when known)") String lastTopic,
            @Metadata(description = "The last consumed partition (only present when known)") Integer lastPartition,
            @Metadata(description = "The last consumed offset (only present when known)") Long lastOffset,
            @Metadata(description = "The committed offsets (only present when requested and there are any)") List<CommittedEntry> committed) {
    }

    public record ConsumerEntry(
            @Metadata(description = "The route id") String routeId,
            @Metadata(description = "The endpoint URI") String uri,
            @Metadata(description = "The consumer worker threads") List<WorkerEntry> workers) {
    }

    public record Response(@Metadata(description = "The Kafka consumers") List<ConsumerEntry> kafkaConsumers) {
    }

    public KafkaDevConsole() {
        super("camel", "kafka", "Kafka", "Apache Kafka");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        final boolean committed = optionBoolean(options, COMMITTED, false);

        StringBuilder sb = new StringBuilder();
        for (Route route : getCamelContext().getRoutes()) {
            if (route.getConsumer() instanceof KafkaConsumer kc) {
                sb.append(String.format("%n    Route Id: %s", route.getRouteId()));
                sb.append(String.format("%n    From: %s", route.getEndpoint().getEndpointUri()));
                for (KafkaFetchRecords t : kc.tasks()) {
                    final DevConsoleMetricsCollector metricsCollector = t.getMetricsCollector();
                    sb.append(String.format("%n        Worked Thread: %s", metricsCollector.getThreadId()));
                    sb.append(String.format("%n        Worker State: %s", t.getState()));
                    TaskHealthState hs = t.healthState();
                    if (!hs.isReady()) {
                        sb.append(String.format("%n        Worker Last Error: %s", hs.buildStateMessage()));
                    }
                    DefaultMetricsCollector.GroupMetadata meta = metricsCollector.getGroupMetadata();
                    if (meta != null) {
                        sb.append(String.format("%n        Group Id: %s", meta.groupId()));
                        sb.append(String.format("%n        Group Instance Id: %s", meta.groupInstanceId()));
                        sb.append(String.format("%n        Member Id: %s", meta.memberId()));
                        sb.append(String.format("%n        Generation Id: %d", meta.generationId()));
                    }
                    if (metricsCollector.getLastRecord() != null) {
                        sb.append(String.format("%n        Last Topic: %s", metricsCollector.getLastRecord().topic()));
                        sb.append(String.format("%n        Last Partition: %d", metricsCollector.getLastRecord().partition()));
                        sb.append(String.format("%n        Last Offset: %d", metricsCollector.getLastRecord().offset()));
                    }
                    if (committed) {
                        List<DefaultMetricsCollector.KafkaTopicPosition> l = fetchCommitOffsets(kc, metricsCollector);
                        if (l != null) {
                            for (DefaultMetricsCollector.KafkaTopicPosition r : l) {
                                sb.append(String.format("%n        Commit Topic: %s", r.topic()));
                                sb.append(String.format("%n        Commit Partition: %s", r.partition()));
                                sb.append(String.format("%n        Commit Offset: %s", r.offset()));
                                if (r.epoch() > 0) {
                                    long delta = System.currentTimeMillis() - r.epoch();
                                    sb.append(String.format("%n        Commit Offset Since: %s",
                                            TimeUtils.printDuration(delta, true)));
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
        final boolean committed = optionBoolean(options, COMMITTED, false);

        List<ConsumerEntry> list = new ArrayList<>();

        for (Route route : getCamelContext().getRoutes()) {
            if (route.getConsumer() instanceof KafkaConsumer kc) {
                List<WorkerEntry> workers = new ArrayList<>();

                for (KafkaFetchRecords t : kc.tasks()) {
                    final DevConsoleMetricsCollector metricsCollector = t.getMetricsCollector();

                    String lastError = null;
                    TaskHealthState hs = t.healthState();
                    if (!hs.isReady()) {
                        lastError = hs.buildStateMessage();
                    }
                    String groupId = null;
                    String groupInstanceId = null;
                    String memberId = null;
                    Integer generationId = null;
                    DefaultMetricsCollector.GroupMetadata meta = metricsCollector.getGroupMetadata();
                    if (meta != null) {
                        groupId = meta.groupId();
                        groupInstanceId = meta.groupInstanceId();
                        memberId = meta.memberId();
                        generationId = meta.generationId();
                    }
                    String lastTopic = null;
                    Integer lastPartition = null;
                    Long lastOffset = null;
                    if (metricsCollector.getLastRecord() != null) {
                        lastTopic = metricsCollector.getLastRecord().topic();
                        lastPartition = metricsCollector.getLastRecord().partition();
                        lastOffset = metricsCollector.getLastRecord().offset();
                    }
                    List<CommittedEntry> committedList = null;
                    if (committed) {
                        List<DefaultMetricsCollector.KafkaTopicPosition> l = fetchCommitOffsets(kc, metricsCollector);
                        if (l != null) {
                            List<CommittedEntry> ca = new ArrayList<>();
                            for (DefaultMetricsCollector.KafkaTopicPosition r : l) {
                                ca.add(new CommittedEntry(r.topic(), r.partition(), r.offset(), r.epoch()));
                            }
                            committedList = ca.isEmpty() ? null : ca;
                        }
                    }

                    workers.add(new WorkerEntry(
                            metricsCollector.getThreadId(), t.getState(), lastError, groupId, groupInstanceId,
                            memberId, generationId, lastTopic, lastPartition, lastOffset, committedList));
                }
                list.add(new ConsumerEntry(route.getRouteId(), route.getEndpoint().getEndpointUri(), workers));
            }
        }

        Response response = new Response(list);
        return JsonRecordSupport.toJsonObject(response);
    }

}
