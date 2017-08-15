/**
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
package org.apache.camel.component.salesforce;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

import org.apache.camel.component.extension.MetaDataExtension;
import org.apache.camel.component.extension.MetaDataExtension.MetaData;
import org.apache.camel.component.salesforce.internal.client.RestClient;
import org.apache.camel.component.salesforce.internal.client.RestClient.ResponseCallback;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Test;
import org.mockito.stubbing.Answer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

public class SalesforceMetaDataExtensionTest {

    final SalesforceComponent component = new SalesforceComponent();

    final MetaDataExtension metadata;

    final RestClient restClient = mock(RestClient.class);

    public SalesforceMetaDataExtensionTest() {
        component.setCamelContext(new DefaultCamelContext());
        SalesforceClientTemplate.restClientSupplier = (c, p) -> restClient;
        metadata = component.getExtension(MetaDataExtension.class).get();
    }

    @Test
    public void componentShouldProvideMetadataExtension() {
        assertThat(component.getExtension(MetaDataExtension.class)).isPresent();
    }

    @Test
    public void shouldProvideSalesforceObjectFields() throws IOException {
        final Optional<MetaData> maybeMeta;
        try (InputStream stream = resource("/objectDescription.json")) {
            doAnswer(provideStreamToCallback(stream)).when(restClient).getDescription(eq("Account"),
                any(ResponseCallback.class));
            maybeMeta = metadata.meta(Collections.singletonMap("objectType", "Account"));
        }

        assertThat(maybeMeta).isPresent();

        final MetaData meta = maybeMeta.get();
        assertThat(meta.getAttribute(MetaDataExtension.MetaData.JAVA_TYPE)).isEqualTo(JsonNode.class);
        assertThat(meta.getAttribute(MetaDataExtension.MetaData.CONTENT_TYPE)).isEqualTo("application/json");

        final JsonNode payload = meta.getPayload(JsonNode.class);
        assertThat(payload).isNotNull();
        assertThat(valueAt(payload, 0, "field")).isEqualTo("Id");
        assertThat(valueAt(payload, 0, "type")).isEqualTo("id");
        assertThat(valueAt(payload, 1, "field")).isEqualTo("OwnerId");
        assertThat(valueAt(payload, 1, "type")).isEqualTo("reference");
        assertThat(valueAt(payload, 2, "field")).isEqualTo("IsDeleted");
        assertThat(valueAt(payload, 2, "type")).isEqualTo("boolean");
        assertThat(valueAt(payload, 3, "field")).isEqualTo("Name");
        assertThat(valueAt(payload, 3, "type")).isEqualTo("string");
    }

    @Test
    public void shouldProvideSalesforceObjectTypes() throws IOException {
        final Optional<MetaData> maybeMeta;
        try (InputStream stream = resource("/globalObjects.json")) {
            doAnswer(provideStreamToCallback(stream)).when(restClient).getGlobalObjects(any(ResponseCallback.class));
            maybeMeta = metadata.meta(Collections.emptyMap());
        }

        assertThat(maybeMeta).isPresent();

        final MetaData meta = maybeMeta.get();
        assertThat(meta.getAttribute(MetaDataExtension.MetaData.JAVA_TYPE)).isEqualTo(JsonNode.class);
        assertThat(meta.getAttribute(MetaDataExtension.MetaData.CONTENT_TYPE)).isEqualTo("application/json");

        final JsonNode payload = meta.getPayload(JsonNode.class);
        assertThat(payload).isNotNull();
        assertThat(valueAt(payload, 0, "sObjectName")).isEqualTo("AcceptedEventRelation");
        assertThat(valueAt(payload, 0, "label")).isEqualTo("Accepted Event Relation");
        assertThat(valueAt(payload, 1, "sObjectName")).isEqualTo("Account");
        assertThat(valueAt(payload, 1, "label")).isEqualTo("Account");
        assertThat(valueAt(payload, 2, "sObjectName")).isEqualTo("AccountCleanInfo");
        assertThat(valueAt(payload, 2, "label")).isEqualTo("Account Clean Info");
        assertThat(valueAt(payload, 3, "sObjectName")).isEqualTo("AccountContactRole");
        assertThat(valueAt(payload, 3, "label")).isEqualTo("Account Contact Role");
    }

    InputStream resource(final String path) {
        return SalesforceMetaDataExtensionTest.class.getResourceAsStream(path);
    }

    String valueAt(final JsonNode payload, final int idx, final String name) {
        return payload.get(idx).get(name).asText();
    }

    static Answer<Void> provideStreamToCallback(final InputStream stream) {
        return invocation -> {
            final ResponseCallback callback = (ResponseCallback) Arrays.stream(invocation.getArguments())
                .filter(ResponseCallback.class::isInstance).findFirst().get();
            callback.onResponse(stream, null);

            return null;
        };
    }
}
