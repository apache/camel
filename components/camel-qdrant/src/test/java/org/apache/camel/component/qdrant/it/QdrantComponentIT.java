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
public class QdrantComponentIT extends QdrantTestSupport {
    @Test
    @Order(1)
    public void createCollection() {
        Exchange result = fluentTemplate.to("qdrant:testComponent")
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
        Exchange result = fluentTemplate.to("qdrant:testComponent")
                .withHeader(Qdrant.Headers.ACTION, QdrantAction.UPSERT)
                .withBody(
                        Points.PointStruct.newBuilder()
                                .setId(PointIdFactory.id(8))
                                .setVectors(VectorsFactory.vectors(List.of(3.5f, 4.5f)))
                                .putAllPayload(Map.of(
                                        "foo", ValueFactory.value("hello"),
                                        "bar", ValueFactory.value(1)))
                                .build())
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();

        assertThat(result.getIn().getHeaders()).hasEntrySatisfying(Qdrant.Headers.OPERATION_ID, v -> {
            assertThat(v).isNotNull();
        });
        assertThat(result.getIn().getHeaders()).hasEntrySatisfying(Qdrant.Headers.OPERATION_STATUS, v -> {
            assertThat(v).isEqualTo(Points.UpdateStatus.Completed.name());
        });
        assertThat(result.getIn().getHeaders()).hasEntrySatisfying(Qdrant.Headers.OPERATION_STATUS_VALUE, v -> {
            assertThat(v).isEqualTo(Points.UpdateStatus.Completed.getNumber());
        });
    }

    @Test
    @Order(3)
    @SuppressWarnings({ "unchecked" })
    public void retrieve() {
        Exchange result = fluentTemplate.to("qdrant:testComponent")
                .withHeader(Qdrant.Headers.ACTION, QdrantAction.RETRIEVE)
                .withBody(PointIdFactory.id(8))
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();

        assertThat(result.getIn().getBody()).isInstanceOfSatisfying(Collection.class, c -> {
            assertThat(c).hasSize(1);
            assertThat(c).hasOnlyElementsOfType(Points.RetrievedPoint.class);
        });
    }

    @Test
    @Order(4)
    public void delete() {
        Exchange result = fluentTemplate.to("qdrant:testComponent")
                .withHeader(Qdrant.Headers.ACTION, QdrantAction.DELETE)
                .withBody(ConditionFactory.matchKeyword("foo", "hello"))
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();

        assertThat(result.getIn().getHeaders()).hasEntrySatisfying(Qdrant.Headers.OPERATION_ID, v -> {
            assertThat(v).isNotNull();
        });
        assertThat(result.getIn().getHeaders()).hasEntrySatisfying(Qdrant.Headers.OPERATION_STATUS, v -> {
            assertThat(v).isEqualTo(Points.UpdateStatus.Completed.name());
        });
        assertThat(result.getIn().getHeaders()).hasEntrySatisfying(Qdrant.Headers.OPERATION_STATUS_VALUE, v -> {
            assertThat(v).isEqualTo(Points.UpdateStatus.Completed.getNumber());
        });
    }

    @Test
    @Order(5)
    public void retrieveAfterDelete() {
        Exchange result = fluentTemplate.to("qdrant:testComponent")
                .withHeader(Qdrant.Headers.ACTION, QdrantAction.RETRIEVE)
                .withBody(PointIdFactory.id(8))
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();

        assertThat(result.getIn().getBody()).isInstanceOfSatisfying(Collection.class, c -> {
            assertThat(c).hasSize(0);
        });
    }

}
