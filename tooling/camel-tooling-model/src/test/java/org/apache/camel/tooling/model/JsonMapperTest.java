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
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void testShouldWriteInternalOnlyWhenSet() {
        ComponentModel model = new ComponentModel();
        String json = JsonMapper.createParameterJsonSchema(model);
        assertFalse(json.contains("\"internal\""));
        assertFalse(JsonMapper.generateComponentModel(json).isInternal());

        model.setInternal(true);
        json = JsonMapper.createParameterJsonSchema(model);
        assertTrue(json.contains("\"internal\": true"));
        assertTrue(JsonMapper.generateComponentModel(json).isInternal());
    }

    @Test
    void testShouldWriteMultiEnumOnlyWhenSet() {
        // CAMEL-25739: an option that takes several of its enum values, separated by comma
        ComponentModel model = new ComponentModel();
        ComponentModel.EndpointOptionModel several = new ComponentModel.EndpointOptionModel();
        several.setName("eventTypes");
        several.setKind("parameter");
        several.setType("enum");
        several.setEnums(List.of("CREATED", "UPDATED"));
        several.setMultiEnum(true);
        model.addEndpointOption(several);
        ComponentModel.EndpointOptionModel one = new ComponentModel.EndpointOptionModel();
        one.setName("action");
        one.setKind("parameter");
        one.setType("enum");
        one.setEnums(List.of("PUT", "GET"));
        model.addEndpointOption(one);

        String json = JsonMapper.createParameterJsonSchema(model);
        assertEquals(1, json.split("\"multiEnum\": true", -1).length - 1, json);

        ComponentModel model2 = JsonMapper.generateComponentModel(json);
        assertTrue(model2.getEndpointOptions().get(0).isMultiEnum());
        assertFalse(model2.getEndpointOptions().get(1).isMultiEnum());
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

    @Test
    void testShouldWriteBodyTypesOnlyWhenSet() {
        // CAMEL-24961: the body a step needs and the body it leaves
        ComponentModel model = new ComponentModel();
        String json = JsonMapper.createParameterJsonSchema(model);
        assertFalse(json.contains("\"bodyTypes\""));
        assertTrue(JsonMapper.generateComponentModel(json).getBodyTypes().isEmpty());

        BodyTypeModel in = new BodyTypeModel();
        in.setCarriers(List.of("text", "bytes", "stream", "file"));
        in.setFormat("xml");
        BodyTypeModel out = new BodyTypeModel();
        out.setCarriers(List.of("text"));
        out.setJavaType("java.lang.String");
        out.setDependsOn(List.of("resultHandlerFactory"));
        BodyTypeModel.OptionBodyTypeModel bytes = new BodyTypeModel.OptionBodyTypeModel();
        bytes.setOption("output");
        bytes.setValue("bytes");
        bytes.setCarriers(List.of("bytes"));
        bytes.setJavaType("byte[]");
        out.getByOption().add(bytes);
        out.setDescription("The result of the transformation");
        model.getBodyTypes().put(BodyTypeModel.PRODUCER_IN, in);
        model.getBodyTypes().put(BodyTypeModel.PRODUCER_OUT, out);

        json = JsonMapper.createParameterJsonSchema(model);
        assertTrue(json.contains("\"bodyTypes\""), json);
        ComponentModel model2 = JsonMapper.generateComponentModel(json);
        assertEquals(List.of(BodyTypeModel.PRODUCER_IN, BodyTypeModel.PRODUCER_OUT),
                List.copyOf(model2.getBodyTypes().keySet()));
        assertNull(model2.getBodyType(BodyTypeModel.CONSUMER_OUT));

        BodyTypeModel in2 = model2.getBodyType(BodyTypeModel.PRODUCER_IN);
        assertEquals(List.of("text", "bytes", "stream", "file"), in2.getCarriers());
        assertEquals("xml", in2.getFormat());
        assertNull(in2.getJavaType());
        assertTrue(in2.getDependsOn().isEmpty());
        assertTrue(in2.getByOption().isEmpty());

        BodyTypeModel out2 = model2.getBodyType(BodyTypeModel.PRODUCER_OUT);
        assertEquals(List.of("text"), out2.getCarriers());
        assertEquals("java.lang.String", out2.getJavaType());
        assertEquals(List.of("resultHandlerFactory"), out2.getDependsOn());
        assertEquals("The result of the transformation", out2.getDescription());
        assertEquals(1, out2.getByOption().size());
        BodyTypeModel.OptionBodyTypeModel bytes2 = out2.getByOption().get(0);
        assertEquals("output", bytes2.getOption());
        assertEquals("bytes", bytes2.getValue());
        assertEquals(List.of("bytes"), bytes2.getCarriers());
        assertEquals("byte[]", bytes2.getJavaType());
        assertFalse(out2.isUnchanged());
        assertFalse(out2.isUnknown());
    }

    @Test
    void testShouldWriteBodyTypesOfDataFormatsAndLanguages() {
        DataFormatModel dataFormat = new DataFormatModel();
        dataFormat.setName("jackson");
        BodyTypeModel unmarshalOut = new BodyTypeModel();
        unmarshalOut.setCarriers(List.of("map", "list"));
        unmarshalOut.setDependsOn(List.of("unmarshalType"));
        dataFormat.getBodyTypes().put(BodyTypeModel.UNMARSHAL_OUT, unmarshalOut);
        DataFormatModel dataFormat2
                = JsonMapper.generateDataFormatModel(JsonMapper.createParameterJsonSchema(dataFormat));
        assertEquals(List.of("map", "list"), dataFormat2.getBodyType(BodyTypeModel.UNMARSHAL_OUT).getCarriers());
        assertEquals(List.of("unmarshalType"), dataFormat2.getBodyType(BodyTypeModel.UNMARSHAL_OUT).getDependsOn());

        LanguageModel language = new LanguageModel();
        language.setName("constant");
        BodyTypeModel expressionOut = new BodyTypeModel();
        expressionOut.setCarriers(List.of("text"));
        language.getBodyTypes().put(BodyTypeModel.EXPRESSION_OUT, expressionOut);
        LanguageModel language2 = JsonMapper.generateLanguageModel(JsonMapper.createParameterJsonSchema(language));
        assertEquals(List.of("text"), language2.getBodyType(BodyTypeModel.EXPRESSION_OUT).getCarriers());
        assertNull(language2.getBodyType(BodyTypeModel.EXPRESSION_IN));
    }
}
