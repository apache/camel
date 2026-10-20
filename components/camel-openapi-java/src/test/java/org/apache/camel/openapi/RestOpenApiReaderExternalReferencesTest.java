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
package org.apache.camel.openapi;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.models.OpenAPI;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.support.DefaultComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The externalReferences option of a contract-first openApi element, applied to the contract served as api-doc.
 */
class RestOpenApiReaderExternalReferencesTest {

    private final CamelContext context = new DefaultCamelContext();

    @BeforeEach
    void start() {
        context.start();
    }

    @AfterEach
    void stop() {
        context.stop();
    }

    @Test
    void resolvesTheReferencedDocumentByDefault() throws Exception {
        RestDefinition rest = new RestDefinition();
        rest.openApi("external-references.yaml");

        OpenAPI openApi = read(rest);

        assertNotNull(openApi.getPaths().get("/pets/{petId}"));
        // the parser imported the Pet schema from external-references-pet.yaml
        assertNotNull(openApi.getComponents().getSchemas().get("Pet"));
    }

    @Test
    void failsWhenTheContractAllowsNoExternalReference() {
        RestDefinition rest = new RestDefinition();
        rest.openApi().specification("external-references.yaml").externalReferences("none");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> read(rest));

        assertTrue(e.getMessage().contains("external-references-pet.yaml#/components/schemas/Pet"), e.getMessage());
        assertTrue(e.getMessage().contains("externalReferences=none"), e.getMessage());
    }

    @Test
    void usesTheSettingOfTheRestOpenApiComponent() {
        context.addComponent("rest-openapi", new RestrictedComponent());
        RestDefinition rest = new RestDefinition();
        rest.openApi("external-references.yaml");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> read(rest));

        assertTrue(e.getMessage().contains("externalReferences=none"), e.getMessage());
    }

    private OpenAPI read(RestDefinition rest) throws Exception {
        return new RestOpenApiReader().read(context, List.of(rest), new BeanConfig(), context.getName(),
                context.getClassResolver());
    }

    /**
     * Stands in for the rest-openapi component, which is not on the classpath of this module. Public like the real
     * component, since the reader finds the setting through bean introspection.
     */
    public static class RestrictedComponent extends DefaultComponent {

        @SuppressWarnings("unused") // read through bean introspection
        public String getExternalReferences() {
            return "none";
        }

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
            return null;
        }
    }
}
