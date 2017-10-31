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
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
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

  private static class UDPSocketListener implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private DatagramSocket serverSocket = null;
    private Map<String, TestTrace> receivedTraces;
    private volatile boolean done = false;

    private UDPSocketListener(Map<String, TestTrace> receivedTraces) {
      this.receivedTraces = receivedTraces;
    }

    @Override
    public void run() {
      try {
        LOG.info("Starting UDP socket listening on port 2000");
        serverSocket = new DatagramSocket(2000);

        StringBuilder sb = new StringBuilder();
        byte[] receiveData = new byte[8096];
        while (!done) {
          DatagramPacket receivedPacket = new DatagramPacket(receiveData, receiveData.length);
          serverSocket.receive(receivedPacket);

          LOG.debug("Receiving UDP data");
          sb.append(new String(receivedPacket.getData()));

          String _segment = null;
          try {
            String raw = sb.toString();
            String[] segments = raw.split("\\n");
            for (String segment : segments) {
              _segment = segment;
              LOG.trace("Processing received segment: {}", segment);
              if (!"".equals(segment)) {
                if (segment.contains("format") && segment.contains("version")) {
                  LOG.trace("Skipping format and version JSON");
                } else {
                  LOG.trace("Converting segment {} to a Java object", segment);
                  JSONObject json = new JSONObject(segment);
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
          } catch (JSONException jsonEx) {
            LOG.warn("Could not convert segment " + _segment + " to a Java object", jsonEx);
          }
        }
      } catch (SocketException sex) {
        LOG.info("UDP socket closed");
      } catch (Exception ex) {
        LOG.warn("UDP socket failed due to " + ex.getLocalizedMessage(), ex);
      }
    }

    private TestSegment convertData(JSONObject json) {
      String name = json.getString("name");
      double startTime = json.getDouble("start_time");
      TestSegment segment = new TestSegment(name, startTime);
      if (json.has("subsegments")) {
        JSONArray jsonSubsegments = json.getJSONArray("subsegments");
        List<TestSubsegment> subsegments = convertSubsegments(jsonSubsegments);
        for (TestSubsegment subsegment : subsegments) {
          segment.withSubsegment(subsegment);
        }
      }
      addAnnotationsIfAvailable(segment, json);
      addMetadataIfAvailable(segment, json);
      return segment;
    }

    private List<TestSubsegment> convertSubsegments(JSONArray jsonSubsegments) {
      List<TestSubsegment> subsegments = new ArrayList<>(jsonSubsegments.length());
      for (int i = 0; i < jsonSubsegments.length(); i++) {
        JSONObject jsonSubsegment = jsonSubsegments.getJSONObject(i);
        subsegments.add(convertSubsegment(jsonSubsegment));
      }
      return subsegments;
    }

    private TestSubsegment convertSubsegment(JSONObject json) {
      TestSubsegment subsegment = new TestSubsegment(json.getString("name"));
      if (json.has("subsegments")) {
        List<TestSubsegment> subsegments = convertSubsegments(json.getJSONArray("subsegments"));
        for (TestSubsegment tss : subsegments) {
          subsegment.withSubsegment(tss);
        }
      }
      addAnnotationsIfAvailable(subsegment, json);
      addMetadataIfAvailable(subsegment, json);
      return subsegment;
    }

    private void addAnnotationsIfAvailable(TestEntity<?> entity, JSONObject json) {
      if (json.has("annotations")) {
        Map<String, Object> annotations = parseAnnotations(json.getJSONObject("annotations"));
        for (String key : annotations.keySet()) {
          entity.withAnnotation(key, annotations.get(key));
        }
      }
    }

    private void addMetadataIfAvailable(TestEntity<?> entity, JSONObject json) {
      if (json.has("metadata")) {
        Map<String, Map<String, Object>> metadata = parseMetadata(json.getJSONObject("metadata"));
        for (String namespace : metadata.keySet()) {
          for (String key : metadata.get(namespace).keySet()) {
            entity.withMetadata(namespace, key, metadata.get(namespace).get(key));
          }
        }
      }
    }

    private Map<String, Object> parseAnnotations(JSONObject json) {
      /*
       "annotations" : {
          "test2" : 1,
          "test3" : true,
          "test1" : "test"
       }
       */
      Map<String, Object> annotations = new LinkedHashMap<>(json.keySet().size());
      for (String key : json.keySet()) {
        annotations.put(key, json.get(key));
      }
      return annotations;
    }

    private Map<String, Map<String, Object>> parseMetadata(JSONObject json) {
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
      Map<String, Map<String, Object>> metadata = new LinkedHashMap<>(json.keySet().size());
      for (String namespace : json.keySet()) {
        JSONObject namespaceData = json.getJSONObject(namespace);
        if (!metadata.containsKey(namespace)) {
          metadata.put(namespace, new LinkedHashMap<>(namespaceData.keySet().size()));
        }
        for (String key : namespaceData.keySet()) {
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