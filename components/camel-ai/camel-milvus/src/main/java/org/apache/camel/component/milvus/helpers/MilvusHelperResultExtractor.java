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
package org.apache.camel.component.milvus.helpers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.milvus.param.highlevel.dml.response.SearchResponse;
import io.milvus.response.QueryResultsWrapper;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

/**
 * A Camel {@link Processor} that extracts field values from a Milvus
 * {@link io.milvus.param.highlevel.dml.response.SearchResponse} into a list of ranked maps. Each map contains a
 * {@code rank} entry and the requested output field values. The extracted list is set as the exchange body.
 */
public class MilvusHelperResultExtractor implements Processor {

    private String outputFields = "content";

    @Override
    public void process(Exchange exchange) throws Exception {
        exchange.getIn().setBody(extract(exchange));
    }

    public List<Map<String, Object>> extract(Exchange exchange) {
        SearchResponse response = exchange.getIn().getBody(SearchResponse.class);
        List<Map<String, Object>> extracted = new ArrayList<>();

        String[] fields = outputFields.split(",");

        if (response != null) {
            List<QueryResultsWrapper.RowRecord> records = response.getRowRecords(0);
            int rank = 1;
            for (QueryResultsWrapper.RowRecord record : records) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("rank", rank++);
                Object score = record.get("score");
                if (score != null) {
                    item.put("score", score);
                }
                for (String field : fields) {
                    String trimmed = field.trim();
                    if (!trimmed.isEmpty()) {
                        Object value = record.get(trimmed);
                        if (value != null) {
                            item.put(trimmed, value);
                        }
                    }
                }
                extracted.add(item);
            }
        }
        return extracted;
    }

    public String getOutputFields() {
        return outputFields;
    }

    /**
     * @param outputFields comma-separated list of field names to extract from search results (e.g.,
     *                     {@code content,title})
     */
    public void setOutputFields(String outputFields) {
        this.outputFields = outputFields;
    }
}
