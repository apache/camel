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

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleFirestoreProducerDocumentDataTest {

    private Map<String, Object> documentData(String body) throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            GoogleFirestoreComponent component = context.getComponent("google-firestore", GoogleFirestoreComponent.class);
            // the endpoint is deliberately not started, so no firestore client is built
            GoogleFirestoreEndpoint endpoint = (GoogleFirestoreEndpoint) component.createEndpoint("google-firestore://users");
            Exchange exchange = new DefaultExchange(context);
            exchange.getIn().setBody(body);
            return new GoogleFirestoreProducer(endpoint).getDocumentData(exchange);
        }
    }

    @Test
    void aJsonStringIsParsed() throws Exception {
        assertThat(documentData("{\"name\":\"camel\"}")).containsEntry("name", "camel");
    }

    @Test
    void contentAfterTheFirstJsonValueIsIgnored() throws Exception {
        assertThat(documentData("{\"name\":\"camel\"} {\"name\":\"other\"}")).containsOnly(Map.entry("name", "camel"));
    }

    @Test
    void malformedJsonIsAnInvalidPayload() {
        assertThatThrownBy(() -> documentData("{not json}")).isInstanceOf(InvalidPayloadException.class);
    }
}
