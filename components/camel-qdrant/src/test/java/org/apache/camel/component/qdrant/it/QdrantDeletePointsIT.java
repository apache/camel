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
package org.apache.camel.component.qdrant.it;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import io.qdrant.client.ConditionFactory;
import io.qdrant.client.PointIdFactory;
import io.qdrant.client.ValueFactory;
import io.qdrant.client.VectorsFactory;
import io.qdrant.client.grpc.Collections;
import io.qdrant.client.grpc.Points;
import org.apache.camel.Exchange;
import org.apache.camel.component.qdrant.Qdrant;
import org.apache.camel.component.qdrant.QdrantAction;
import org.apache.camel.component.qdrant.QdrantTestSupport;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class QdrantDeletePointsIT extends QdrantTestSupport {
    @Test
    @Order(1)
    public void createCollection() {
        Exchange result = fluentTemplate.to("qdrant:testDelete")
                .withHeader(Qdrant.Headers.ACTION, QdrantAction.CREATE_COLLECTION)
                .withBody(
                        Collections.VectorParams.newBuilder()
                                .setSize(2)
                                .setDistance(Collections.Distance.Cosine).build())
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
    }

    @Test
    @Order(2)
    public void upsert() {
        Exchange result1 = fluentTemplate.to("qdrant:testDelete")
                .withHeader(Qdrant.Headers.ACTION, QdrantAction.UPSERT)
                .withBody(
                        Points.PointStruct.newBuilder()
                                .setId(PointIdFactory.id(8))
                                .setVectors(VectorsFactory.vectors(List.of(3.5f, 4.5f)))
                                .putAllPayload(Map.of("foo", ValueFactory.value("hello1")))
                                .build())
                .request(Exchange.class);

        assertThat(result1).isNotNull();
        assertThat(result1.getException()).isNull();

        Exchange result2 = fluentTemplate.to("qdrant:testDelete")
                .withHeader(Qdrant.Headers.ACTION, QdrantAction.UPSERT)
                .withBody(
                        Points.PointStruct.newBuilder()
                                .setId(PointIdFactory.id(9))
                                .setVectors(VectorsFactory.vectors(List.of(3.5f, 4.5f)))
                                .putAllPayload(Map.of("bar", ValueFactory.value("hello2")))
                                .build())
                .request(Exchange.class);

        assertThat(result2).isNotNull();
        assertThat(result2.getException()).isNull();
    }

    @Test
    @Order(3)
    public void deleteWithCondition() {
        Exchange deleteResult = fluentTemplate.to("qdrant:testDelete")
                .withHeader(Qdrant.Headers.ACTION, QdrantAction.DELETE)
                .withBody(ConditionFactory.matchKeyword("foo", "hello1"))
                .request(Exchange.class);

        assertThat(deleteResult).isNotNull();
        assertThat(deleteResult.getException()).isNull();

        Exchange result = fluentTemplate.to("qdrant:testDelete")
                .withHeader(Qdrant.Headers.ACTION, QdrantAction.RETRIEVE)
                .withBody(PointIdFactory.id(8))
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();

        assertThat(result.getIn().getBody()).isInstanceOfSatisfying(Collection.class, c -> {
            assertThat(c).hasSize(0);
        });
    }

    @Test
    @Order(4)
    public void deleteWithFilter() {
        Exchange deleteResult = fluentTemplate.to("qdrant:testDelete")
                .withHeader(Qdrant.Headers.ACTION, QdrantAction.DELETE)
                .withBody(
                        Points.Filter.newBuilder()
                                .addMust(ConditionFactory.matchKeyword("bar", "hello2"))
                                .build())
                .request(Exchange.class);

        assertThat(deleteResult).isNotNull();
        assertThat(deleteResult.getException()).isNull();

        Exchange result = fluentTemplate.to("qdrant:testDelete")
                .withHeader(Qdrant.Headers.ACTION, QdrantAction.RETRIEVE)
                .withBody(PointIdFactory.id(9))
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();

        assertThat(result.getIn().getBody()).isInstanceOfSatisfying(Collection.class, c -> {
            assertThat(c).hasSize(0);
        });
    }

}
