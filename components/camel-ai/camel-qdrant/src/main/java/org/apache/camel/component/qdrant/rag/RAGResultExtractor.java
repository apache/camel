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
package org.apache.camel.component.qdrant.rag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.qdrant.client.grpc.Points;
import org.apache.camel.Exchange;

public class RAGResultExtractor {

    private String payloadKey = "content";

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> extract(Exchange exchange) {
        List<Points.ScoredPoint> results = exchange.getIn().getBody(List.class);
        List<Map<String, Object>> extracted = new ArrayList<>();
        int rank = 1;
        for (Points.ScoredPoint point : results) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("rank", rank++);
            item.put("content", point.getPayloadMap().get(payloadKey).getStringValue());
            item.put("score", point.getScore());
            extracted.add(item);
        }
        return extracted;
    }

    public String getPayloadKey() {
        return payloadKey;
    }

    public void setPayloadKey(String payloadKey) {
        this.payloadKey = payloadKey;
    }
}
