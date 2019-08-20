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
package org.apache.camel.component.aws.xray;

import java.lang.invoke.MethodHandles;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.camel.component.aws.xray.TestDataBuilder.TestEntity;
import org.apache.camel.component.aws.xray.TestDataBuilder.TestSegment;
import org.apache.camel.component.aws.xray.TestDataBuilder.TestSubsegment;
import org.apache.camel.component.aws.xray.TestDataBuilder.TestTrace;
import org.apache.camel.component.aws.xray.json.JsonArray;
import org.apache.camel.component.aws.xray.json.JsonObject;
import org.apache.camel.component.aws.xray.json.JsonParser;
import org.apache.commons.lang3.StringUtils;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FakeAWSDaemon extends ExternalResource {

    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private Map<String, TestTrace> receivedTraces = Collections.synchronizedMap(new LinkedHashMap<>());
    private UDPSocketListener socketListener = new UDPSocketListener(receivedTraces);
    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    protected void before() throws Throwable {
        LOG.info("Starting up Mock-AWS daemon");
        executorService.submit(socketListener);
    }

    @Override
    protected void after() {
        LOG.info("Shutting down Mock-AWS daemon");
        socketListener.close();
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
                if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    LOG.error("Could not terminate UDP server");
                }
            }
        } catch (InterruptedException iEx) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    Map<String, TestTrace> getReceivedData() {
        LOG.trace("List of received data packages requested: {}", receivedTraces.size());
        return receivedTraces;
    }

    private static final class UDPSocketListener implements Runnable {

        private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

        private DatagramSocket serverSocket;
        private Map<String, TestTrace> receivedTraces;
        private volatile boolean done;

        private UDPSocketListener(Map<String, TestTrace> receivedTraces) {
            this.receivedTraces = receivedTraces;
        }

        @Override
        public void run() {
            try {
                LOG.info("Starting UDP socket listening on port 2000");
                serverSocket = new DatagramSocket(2000);

                StringBuilder sb = new StringBuilder();
                while (!done) {
                    byte[] receiveData = new byte[2048];
                    DatagramPacket receivedPacket = new DatagramPacket(receiveData, receiveData.length);
                    serverSocket.receive(receivedPacket);

                    LOG.debug("Receiving UDP data");
                    sb.append(new String(receivedPacket.getData()));

                    String locSegment = null;
                    try {
                        String raw = sb.toString().trim();
                        String[] segments = raw.split("\\n");
                        for (String segment : segments) {
                            locSegment = segment;
                            LOG.trace("Processing received segment: {}", segment);
                            if (!"".equals(segment)) {
                                if (!segment.endsWith("}")
                                        || StringUtils.countMatches(segment, "{") != StringUtils.countMatches(segment, "}")
                                        || StringUtils.countMatches(segment, "[") != StringUtils.countMatches(segment, "]")) {
                                    LOG.trace("Skipping incomplete content: {}", segment);
                                    continue;
                                }
                                if (segment.contains("format") && segment.contains("version")) {
                                    LOG.trace("Skipping format and version JSON");
                                } else {
                                    LOG.trace("Converting segment {} to a Java object", segment);
                                    // clean the JSON string received
                                    LOG.trace("Original JSON content: {}", segment);
                                    locSegment = segment;
                                    JsonObject json = (JsonObject) JsonParser.parse(segment);
                                    String traceId = json.getString("trace_id");
                                    TestTrace testTrace = receivedTraces.get(traceId);
                                    if (null == testTrace) {
                                        testTrace = new TestTrace();
                                    }
                                    testTrace.withSegment(convertData(json));
                                    receivedTraces.put(traceId, testTrace);
                                }
                                sb.delete(0, segment.length());
                                if (sb.length() > 1 && sb.charAt(0) == '\n') {
                                    sb.deleteCharAt(0);
                                }
                            }
                        }
                        LOG.trace("Item {} received. JSON content: {}, Raw: {}",
                                receivedTraces.size(), receivedTraces, raw);
                    } catch (Exception jsonEx) {
                        LOG.warn("Could not convert segment " + locSegment + " to a Java object", jsonEx);
                    }
                }
            } catch (SocketException sex) {
                LOG.info("UDP socket closed");
            } catch (Exception ex) {
                LOG.warn("UDP socket failed due to " + ex.getLocalizedMessage(), ex);
            }
        }

        private TestSegment convertData(JsonObject json) {
            String name = json.getString("name");
            double startTime = json.getDouble("start_time");
            TestSegment segment = new TestSegment(name, startTime);
            if (json.has("subsegments")) {
                JsonArray jsonSubsegments = (JsonArray) json.get("subsegments");
                List<TestSubsegment> subsegments = convertSubsegments(jsonSubsegments);
                for (TestSubsegment subsegment : subsegments) {
                    segment.withSubsegment(subsegment);
                }
            }
            addAnnotationsIfAvailable(segment, json);
            addMetadataIfAvailable(segment, json);
            return segment;
        }

        private List<TestSubsegment> convertSubsegments(JsonArray jsonSubsegments) {
            List<TestSubsegment> subsegments = new ArrayList<>(jsonSubsegments.size());
            for (int i = 0; i < jsonSubsegments.size(); i++) {
                JsonObject jsonSubsegment = jsonSubsegments.toArray(new JsonObject[jsonSubsegments.size()])[i];
                subsegments.add(convertSubsegment(jsonSubsegment));
            }
            return subsegments;
        }

        private TestSubsegment convertSubsegment(JsonObject json) {
            TestSubsegment subsegment = new TestSubsegment((String)json.get("name"));
            if (json.has("subsegments")) {
                List<TestSubsegment> subsegments = convertSubsegments((JsonArray) json.get("subsegments"));
                for (TestSubsegment tss : subsegments) {
                    subsegment.withSubsegment(tss);
                }
            }
            addAnnotationsIfAvailable(subsegment, json);
            addMetadataIfAvailable(subsegment, json);
            return subsegment;
        }

        private void addAnnotationsIfAvailable(TestEntity<?> entity, JsonObject json) {
            if (json.has("annotations")) {
                JsonObject annotations = (JsonObject) json.get("annotations");
                for (String key : annotations.getKeys()) {
                    entity.withAnnotation(key, annotations.get(key));
                }
            }
        }

        private void addMetadataIfAvailable(TestEntity<?> entity, JsonObject json) {
            if (json.has("metadata")) {
                JsonObject rawMetadata = (JsonObject) json.get("metadata");
                Map<String, Map<String, Object>> metadata = parseMetadata(rawMetadata);
                for (String namespace : metadata.keySet()) {
                    for (String key : metadata.get(namespace).keySet()) {
                        entity.withMetadata(namespace, key, metadata.get(namespace).get(key));
                    }
                }
            }
        }

        private Map<String, Map<String, Object>> parseMetadata(JsonObject json) {
            /*
             "metadata" : {
                "default" : {
                    "meta1" : "meta1"
                },
                "customNamespace" : {
                    "meta2" : "meta2"
                }
             }
             */
            Map<String, Map<String, Object>> metadata = new LinkedHashMap<>(json.getKeys().size());
            for (String namespace : json.getKeys()) {
                JsonObject namespaceData = (JsonObject) json.get(namespace);
                if (!metadata.containsKey(namespace)) {
                    metadata.put(namespace, new LinkedHashMap<>(namespaceData.getKeys().size()));
                }
                for (String key : namespaceData.getKeys()) {
                    metadata.get(namespace).put(key, namespaceData.get(key));
                }
            }
            return metadata;
        }

        private void close() {
            done = true;
            if (null != serverSocket) {
                LOG.info("Shutting down UDP socket");
                serverSocket.close();
            }
        }
    }
}
