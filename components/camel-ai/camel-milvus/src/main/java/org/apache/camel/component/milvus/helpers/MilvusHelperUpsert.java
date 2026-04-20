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
import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.milvus.param.dml.UpsertParam;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.milvus.MilvusAction;
import org.apache.camel.component.milvus.MilvusHeaders;

/**
 * A Camel {@link Processor} that builds a Milvus {@link io.milvus.param.dml.UpsertParam} from the exchange body vector
 * and exchange variables mapped via {@link #setTextFieldMappings(String)}, then sets it as the exchange body together
 * with the {@link MilvusAction#UPSERT} header.
 */
public class MilvusHelperUpsert implements Processor {

    private String collectionName = "default_collection";
    private String vectorFieldName = "embedding";
    private String textFieldMappings = "content=text";

    @SuppressWarnings("unchecked")
    @Override
    public void process(Exchange exchange) throws Exception {
        List<Float> vector = exchange.getIn().getBody(List.class);
        if (vector == null) {
            throw new IllegalArgumentException("Exchange body must contain a List<Float> vector, but was null");
        }

        Map<String, String> mappings = MilvusHelperFieldMappingUtil.parseMappings(textFieldMappings);

        List<UpsertParam.Field> fields = new ArrayList<>();
        for (Map.Entry<String, String> entry : mappings.entrySet()) {
            String value = exchange.getVariable(entry.getValue(), String.class);
            if (value == null) {
                throw new IllegalArgumentException(
                        "Exchange variable '" + entry.getValue() + "' is not set (mapped from field '" + entry.getKey() + "')");
            }
            fields.add(new UpsertParam.Field(entry.getKey(), Collections.singletonList(value)));
        }

        fields.add(new UpsertParam.Field(vectorFieldName, Collections.singletonList(vector)));

        UpsertParam param = UpsertParam.newBuilder()
                .withCollectionName(collectionName)
                .withFields(fields)
                .build();

        exchange.getIn().setBody(param);
        exchange.getIn().setHeader(MilvusHeaders.ACTION, MilvusAction.UPSERT);
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

    public String getTextFieldMappings() {
        return textFieldMappings;
    }

    /**
     * @param textFieldMappings comma-separated mappings of Milvus field names to exchange variable names (e.g.,
     *                          {@code field1=var1,field2=var2})
     */
    public void setTextFieldMappings(String textFieldMappings) {
        this.textFieldMappings = textFieldMappings;
    }
}
