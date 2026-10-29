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

import io.qdrant.client.grpc.Collections.VectorParams;
import io.qdrant.client.grpc.Points;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.NoSuchHeaderException;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The action option of the endpoint selects the action, and the CamelQdrantAction header wins over it. The body is of
 * no type an action takes, so the action fails before it calls Qdrant, with the type it needs: no server is needed.
 */
public class QdrantActionOptionTest extends CamelTestSupport {

    @Test
    public void actionOption() {
        Exchange result = fluentTemplate.to("qdrant:test?action=CREATE_COLLECTION")
                .withBody(new Object())
                .request(Exchange.class);

        assertThat(result.getException()).isInstanceOfSatisfying(InvalidPayloadException.class,
                e -> assertThat(e.getType()).isEqualTo(VectorParams.class));
    }

    @Test
    public void headerWinsOverActionOption() {
        Exchange result = fluentTemplate.to("qdrant:test?action=CREATE_COLLECTION")
                .withHeader(QdrantHeaders.ACTION, QdrantAction.DELETE)
                .withBody(new Object())
                .request(Exchange.class);

        assertThat(result.getException()).isInstanceOfSatisfying(InvalidPayloadException.class,
                e -> assertThat(e.getType()).isEqualTo(Points.PointsSelector.class));
    }

    @Test
    public void noAction() {
        Exchange result = fluentTemplate.to("qdrant:test")
                .withBody(new Object())
                .request(Exchange.class);

        assertThat(result.getException()).isInstanceOf(NoSuchHeaderException.class);
    }
}
