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

import org.apache.camel.Route;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

@DevConsole(name = "kafka", displayName = "Kafka", description = "Apache Kafka")
public class KafkaDevConsole extends AbstractDevConsole {

    public KafkaDevConsole() {
        super("camel", "kafka", "Kafka", "Apache Kafka");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();
        for (Route route : getCamelContext().getRoutes()) {
            if (route.getConsumer() instanceof KafkaConsumer kc) {
                sb.append(String.format("\n    Route Id: %s", route.getRouteId()));
                sb.append(String.format("\n    From: %s", route.getEndpoint().getEndpointUri()));
                sb.append(
                        String.format("\n    State: %s", getCamelContext().getRouteController().getRouteStatus(route.getId())));
                sb.append(String.format("\n    Uptime: %s", route.getUptime()));
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
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();

        List<JsonObject> list = new ArrayList<>();
        root.put("kafkaConsumers", list);

        for (Route route : getCamelContext().getRoutes()) {
            if (route.getConsumer() instanceof KafkaConsumer kc) {
                JsonObject jo = new JsonObject();
                jo.put("routeId", route.getRouteId());
                jo.put("uri", route.getEndpoint().getEndpointUri());
                jo.put("state", getCamelContext().getRouteController().getRouteStatus(route.getId()));
                jo.put("uptime", route.getUptime());

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
                }
                list.add(jo);
            }
        }
        return root;
    }

}
