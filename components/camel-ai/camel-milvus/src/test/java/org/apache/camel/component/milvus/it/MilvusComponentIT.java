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

package org.apache.camel.component.milvus.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.grpc.DataType;
import io.milvus.grpc.QueryResults;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.QueryParam;
import io.milvus.param.dml.UpsertParam;
import io.milvus.param.highlevel.dml.SearchSimpleParam;
import io.milvus.param.highlevel.dml.response.SearchResponse;
import io.milvus.param.index.CreateIndexParam;
import org.apache.camel.Exchange;
import org.apache.camel.component.milvus.MilvusAction;
import org.apache.camel.component.milvus.MilvusHeaders;
import org.apache.camel.component.milvus.MilvusTestSupport;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MilvusComponentIT extends MilvusTestSupport {
    @Test
    @Order(1)
    public void createCollection() {
        FieldType fieldType1 = FieldType.newBuilder()
                .withName("userID")
                .withDescription("user identification")
                .withDataType(DataType.Int64)
                .withPrimaryKey(true)
                .withAutoID(true)
                .build();

        FieldType fieldType2 = FieldType.newBuilder()
                .withName("userFace")
                .withDescription("face embedding")
                .withDataType(DataType.FloatVector)
                .withDimension(64)
                .build();

        FieldType fieldType3 = FieldType.newBuilder()
                .withName("userAge")
                .withDescription("user age")
                .withDataType(DataType.Int8)
                .build();

        CreateCollectionParam createCollectionReq = CreateCollectionParam.newBuilder()
                .withCollectionName("test")
                .withDescription("customer info")
                .withShardsNum(2)
                .withEnableDynamicField(false)
                .addFieldType(fieldType1)
                .addFieldType(fieldType2)
                .addFieldType(fieldType3)
                .build();

        Exchange result = fluentTemplate
                .to("milvus:test")
                .withHeader(MilvusHeaders.ACTION, MilvusAction.CREATE_COLLECTION)
                .withBody(createCollectionReq)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
    }

    @Test
    @Order(2)
    public void createIndex() {
        CreateIndexParam createAgeIndexParam = CreateIndexParam.newBuilder()
                .withCollectionName("test")
                .withFieldName("userAge")
                .withIndexType(IndexType.STL_SORT)
                .withSyncMode(Boolean.TRUE)
                .build();

        Exchange result = fluentTemplate
                .to("milvus:test")
                .withHeader(MilvusHeaders.ACTION, MilvusAction.CREATE_INDEX)
                .withBody(createAgeIndexParam)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();

        CreateIndexParam createVectorIndexParam = CreateIndexParam.newBuilder()
                .withCollectionName("test")
                .withFieldName("userFace")
                .withIndexName("userFaceIndex")
                .withIndexType(IndexType.IVF_FLAT)
                .withMetricType(MetricType.L2)
                .withExtraParam("{\"nlist\":128}")
                .withSyncMode(Boolean.TRUE)
                .build();

        result = fluentTemplate
                .to("milvus:test")
                .withHeader(MilvusHeaders.ACTION, MilvusAction.CREATE_INDEX)
                .withBody(createVectorIndexParam)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
    }

    @Test
    @Order(3)
    public void insert() {
        Random ran = new Random();
        List<Integer> ages = new ArrayList<>();
        for (long i = 0L; i < 2; ++i) {
            ages.add(ran.nextInt(99));
        }
        List<InsertParam.Field> fields = new ArrayList<>();
        fields.add(new InsertParam.Field("userAge", ages));
        fields.add(new InsertParam.Field("userFace", generateFloatVectors(2)));

        InsertParam insertParam = InsertParam.newBuilder()
                .withCollectionName("test")
                .withFields(fields)
                .build();

        Exchange result = fluentTemplate
                .to("milvus:test")
                .withHeader(MilvusHeaders.ACTION, MilvusAction.INSERT)
                .withBody(insertParam)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
    }

    @Test
    @Order(4)
    public void upsert() {
        Random ran = new Random();
        List<Integer> ages = new ArrayList<>();
        for (long i = 0L; i < 2; ++i) {
            ages.add(ran.nextInt(99));
        }
        List<UpsertParam.Field> fields = new ArrayList<>();
        fields.add(new UpsertParam.Field("userAge", ages));
        fields.add(new UpsertParam.Field("userFace", generateFloatVectors(2)));

        UpsertParam upsertParam = UpsertParam.newBuilder()
                .withCollectionName("test")
                .withFields(fields)
                .build();

        Exchange result = fluentTemplate
                .to("milvus:test")
                .withHeader(MilvusHeaders.ACTION, MilvusAction.UPSERT)
                .withBody(upsertParam)
                .request(Exchange.class);

        // we cannot upsert as we lack userID field
        assertThat(result).isNotNull();
        Assertions.assertTrue(result.isFailed());
    }

    @Test
    @Order(5)
    public void search() {
        SearchSimpleParam searchSimpleParam = SearchSimpleParam.newBuilder()
                .withCollectionName("test")
                .withVectors(generateFloatVector())
                .withFilter("userAge>0")
                .withLimit(100L)
                .withOffset(0L)
                .withOutputFields(Lists.newArrayList("userAge"))
                .withConsistencyLevel(ConsistencyLevelEnum.STRONG)
                .build();

        Exchange result = fluentTemplate
                .to("milvus:test")
                .withHeader(MilvusHeaders.ACTION, MilvusAction.SEARCH)
                .withBody(searchSimpleParam)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
        assertThat(result.getMessage()
                        .getBody(SearchResponse.class)
                        .getRowRecords()
                        .size()
                == 2);
    }

    @Test
    @Order(6)
    public void query() {
        QueryParam searchSimpleParam = QueryParam.newBuilder()
                .withCollectionName("test")
                .withExpr("userAge>0")
                .withOutFields(Lists.newArrayList("userAge"))
                .withConsistencyLevel(ConsistencyLevelEnum.STRONG)
                .build();

        Exchange result = fluentTemplate
                .to("milvus:test")
                .withHeader(MilvusHeaders.ACTION, MilvusAction.QUERY)
                .withBody(searchSimpleParam)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody(QueryResults.class).getFieldsDataCount() == 2);
    }

    @Test
    @Order(7)
    public void delete() {
        DeleteParam delete = DeleteParam.newBuilder()
                .withCollectionName("test")
                .withExpr("userAge>0")
                .build();

        Exchange result = fluentTemplate
                .to("milvus:test")
                .withHeader(MilvusHeaders.ACTION, MilvusAction.DELETE)
                .withBody(delete)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();

        SearchSimpleParam searchSimpleParam = SearchSimpleParam.newBuilder()
                .withCollectionName("test")
                .withVectors(generateFloatVector())
                .withFilter("userAge>0")
                .withLimit(100L)
                .withOffset(0L)
                .withOutputFields(Lists.newArrayList("userAge"))
                .withConsistencyLevel(ConsistencyLevelEnum.STRONG)
                .build();

        result = fluentTemplate
                .to("milvus:test")
                .withHeader(MilvusHeaders.ACTION, MilvusAction.SEARCH)
                .withBody(searchSimpleParam)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
        assertThat(result.getMessage()
                        .getBody(SearchResponse.class)
                        .getRowRecords()
                        .size()
                == 0);
    }

    private List<List<Float>> generateFloatVectors(int count) {
        Random ran = new Random();
        List<List<Float>> vectors = new ArrayList<>();
        for (int n = 0; n < count; ++n) {
            List<Float> vector = new ArrayList<>();
            for (int i = 0; i < 64; ++i) {
                vector.add(ran.nextFloat());
            }
            vectors.add(vector);
        }

        return vectors;
    }

    private List<Float> generateFloatVector() {
        Random ran = new Random();
        List<Float> vector = new ArrayList<>();
        for (int i = 0; i < 64; ++i) {
            vector.add(ran.nextFloat());
        }
        return vector;
    }
}
