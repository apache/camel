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

package org.apache.camel.component.qdrant;

import java.util.List;
import java.util.Map;

import io.qdrant.client.VectorsFactory;
import io.qdrant.client.grpc.Points;
import org.apache.camel.Exchange;
import org.apache.camel.NoSuchHeaderException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.qdrant.client.PointIdFactory.id;
import static io.qdrant.client.ValueFactory.value;
import static org.assertj.core.api.Assertions.assertThat;

public class QdrantUpsertTest extends QdrantTestSupport {

    @DisplayName("Tests that trying to upsert without passing the action name triggers a failure")
    @Test
    public void upsertWithoutRequiredParameters() {
        Exchange result = fluentTemplate.to("qdrant:upsert")
                .withBody(
                        Points.PointStruct.newBuilder()
                                .setId(id(8))
                                .setVectors(VectorsFactory.vectors(List.of(3.5f, 4.5f)))
                                .putAllPayload(Map.of(
                                        "foo", value("hello"),
                                        "bar", value(1))))
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isInstanceOf(NoSuchHeaderException.class);
    }
}
