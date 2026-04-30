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
import java.util.List;

import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.param.highlevel.dml.SearchSimpleParam;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.milvus.MilvusAction;
import org.apache.camel.component.milvus.MilvusHeaders;

/**
 * A Camel {@link Processor} that builds a Milvus {@link io.milvus.param.highlevel.dml.SearchSimpleParam} from the
 * exchange body vector and simple string properties, then sets it as the exchange body together with the
 * {@link MilvusAction#SEARCH} header.
 */
public class MilvusHelperSearch implements Processor {

    private String collectionName = "default_collection";
    private String outputFields = "content";
    private String limit = "10";
    private String offset = "0";
    private String consistencyLevel = "STRONG";
    private String filter;

    @SuppressWarnings("unchecked")
    @Override
    public void process(Exchange exchange) throws Exception {
        List<Float> queryEmbedding = exchange.getIn().getBody(List.class);
        if (queryEmbedding == null) {
            throw new IllegalArgumentException("Exchange body must contain a List<Float> vector, but was null");
        }
        long searchLimit = Long.parseLong(limit);

        List<String> fields = new ArrayList<>();
        for (String field : outputFields.split(",")) {
            String trimmed = field.trim();
            if (!trimmed.isEmpty()) {
                fields.add(trimmed);
            }
        }

        long searchOffset = Long.parseLong(offset);
        ConsistencyLevelEnum consistency = ConsistencyLevelEnum.valueOf(consistencyLevel);

        SearchSimpleParam.Builder builder = SearchSimpleParam.newBuilder()
                .withCollectionName(collectionName)
                .withVectors(queryEmbedding)
                .withLimit(searchLimit)
                .withOffset(searchOffset)
                .withOutputFields(fields)
                .withConsistencyLevel(consistency);

        if (filter != null && !filter.isEmpty()) {
            builder.withFilter(filter);
        }

        SearchSimpleParam param = builder.build();

        exchange.getIn().setBody(param);
        exchange.getIn().setHeader(MilvusHeaders.ACTION, MilvusAction.SEARCH);
    }

    public String getCollectionName() {
        return collectionName;
    }

    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
    }

    public String getOutputFields() {
        return outputFields;
    }

    /**
     * @param outputFields comma-separated list of field names to include in search results (e.g.,
     *                     {@code content,title})
     */
    public void setOutputFields(String outputFields) {
        this.outputFields = outputFields;
    }

    public String getLimit() {
        return limit;
    }

    /**
     * @param limit the maximum number of results to return as a string (e.g., {@code 10}, {@code 100})
     */
    public void setLimit(String limit) {
        this.limit = limit;
    }

    public String getOffset() {
        return offset;
    }

    /**
     * @param offset the number of results to skip as a string (e.g., {@code 0}, {@code 10})
     */
    public void setOffset(String offset) {
        this.offset = offset;
    }

    public String getConsistencyLevel() {
        return consistencyLevel;
    }

    /**
     * @param consistencyLevel the Milvus {@link io.milvus.common.clientenum.ConsistencyLevelEnum} enum name (e.g.,
     *                         {@code STRONG}, {@code BOUNDED}, {@code EVENTUALLY})
     */
    public void setConsistencyLevel(String consistencyLevel) {
        this.consistencyLevel = consistencyLevel;
    }

    public String getFilter() {
        return filter;
    }

    /**
     * @param filter a Milvus boolean expression to filter results (e.g., {@code age > 18}, {@code status == "active"})
     */
    public void setFilter(String filter) {
        this.filter = filter;
    }
}
