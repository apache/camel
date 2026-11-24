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
package org.apache.camel.component.es.integration;

import java.util.Map;

import co.elastic.clients.elasticsearch.security.CreateApiKeyResponse;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.es.ElasticsearchComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Logs in with an API key only: the components have no user and password.
 */
class ElasticsearchApiKeyIT extends ElasticsearchTestSupport {

    private static CreateApiKeyResponse apiKey;

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();
        if (apiKey == null) {
            // created with the user and password of the test container
            apiKey = client.security().createApiKey(b -> b.name("camel-elasticsearch-api-key-it"));
        }
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        configure(context.getComponent("elasticsearch", ElasticsearchComponent.class), apiKey.encoded());

        ElasticsearchComponent idAndKey = new ElasticsearchComponent();
        configure(idAndKey, apiKey.id() + ":" + apiKey.apiKey());
        context.addComponent("elasticsearch-id-key", idAndKey);

        ElasticsearchComponent wrongKey = new ElasticsearchComponent();
        configure(wrongKey, apiKey.id() + ":wrong");
        context.addComponent("elasticsearch-wrong-key", wrongKey);
        return context;
    }

    private static void configure(ElasticsearchComponent component, String key) {
        component.setEnableSSL(service.getSslContext().isPresent());
        component.setHostAddresses(service.getHttpHostAddress());
        service.getCertificatePath()
                .ifPresent(certificatePath -> component.setCertificatePath("file:%s".formatted(certificatePath)));
        component.setUser(null);
        component.setPassword(null);
        component.setApiKey(key);
    }

    @Test
    void indexAndGetWithTheEncodedApiKey() {
        Map<String, String> map = createIndexedData();
        String indexId = template.requestBody("direct:index", map, String.class);
        assertNotNull(indexId, "indexId should be set");

        Object document = template.requestBody("direct:get", indexId);
        assertNotNull(document, "the document should be found");
    }

    @Test
    void pingWithTheIdAndKey() {
        assertEquals(Boolean.TRUE, template.requestBody("direct:ping-id-key", "test", Boolean.class));
    }

    @Test
    void failsWithAWrongApiKey() {
        Map<String, String> map = createIndexedData();
        assertThrows(CamelExecutionException.class, () -> template.requestBody("direct:index-wrong-key", map));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:index")
                        .to("elasticsearch://elasticsearch?operation=Index&indexName=api-key");
                from("direct:get")
                        .to("elasticsearch://elasticsearch?operation=GetById&indexName=api-key");
                from("direct:ping-id-key")
                        .to("elasticsearch-id-key://elasticsearch?operation=Ping");
                from("direct:index-wrong-key")
                        .to("elasticsearch-wrong-key://elasticsearch?operation=Index&indexName=api-key");
            }
        };
    }
}
