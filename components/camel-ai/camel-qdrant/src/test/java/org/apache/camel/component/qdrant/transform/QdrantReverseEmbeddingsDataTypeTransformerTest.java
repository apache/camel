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
package org.apache.camel.component.qdrant.transform;

import java.util.List;

import io.qdrant.client.PointIdFactory;
import io.qdrant.client.ValueFactory;
import io.qdrant.client.grpc.Points;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.DataType;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QdrantReverseEmbeddingsDataTypeTransformerTest {

    private final DefaultCamelContext camelContext = new DefaultCamelContext();

    private final QdrantReverseEmbeddingsDataTypeTransformer transformer = new QdrantReverseEmbeddingsDataTypeTransformer();

    @Test
    void theBodyTypesSayTheBody() throws Exception {
        // CAMEL-25928: the points of a similarity search, as transformerIn says, and the List of String transformerOut
        // says
        BodyTypes bodyTypes = QdrantReverseEmbeddingsDataTypeTransformer.class.getAnnotation(BodyTypes.class);
        assertThat(bodyTypes.transformerIn().value()).containsExactly(Carrier.LIST);
        assertThat(bodyTypes.transformerIn().javaType()).isEqualTo("java.util.List<io.qdrant.client.grpc.Points.ScoredPoint>");
        assertThat(bodyTypes.transformerOut().value()).containsExactly(Carrier.LIST);
        assertThat(bodyTypes.transformerOut().javaType()).isEqualTo("java.util.List<java.lang.String>");

        Points.ScoredPoint withText = Points.ScoredPoint.newBuilder().setId(PointIdFactory.id(1)).setScore(0.9f)
                .putPayload("text_segment", ValueFactory.value("hello")).build();
        Points.ScoredPoint withoutText = Points.ScoredPoint.newBuilder().setId(PointIdFactory.id(2)).setScore(0.5f).build();

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setBody(List.of(withText, withoutText));
        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);
        assertThat(exchange.getMessage().getBody()).isInstanceOf(List.class);
        assertThat(exchange.getMessage().getBody(List.class)).containsExactly("hello", "");

        // a null body fails
        Exchange empty = new DefaultExchange(camelContext);
        assertThatThrownBy(() -> transformer.transform(empty.getMessage(), DataType.ANY, DataType.ANY))
                .isInstanceOf(NullPointerException.class);
    }
}
