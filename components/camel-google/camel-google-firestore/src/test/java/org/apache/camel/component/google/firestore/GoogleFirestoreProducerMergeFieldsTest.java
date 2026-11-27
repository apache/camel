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
package org.apache.camel.component.google.firestore;

import java.util.List;
import java.util.Map;

import com.google.api.core.ApiFutures;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.FieldPath;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.SetOptions;
import com.google.cloud.firestore.WriteResult;
import org.apache.camel.CatalogCamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The CamelGoogleFirestoreMergeFields header of setDocument, with a mocked Firestore client.
 */
class GoogleFirestoreProducerMergeFieldsTest {

    private final DocumentReference document = mock(DocumentReference.class);
    private DefaultCamelContext context;
    private GoogleFirestoreProducer producer;

    @BeforeEach
    void setUp() throws Exception {
        Firestore firestore = mock(Firestore.class);
        CollectionReference collection = mock(CollectionReference.class);
        when(firestore.collection("users")).thenReturn(collection);
        when(collection.document("doc1")).thenReturn(document);
        when(document.set(anyMap(), any(SetOptions.class))).thenReturn(ApiFutures.immediateFuture(mock(WriteResult.class)));

        context = new DefaultCamelContext();
        context.getRegistry().bind("firestore", firestore);
        context.start();
        GoogleFirestoreEndpoint endpoint = context.getEndpoint(
                "google-firestore://users?documentId=doc1&firestoreClient=#firestore", GoogleFirestoreEndpoint.class);
        producer = new GoogleFirestoreProducer(endpoint);
    }

    @AfterEach
    void tearDown() {
        context.stop();
    }

    private void setDocument(List<?> mergeFields) throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(Map.of("address", Map.of("city", "Rome")));
        exchange.getIn().setHeader(GoogleFirestoreConstants.MERGE, true);
        exchange.getIn().setHeader(GoogleFirestoreConstants.MERGE_FIELDS, mergeFields);
        producer.process(exchange);
    }

    @Test
    void aDottedNameIsANestedField() throws Exception {
        setDocument(List.of("address.city"));

        verify(document).set(anyMap(), eq(
                SetOptions.mergeFieldPaths(List.of(FieldPath.of("address", "city")))));
    }

    @Test
    void aFieldPathIsUsedAsItIs() throws Exception {
        setDocument(List.of(FieldPath.of("address.city")));

        verify(document).set(anyMap(), eq(
                SetOptions.mergeFieldPaths(List.of(FieldPath.of("address.city")))));
    }

    @Test
    void aNameThatIsNotADotSeparatedPathIsOneTopLevelField() throws Exception {
        // FieldPath.fromDotSeparatedString rejects ~ * / [ ], and a..b has an empty segment: such names are taken
        // as they are, as before
        setDocument(List.of("price/unit", "address..city"));

        verify(document).set(anyMap(), eq(
                SetOptions.mergeFieldPaths(List.of(FieldPath.of("price/unit"), FieldPath.of("address..city")))));
    }

    @Test
    void setDocumentIsTheDefaultOperationInTheMetadata() throws Exception {
        JsonObject json = (JsonObject) Jsoner.deserialize(
                ((CatalogCamelContext) context).getComponentParameterJsonSchema("google-firestore"));
        Map<String, Object> properties = json.getMap("properties");

        assertThat(((Map<?, ?>) properties.get("operation")).get("defaultValue")).isEqualTo("setDocument");
    }
}
