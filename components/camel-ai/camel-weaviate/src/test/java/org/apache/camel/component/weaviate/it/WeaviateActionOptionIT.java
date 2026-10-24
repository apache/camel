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
package org.apache.camel.component.weaviate.it;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.NoSuchHeaderException;
import org.apache.camel.component.weaviate.WeaviateTestSupport;
import org.apache.camel.component.weaviate.WeaviateVectorDbAction;
import org.apache.camel.component.weaviate.WeaviateVectorDbHeaders;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperties;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The action option of the endpoint selects the action, and the CamelWeaviateAction header wins over it.
 */
@DisabledIfSystemProperties({
        @DisabledIfSystemProperty(named = "weaviate.apikey", matches = ".*", disabledReason = "weaviate API Key provided"),
})
public class WeaviateActionOptionIT extends WeaviateTestSupport {
    private static final String COLLECTION = "WeaviateActionOptionCollection";

    @Test
    public void actionOption() {
        Exchange result = fluentTemplate.to("weaviate:test?action=CREATE_COLLECTION")
                .withHeader(WeaviateVectorDbHeaders.COLLECTION_NAME, COLLECTION)
                .request(Exchange.class);

        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody(Boolean.class)).isTrue();

        result = fluentTemplate.to("weaviate:test?action=DELETE_COLLECTION")
                .withHeader(WeaviateVectorDbHeaders.COLLECTION_NAME, COLLECTION)
                .request(Exchange.class);

        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody(Boolean.class)).isTrue();
    }

    @Test
    public void headerWinsOverActionOption() {
        // BM25_QUERY would read the body as the query text; CREATE fails on a body that is not the List of the vector
        Exchange result = fluentTemplate.to("weaviate:test?action=BM25_QUERY")
                .withHeader(WeaviateVectorDbHeaders.ACTION, WeaviateVectorDbAction.CREATE)
                .withHeader(WeaviateVectorDbHeaders.COLLECTION_NAME, COLLECTION)
                .withBody(new Object())
                .request(Exchange.class);

        assertThat(result.getException()).isInstanceOfSatisfying(InvalidPayloadException.class,
                e -> assertThat(e.getType()).isEqualTo(List.class));
    }

    @Test
    public void noAction() {
        Exchange result = fluentTemplate.to("weaviate:test")
                .withBody(new Object())
                .request(Exchange.class);

        assertThat(result.getException()).isInstanceOf(NoSuchHeaderException.class);
    }
}
