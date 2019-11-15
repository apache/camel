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
package org.apache.camel.component.salesforce;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.module.jsonSchema.JsonSchema;
import com.fasterxml.jackson.module.jsonSchema.types.ObjectSchema;
import org.apache.camel.component.extension.MetaDataExtension;
import org.apache.camel.component.extension.MetaDataExtension.MetaData;
import org.apache.camel.component.salesforce.api.utils.JsonUtils;
import org.apache.camel.component.salesforce.internal.client.RestClient;
import org.apache.camel.component.salesforce.internal.client.RestClient.ResponseCallback;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Test;
import org.mockito.stubbing.Answer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

public class SalesforceMetaDataExtensionTest {

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final Class<Map<String, List<String>>> HEADERS_TYPE = (Class)Map.class;

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
            doAnswer(provideStreamToCallback(stream)).when(restClient).getDescription(eq("Account"), any(HEADERS_TYPE), any(ResponseCallback.class));
            maybeMeta = metadata.meta(Collections.singletonMap(SalesforceEndpointConfig.SOBJECT_NAME, "Account"));
        }

        assertThat(maybeMeta).isPresent();

        final MetaData meta = maybeMeta.get();
        assertThat(meta.getAttribute(MetaDataExtension.MetaData.JAVA_TYPE)).isEqualTo(JsonNode.class);
        assertThat(meta.getAttribute(MetaDataExtension.MetaData.CONTENT_TYPE)).isEqualTo("application/schema+json");

        final ObjectSchema payload = meta.getPayload(ObjectSchema.class);
        assertThat(payload).isNotNull();

        assertThat(schemaFor(payload, "Merchandise__c")).isPresent();
        assertThat(schemaFor(payload, "QueryRecordsMerchandise__c")).isPresent();
    }

    @Test
    public void shouldProvideSalesforceObjectTypes() throws IOException {
        final Optional<MetaData> maybeMeta;
        try (InputStream stream = resource("/globalObjects.json")) {
            doAnswer(provideStreamToCallback(stream)).when(restClient).getGlobalObjects(any(HEADERS_TYPE), any(ResponseCallback.class));
            maybeMeta = metadata.meta(Collections.emptyMap());
        }

        assertThat(maybeMeta).isPresent();

        final MetaData meta = maybeMeta.get();
        assertThat(meta.getAttribute(MetaDataExtension.MetaData.JAVA_TYPE)).isEqualTo(JsonNode.class);
        assertThat(meta.getAttribute(MetaDataExtension.MetaData.CONTENT_TYPE)).isEqualTo("application/schema+json");

        final ObjectSchema payload = meta.getPayload(ObjectSchema.class);
        assertThat(payload).isNotNull();

        @SuppressWarnings({"unchecked", "rawtypes"})
        final Set<JsonSchema> oneOf = (Set)payload.getOneOf();
        assertThat(oneOf).hasSize(4);

        assertThat(schemaFor(payload, "AcceptedEventRelation")).isPresent().hasValueSatisfying(schema -> assertThat(schema.getTitle()).isEqualTo("Accepted Event Relation"));
        assertThat(schemaFor(payload, "Account")).isPresent().hasValueSatisfying(schema -> assertThat(schema.getTitle()).isEqualTo("Account"));
        assertThat(schemaFor(payload, "AccountCleanInfo")).isPresent().hasValueSatisfying(schema -> assertThat(schema.getTitle()).isEqualTo("Account Clean Info"));
        assertThat(schemaFor(payload, "AccountContactRole")).isPresent().hasValueSatisfying(schema -> assertThat(schema.getTitle()).isEqualTo("Account Contact Role"));
    }

    static Answer<Void> provideStreamToCallback(final InputStream stream) {
        return invocation -> {
            final ResponseCallback callback = (ResponseCallback)Arrays.stream(invocation.getArguments()).filter(ResponseCallback.class::isInstance).findFirst().get();
            callback.onResponse(stream, Collections.emptyMap(), null);

            return null;
        };
    }

    static InputStream resource(final String path) {
        return SalesforceMetaDataExtensionTest.class.getResourceAsStream(path);
    }

    static Optional<ObjectSchema> schemaFor(final ObjectSchema schema, final String sObjectName) {
        @SuppressWarnings({"unchecked", "rawtypes"})
        final Set<ObjectSchema> oneOf = (Set)schema.getOneOf();

        return StreamSupport.stream(oneOf.spliterator(), false).filter(idMatches(JsonUtils.DEFAULT_ID_PREFIX + ":" + sObjectName)).findAny();
    }

    static String valueAt(final JsonNode payload, final int idx, final String name) {
        return payload.get(idx).get(name).asText();
    }

    private static Predicate<JsonSchema> idMatches(final String wantedId) {
        return schema -> wantedId.equals(schema.getId());
    }
}
