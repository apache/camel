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

import io.qdrant.client.PointIdFactory;
import io.qdrant.client.ValueFactory;
import io.qdrant.client.VectorsFactory;
import io.qdrant.client.WithPayloadSelectorFactory;
import io.qdrant.client.grpc.Collections;
import io.qdrant.client.grpc.Points;
import org.apache.camel.Exchange;
import org.apache.camel.component.qdrant.QdrantAction;
import org.apache.camel.component.qdrant.QdrantHeaders;
import org.apache.camel.component.qdrant.QdrantTestSupport;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The {@link QdrantHeaders#PAYLOAD_SELECTOR} header is advertised by the component, so a route must be able to ask for
 * a subset of the payload fields instead of the all-or-nothing {@link QdrantHeaders#INCLUDE_PAYLOAD} boolean.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class QdrantPayloadSelectorIT extends QdrantTestSupport {

    private static final String COLLECTION = "qdrant:payloadSelector";

    @Test
    @Order(0)
    void createCollection() {
        Exchange result = fluentTemplate.to(COLLECTION)
                .withHeader(QdrantHeaders.ACTION, QdrantAction.CREATE_COLLECTION)
                .withBody(Collections.VectorParams.newBuilder()
                        .setSize(2)
                        .setDistance(Collections.Distance.Cosine)
                        .build())
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
    }

    @Test
    @Order(1)
    void upsert() {
        Exchange result = fluentTemplate.to(COLLECTION)
                .withHeader(QdrantHeaders.ACTION, QdrantAction.UPSERT)
                .withBody(Points.PointStruct.newBuilder()
                        .setId(PointIdFactory.id(1))
                        .putPayload("keep", ValueFactory.value("kept"))
                        .putPayload("drop", ValueFactory.value("dropped"))
                        .setVectors(VectorsFactory.vectors(List.of(0.5f, 0.5f)))
                        .build())
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
    }

    @Test
    @Order(2)
    void retrieveHonoursThePayloadSelector() {
        Exchange result = fluentTemplate.to(COLLECTION)
                .withHeader(QdrantHeaders.ACTION, QdrantAction.RETRIEVE)
                .withHeader(QdrantHeaders.PAYLOAD_SELECTOR, WithPayloadSelectorFactory.include(List.of("keep")))
                .withBody(PointIdFactory.id(1))
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
        assertThat(result.getIn().getBody()).isInstanceOfSatisfying(Collection.class, c -> {
            assertThat(c).hasSize(1);
            Points.RetrievedPoint point = (Points.RetrievedPoint) c.iterator().next();
            assertThat(point.getPayloadMap()).containsOnlyKeys("keep");
        });
    }

    @Test
    @Order(3)
    void retrieveFallsBackToTheIncludePayloadFlag() {
        Exchange result = fluentTemplate.to(COLLECTION)
                .withHeader(QdrantHeaders.ACTION, QdrantAction.RETRIEVE)
                .withHeader(QdrantHeaders.INCLUDE_PAYLOAD, true)
                .withBody(PointIdFactory.id(1))
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
        assertThat(result.getIn().getBody()).isInstanceOfSatisfying(Collection.class, c -> {
            Points.RetrievedPoint point = (Points.RetrievedPoint) c.iterator().next();
            assertThat(point.getPayloadMap()).containsOnlyKeys("keep", "drop");
        });
    }

    @Test
    @Order(4)
    void similaritySearchHonoursThePayloadSelector() {
        Exchange result = fluentTemplate.to(COLLECTION)
                .withHeader(QdrantHeaders.ACTION, QdrantAction.SIMILARITY_SEARCH)
                .withHeader(QdrantHeaders.PAYLOAD_SELECTOR, WithPayloadSelectorFactory.include(List.of("keep")))
                .withBody(List.of(0.5f, 0.5f))
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
        assertThat(result.getIn().getBody()).isInstanceOfSatisfying(Collection.class, c -> {
            assertThat(c).hasSize(1);
            Points.ScoredPoint point = (Points.ScoredPoint) c.iterator().next();
            assertThat(point.getPayloadMap()).containsOnlyKeys("keep");
        });
    }
}
