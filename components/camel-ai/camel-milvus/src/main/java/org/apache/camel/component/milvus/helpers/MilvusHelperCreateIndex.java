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

import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.index.CreateIndexParam;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.milvus.MilvusAction;
import org.apache.camel.component.milvus.MilvusHeaders;

/**
 * A Camel {@link Processor} that builds a Milvus {@link io.milvus.param.index.CreateIndexParam} from simple string
 * properties and sets it as the exchange body together with the {@link MilvusAction#CREATE_INDEX} header.
 */
public class MilvusHelperCreateIndex implements Processor {

    private String collectionName = "default_collection";
    private String vectorFieldName = "embedding";
    private String indexName;
    private String indexType = "IVF_FLAT";
    private String metricType = "COSINE";
    private String extraParam = "{\"nlist\": 128}";

    @Override
    public void process(Exchange exchange) throws Exception {
        IndexType idxType = IndexType.valueOf(indexType);
        MetricType metric = MetricType.valueOf(metricType);

        CreateIndexParam.Builder builder = CreateIndexParam.newBuilder()
                .withCollectionName(collectionName)
                .withFieldName(vectorFieldName)
                .withIndexType(idxType)
                .withMetricType(metric)
                .withExtraParam(extraParam)
                .withSyncMode(Boolean.TRUE);

        if (indexName != null && !indexName.isEmpty()) {
            builder.withIndexName(indexName);
        }

        CreateIndexParam param = builder.build();

        exchange.getIn().setBody(param);
        exchange.getIn().setHeader(MilvusHeaders.ACTION, MilvusAction.CREATE_INDEX);
    }

    public String getCollectionName() {
        return collectionName;
    }

    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
    }

    public String getVectorFieldName() {
        return vectorFieldName;
    }

    public void setVectorFieldName(String vectorFieldName) {
        this.vectorFieldName = vectorFieldName;
    }

    public String getIndexName() {
        return indexName;
    }

    /**
     * @param indexName the name to assign to the index (e.g., {@code myVectorIndex})
     */
    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    public String getIndexType() {
        return indexType;
    }

    /**
     * @param indexType the Milvus {@link io.milvus.param.IndexType} enum name (e.g., {@code IVF_FLAT}, {@code HNSW})
     */
    public void setIndexType(String indexType) {
        this.indexType = indexType;
    }

    public String getMetricType() {
        return metricType;
    }

    /**
     * @param metricType the Milvus {@link io.milvus.param.MetricType} enum name (e.g., {@code COSINE}, {@code L2},
     *                   {@code IP})
     */
    public void setMetricType(String metricType) {
        this.metricType = metricType;
    }

    public String getExtraParam() {
        return extraParam;
    }

    /**
     * @param extraParam JSON string with index-specific parameters (e.g., {@code {"nlist": 128}})
     */
    public void setExtraParam(String extraParam) {
        this.extraParam = extraParam;
    }
}
