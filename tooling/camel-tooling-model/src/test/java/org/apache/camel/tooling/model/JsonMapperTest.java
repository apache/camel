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
package org.apache.camel.tooling.model;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.apache.camel.tooling.model.ComponentModel.EndpointHeaderModel;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The unit test class for {@link JsonMapper}.
 */
class JsonMapperTest {

    @Test
    void testShouldSerializeAndDeserializeComponentWithoutHeaders() {
        ComponentModel model = new ComponentModel();
        String json = JsonMapper.createParameterJsonSchema(model);
        assertFalse(json.contains("\"headers\""));
        ComponentModel model2 = JsonMapper.generateComponentModel(json);
        assertTrue(model2.getEndpointHeaders().isEmpty());
    }

    @Test
    void testShouldSerializeAndDeserializeComponentWithOneHeader() {
        ComponentModel model = new ComponentModel();
        EndpointHeaderModel header = new EndpointHeaderModel();
        header.setName("Some Name");
        header.setIndex(1);
        header.setDescription("Some Description");
        header.setConstantName("Some constant Name");
        model.addEndpointHeader(header);
        String json = JsonMapper.createParameterJsonSchema(model);
        ComponentModel model2 = JsonMapper.generateComponentModel(json);
        List<EndpointHeaderModel> headers = model2.getEndpointHeaders();
        assertEquals(1, headers.size());
        assertEquals(header.getName(), headers.get(0).getName());
        assertEquals(0, headers.get(0).getIndex());
        assertEquals(header.getDescription(), headers.get(0).getDescription());
        assertEquals(header.getConstantName(), headers.get(0).getConstantName());
    }

    @Test
    void testShouldSerializeAndDeserializeComponentWithSeveralHeaders() {
        ComponentModel model = new ComponentModel();
        EndpointHeaderModel header1 = new EndpointHeaderModel();
        header1.setName("Some Name");
        header1.setDescription("Some Description");
        header1.setConstantName("Some constant Name");
        model.addEndpointHeader(header1);
        EndpointHeaderModel header2 = new EndpointHeaderModel();
        header2.setName("Some Name 2");
        header2.setDescription("Some Description 2");
        header2.setConstantName("Some constant Name 2");
        model.addEndpointHeader(header2);
        String json = JsonMapper.createParameterJsonSchema(model);
        ComponentModel model2 = JsonMapper.generateComponentModel(json);
        List<EndpointHeaderModel> headers = model2.getEndpointHeaders();
        assertEquals(2, headers.size());
        assertEquals(header1.getName(), headers.get(0).getName());
        assertEquals(header1.getDescription(), headers.get(0).getDescription());
        assertEquals(header1.getConstantName(), headers.get(0).getConstantName());
        assertEquals(header2.getName(), headers.get(1).getName());
        assertEquals(header2.getDescription(), headers.get(1).getDescription());
        assertEquals(header2.getConstantName(), headers.get(1).getConstantName());
    }

    @Test
    void testShouldSerializeAndDeserializeApiReference() {
        ApiReferenceModel model = new ApiReferenceModel();
        model.setName("Message");
        model.setTitle("Message");
        model.setJavaType("org.apache.camel.Message");
        model.setDescription("The body and headers of the exchange");
        model.setArtifactId("camel-api");
        ApiReferenceModel.ApiMethodOptionModel method = new ApiReferenceModel.ApiMethodOptionModel();
        method.setName("getBody");
        method.setJavaType("java.lang.Object");
        method.setDescription("The body, converted when a type is given");
        method.setImportant(true);
        method.addSignature("Object getBody()");
        method.addSignature("<T> T getBody(Class<T> type)");
        method.addExample("message.getBody(String.class)");
        model.addOption(method);

        String json = JsonMapper.createParameterJsonSchema(model);
        assertTrue(json.contains("\"api\""));
        assertTrue(json.contains("\"kind\": \"api\""));

        BaseModel<?> generic = JsonMapper.generateModel(json);
        assertTrue(generic instanceof ApiReferenceModel);
        ApiReferenceModel model2 = JsonMapper.generateApiReferenceModel(json);
        assertEquals(Kind.api, model2.getKind());
        assertEquals("Message", model2.getName());
        assertEquals("org.apache.camel.Message", model2.getJavaType());
        assertEquals("camel-api", model2.getArtifactId());
        assertEquals(1, model2.getOptions().size());
        ApiReferenceModel.ApiMethodOptionModel m = model2.getOptions().get(0);
        assertEquals("getBody", m.getName());
        assertEquals("java.lang.Object", m.getJavaType());
        assertEquals(method.getDescription(), m.getDescription());
        assertTrue(m.isImportant());
        assertFalse(m.isDeprecated());
        assertEquals(List.of("Object getBody()", "<T> T getBody(Class<T> type)"), m.getSignatures());
        assertEquals(List.of("message.getBody(String.class)"), m.getExamples());
    }
}
