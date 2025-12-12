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
package org.apache.camel.component.langchain4j.embeddings;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.grpc.DataType;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.collection.CollectionSchemaParam;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.highlevel.dml.SearchSimpleParam;
import io.milvus.param.highlevel.dml.response.SearchResponse;
import io.milvus.param.index.CreateIndexParam;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.milvus.Milvus;
import org.apache.camel.component.milvus.MilvusAction;
import org.apache.camel.component.milvus.MilvusComponent;
import org.apache.camel.component.milvus.MilvusHeaders;
import org.apache.camel.test.infra.milvus.services.MilvusService;
import org.apache.camel.test.infra.milvus.services.MilvusServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class LangChain4jEmbeddingsComponentMilvusTargetIT extends CamelTestSupport {
    public static final String MILVUS_URI = "milvus:embeddings";

    @RegisterExtension
    static MilvusService MILVUS = MilvusServiceFactory.createSingletonService();

    @Override

    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        var milvus = context.getComponent(Milvus.SCHEME, MilvusComponent.class);
        milvus.getConfiguration().setHost(MILVUS.getMilvusHost());
        milvus.getConfiguration().setPort(MILVUS.getMilvusPort());

        context.getRegistry().bind("embedding-model", new AllMiniLmL6V2EmbeddingModel());

        return context;
    }

    @Test
    @Order(1)
    public void createCollectionAndIndex() {
        FieldType fieldType1 = FieldType.newBuilder()
                .withName("userID")
                .withDescription("user identification")
                .withDataType(DataType.Int64)
                .withPrimaryKey(true)
                .build();

        FieldType fieldType2 = FieldType.newBuilder()
                .withName("vector")
                .withDescription("face embedding")
                .withDataType(DataType.FloatVector)
                .withDimension(384)
                .build();

        FieldType fieldType3 = FieldType.newBuilder()
                .withName("text")
                .withDataType(DataType.VarChar)
                .withMaxLength(65535)
                .build();

        CreateCollectionParam createCollectionReq = CreateCollectionParam.newBuilder()
                .withCollectionName("embeddings")
                .withDescription("customer info")
                .withShardsNum(2)
                .withSchema(CollectionSchemaParam.newBuilder()
                        .withEnableDynamicField(false)
                        .addFieldType(fieldType1)
                        .addFieldType(fieldType2)
                        .addFieldType(fieldType3)
                        .build())
                .build();

        Exchange result = fluentTemplate.to(MILVUS_URI)
                .withHeader(MilvusHeaders.ACTION, MilvusAction.CREATE_COLLECTION)
                .withBody(
                        createCollectionReq)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();

        CreateIndexParam createVectorIndexParam = CreateIndexParam.newBuilder()
                .withCollectionName("embeddings")
                .withFieldName("vector")
                .withIndexName("vectorIndex")
                .withIndexType(IndexType.IVF_FLAT)
                .withMetricType(MetricType.L2)
                .withExtraParam("{\"nlist\":128}")
                .withSyncMode(Boolean.TRUE)
                .build();

        result = fluentTemplate.to(MILVUS_URI)
                .withHeader(MilvusHeaders.ACTION, MilvusAction.CREATE_INDEX)
                .withBody(
                        createVectorIndexParam)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
    }

    @Test
    @Order(2)
    public void insert() {

        Exchange result = fluentTemplate.to("direct:in")
                .withBody("hi")
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
    }

    @Test
    @Order(3)
    public void search() {
        SearchSimpleParam searchSimpleParam = SearchSimpleParam.newBuilder()
                .withCollectionName("embeddings")
                .withVectors(generateFloatVector())
                .withFilter("userID>=1")
                .withLimit(100L)
                .withOffset(0L)
                .withOutputFields(Lists.newArrayList("userID"))
                .withConsistencyLevel(ConsistencyLevelEnum.STRONG)
                .build();
        Exchange result = fluentTemplate.to(MILVUS_URI)
                .withHeader(MilvusHeaders.ACTION, MilvusAction.SEARCH)
                .withBody(searchSimpleParam)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();

        assertThat(result.getIn().getBody()).isInstanceOfSatisfying(SearchResponse.class,
                c -> assertThat(c.getRowRecords().size() == 1));
    }

    @Test
    @Order(4)
    public void upsert() {

        Exchange result = fluentTemplate.to("direct:up")
                .withBody("hello")
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:in")
                        .to("langchain4j-embeddings:test")
                        .setHeader(MilvusHeaders.ACTION).constant(MilvusAction.INSERT)
                        .setHeader(MilvusHeaders.KEY_NAME).constant("userID")
                        .setHeader(MilvusHeaders.KEY_VALUE).constant(Long.valueOf("3"))
                        .transformDataType(new org.apache.camel.spi.DataType("milvus:embeddings"))
                        .to(MILVUS_URI);

                from("direct:up")
                        .to("langchain4j-embeddings:test")
                        .setHeader(MilvusHeaders.ACTION).constant(MilvusAction.UPSERT)
                        .setHeader(MilvusHeaders.KEY_NAME).constant("userID")
                        .setHeader(MilvusHeaders.KEY_VALUE).constant(Long.valueOf("3"))
                        .transformDataType(new org.apache.camel.spi.DataType("milvus:embeddings"))
                        .to(MILVUS_URI);
            }
        };
    }

    private List<Float> generateFloatVector() {
        Random ran = new Random();
        List<Float> vector = new ArrayList<>();
        for (int i = 0; i < 384; ++i) {
            vector.add(ran.nextFloat());
        }
        return vector;
    }
}
