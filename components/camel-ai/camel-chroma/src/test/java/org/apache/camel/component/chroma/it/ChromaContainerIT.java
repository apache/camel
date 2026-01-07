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
package org.apache.camel.component.chroma.it;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.component.chroma.ChromaAction;
import org.apache.camel.component.chroma.ChromaHeaders;
import org.apache.camel.component.chroma.ChromaTestSupport;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import tech.amikos.chromadb.Collection;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ChromaContainerIT extends ChromaTestSupport {
    private static final String COLLECTION = "ChromaITCollection";

    @Test
    @Order(1)
    public void createCollection() {
        Exchange result = fluentTemplate
                .to("chroma:" + COLLECTION)
                .withHeader(ChromaHeaders.ACTION, ChromaAction.CREATE_COLLECTION)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getHeader(ChromaHeaders.OPERATION_STATUS)).isEqualTo("SUCCESS");

        Collection collection = result.getMessage().getBody(Collection.class);
        assertThat(collection).isNotNull();
        assertThat(collection.getName()).isEqualTo(COLLECTION);
    }

    @Test
    @Order(2)
    public void getCollection() {
        Exchange result = fluentTemplate
                .to("chroma:" + COLLECTION)
                .withHeader(ChromaHeaders.ACTION, ChromaAction.GET_COLLECTION)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getHeader(ChromaHeaders.OPERATION_STATUS)).isEqualTo("SUCCESS");

        Collection collection = result.getMessage().getBody(Collection.class);
        assertThat(collection).isNotNull();
        assertThat(collection.getName()).isEqualTo(COLLECTION);
    }

    @Test
    @Order(3)
    public void addDocuments() {
        List<String> ids = Arrays.asList("id1", "id2", "id3");
        List<String> documents = Arrays.asList(
                "This is a document about cats",
                "This is a document about dogs",
                "This is a document about birds");

        Exchange result = fluentTemplate
                .to("chroma:" + COLLECTION)
                .withHeader(ChromaHeaders.ACTION, ChromaAction.ADD)
                .withHeader(ChromaHeaders.IDS, ids)
                .withBody(documents)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getHeader(ChromaHeaders.OPERATION_STATUS)).isEqualTo("SUCCESS");
    }

    @Test
    @Order(4)
    public void getDocuments() {
        List<String> ids = Arrays.asList("id1", "id2");

        Exchange result = fluentTemplate
                .to("chroma:" + COLLECTION)
                .withHeader(ChromaHeaders.ACTION, ChromaAction.GET)
                .withHeader(ChromaHeaders.IDS, ids)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getHeader(ChromaHeaders.OPERATION_STATUS)).isEqualTo("SUCCESS");

        Collection.GetResult getResult = result.getMessage().getBody(Collection.GetResult.class);
        assertThat(getResult).isNotNull();
        assertThat(getResult.getIds()).containsExactlyInAnyOrder("id1", "id2");
    }

    @Test
    @Order(5)
    public void queryDocuments() {
        List<String> queryTexts = Collections.singletonList("cats");

        Exchange result = fluentTemplate
                .to("chroma:" + COLLECTION)
                .withHeader(ChromaHeaders.ACTION, ChromaAction.QUERY)
                .withHeader(ChromaHeaders.N_RESULTS, 2)
                .withBody(queryTexts)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getHeader(ChromaHeaders.OPERATION_STATUS)).isEqualTo("SUCCESS");

        Collection.QueryResponse queryResponse = result.getMessage().getBody(Collection.QueryResponse.class);
        assertThat(queryResponse).isNotNull();
    }

    @Test
    @Order(6)
    public void deleteDocuments() {
        List<String> ids = Collections.singletonList("id3");

        Exchange result = fluentTemplate
                .to("chroma:" + COLLECTION)
                .withHeader(ChromaHeaders.ACTION, ChromaAction.DELETE)
                .withHeader(ChromaHeaders.IDS, ids)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getHeader(ChromaHeaders.OPERATION_STATUS)).isEqualTo("SUCCESS");
    }

    @Test
    @Order(7)
    public void verifyDocumentDeleted() {
        List<String> ids = Collections.singletonList("id3");

        Exchange result = fluentTemplate
                .to("chroma:" + COLLECTION)
                .withHeader(ChromaHeaders.ACTION, ChromaAction.GET)
                .withHeader(ChromaHeaders.IDS, ids)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();

        Collection.GetResult getResult = result.getMessage().getBody(Collection.GetResult.class);
        assertThat(getResult).isNotNull();
        assertThat(getResult.getIds()).isEmpty();
    }

    @Test
    @Order(10)
    public void deleteCollection() {
        Exchange result = fluentTemplate
                .to("chroma:" + COLLECTION)
                .withHeader(ChromaHeaders.ACTION, ChromaAction.DELETE_COLLECTION)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getHeader(ChromaHeaders.OPERATION_STATUS)).isEqualTo("SUCCESS");
    }
}
