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

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.qdrant.client.grpc.Collections;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.component.qdrant.QdrantAction;
import org.apache.camel.component.qdrant.QdrantActionException;
import org.apache.camel.component.qdrant.QdrantEndpoint;
import org.apache.camel.component.qdrant.QdrantHeaders;
import org.apache.camel.component.qdrant.QdrantTestSupport;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class QdrantDeleteCollectionIT extends QdrantTestSupport {
    @EndpointInject("qdrant:collectionForDeletion")
    QdrantEndpoint qdrantEndpoint;

    @Test
    @Order(1)
    void createCollection() {
        Exchange result = fluentTemplate.to(qdrantEndpoint)
                .withHeader(QdrantHeaders.ACTION, QdrantAction.CREATE_COLLECTION)
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
    void collectionInfoExistent() {
        Exchange result = fluentTemplate.to(qdrantEndpoint)
                .withHeader(QdrantHeaders.ACTION, QdrantAction.COLLECTION_INFO)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody()).isInstanceOf(Collections.CollectionInfo.class);
    }

    @Test
    @Order(3)
    void deleteCollection() {
        Exchange result = fluentTemplate.to(qdrantEndpoint)
                .withHeader(QdrantHeaders.ACTION, QdrantAction.DELETE_COLLECTION)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
    }

    @Test
    @Order(4)
    void collectionInfoNonExistent() {
        Exchange result = fluentTemplate.to(qdrantEndpoint)
                .withHeader(QdrantHeaders.ACTION, QdrantAction.COLLECTION_INFO)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isInstanceOf(QdrantActionException.class);

        final QdrantActionException exception = result.getException(QdrantActionException.class);
        final Throwable cause = exception.getCause();

        assertThat(cause).isNotNull();
        assertThat(cause).isInstanceOf(StatusRuntimeException.class);

        StatusRuntimeException statusRuntimeException = (StatusRuntimeException) cause;
        assertThat(statusRuntimeException.getStatus().getCode()).isEqualTo(Status.NOT_FOUND.getCode());
    }
}
