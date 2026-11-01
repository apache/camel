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
    void testShouldReadAndWriteEipBodyTypes() {
        // CAMEL-25928: the body after an EIP, its steps, and the side of another model it comes from
        EipModel model = new EipModel();
        model.setName("marshal");
        String json = JsonMapper.createParameterJsonSchema(model);
        assertFalse(json.contains("\"bodyTypes\""));
        assertTrue(JsonMapper.generateEipModel(json).getBodyTypes().isEmpty());

        BodyTypeModel out = new BodyTypeModel();
        out.setFrom("marshalOut");
        out.setDependsOn(List.of("variableReceive"));
        BodyTypeModel steps = new BodyTypeModel();
        steps.setCarriers(List.of("unchanged"));
        model.getBodyTypes().put(BodyTypeModel.EIP_OUT, out);
        model.getBodyTypes().put(BodyTypeModel.EIP_STEPS, steps);

        json = JsonMapper.createParameterJsonSchema(model);
        assertTrue(json.contains("\"bodyTypes\""), json);
        assertFalse(json.contains("\"carriers\": []"), json);
        EipModel model2 = JsonMapper.generateEipModel(json);
        assertEquals(List.of(BodyTypeModel.EIP_OUT, BodyTypeModel.EIP_STEPS), List.copyOf(model2.getBodyTypes().keySet()));

        BodyTypeModel out2 = model2.getBodyType(BodyTypeModel.EIP_OUT);
        assertEquals("marshalOut", out2.getFrom());
        assertTrue(out2.getCarriers().isEmpty());
        assertFalse(out2.isUnknown());
        assertEquals(List.of("variableReceive"), out2.getDependsOn());
        assertEquals("from:marshalOut", out2.toString());
        assertTrue(model2.getBodyType(BodyTypeModel.EIP_STEPS).isUnchanged());
        assertEquals("unchanged", model2.getBodyType(BodyTypeModel.EIP_STEPS).toString());
    }

    @Test
    void testShouldReadAndWriteTransformerBodyTypes() {
        // CAMEL-25928: the body a data type transformer takes and the body it leaves
        TransformerModel model = new TransformerModel();
        model.setName("application-json");
        model.setFrom("text-plain");
        model.setTo("application-json");
        String json = JsonMapper.createParameterJsonSchema(model);
        assertFalse(json.contains("\"bodyTypes\""));
        assertTrue(JsonMapper.generateTransformerModel(json).getBodyTypes().isEmpty());

        BodyTypeModel in = new BodyTypeModel();
        in.setCarriers(List.of("any"));
        BodyTypeModel out = new BodyTypeModel();
        out.setCarriers(List.of("bytes"));
        out.setJavaType("byte[]");
        out.setFormat("json");
        model.getBodyTypes().put(BodyTypeModel.TRANSFORMER_IN, in);
        model.getBodyTypes().put(BodyTypeModel.TRANSFORMER_OUT, out);

        json = JsonMapper.createParameterJsonSchema(model);
        assertTrue(json.contains("\"bodyTypes\""), json);
        TransformerModel model2 = JsonMapper.generateTransformerModel(json);
        assertEquals("text-plain", model2.getFrom());
        assertEquals("application-json", model2.getTo());
        assertEquals(List.of(BodyTypeModel.TRANSFORMER_IN, BodyTypeModel.TRANSFORMER_OUT),
                List.copyOf(model2.getBodyTypes().keySet()));
        assertEquals(List.of("any"), model2.getBodyType(BodyTypeModel.TRANSFORMER_IN).getCarriers());
        BodyTypeModel out2 = model2.getBodyType(BodyTypeModel.TRANSFORMER_OUT);
        assertEquals(List.of("bytes"), out2.getCarriers());
        assertEquals("byte[]", out2.getJavaType());
        assertEquals("json", out2.getFormat());
    }

    @Test
    void testShouldReadAndWriteJavaTypeOption() {
        // CAMEL-25928: the option that names the Java type of the body, such as resultType of a language
        LanguageModel model = new LanguageModel();
        model.setName("simple");
        BodyTypeModel out = new BodyTypeModel();
        out.setCarriers(List.of("unknown"));
        out.setJavaTypeOption("resultType");
        model.getBodyTypes().put(BodyTypeModel.EXPRESSION_OUT, out);

        String json = JsonMapper.createParameterJsonSchema(model);
        assertTrue(json.contains("\"javaTypeOption\": \"resultType\""), json);
        BodyTypeModel out2 = JsonMapper.generateLanguageModel(json).getBodyType(BodyTypeModel.EXPRESSION_OUT);
        assertEquals("resultType", out2.getJavaTypeOption());
        assertTrue(out2.isUnknown());
        assertEquals("unknown (javaTypeOption:resultType)", out2.toString());

        // and for an option value: the class of the one row with outputType=SelectOne
        BodyTypeModel.OptionBodyTypeModel selectOne = new BodyTypeModel.OptionBodyTypeModel();
        selectOne.setOption("outputType");
        selectOne.setValue("SelectOne");
        selectOne.setCarriers(List.of("map", "null"));
        selectOne.setJavaTypeOption("outputClass");
        out.getByOption().add(selectOne);
        BodyTypeModel.OptionBodyTypeModel selectOne2 = JsonMapper
                .generateLanguageModel(JsonMapper.createParameterJsonSchema(model))
                .getBodyType(BodyTypeModel.EXPRESSION_OUT).getByOption().get(0);
        assertEquals("outputClass", selectOne2.getJavaTypeOption());
        assertEquals("outputType=SelectOne: map, null (javaTypeOption:outputClass)", selectOne2.toString());
    }

    @Test
    void testCarrierOfJavaType() {
        // CAMEL-25928: the carrier of the class a type option names, in the names Camel takes
        assertEquals("text", BodyTypeModel.carrierOf("String"));
        assertEquals("text", BodyTypeModel.carrierOf("java.lang.String"));
        assertEquals("bytes", BodyTypeModel.carrierOf("byte[]"));
        assertEquals("bytes", BodyTypeModel.carrierOf("java.lang.byte[]"));
        assertEquals("stream", BodyTypeModel.carrierOf("java.io.InputStream"));
        assertEquals("file", BodyTypeModel.carrierOf("java.io.File"));
        assertEquals("value", BodyTypeModel.carrierOf("int"));
        assertEquals("value", BodyTypeModel.carrierOf("Integer"));
        assertEquals("value", BodyTypeModel.carrierOf("java.math.BigDecimal"));
        assertEquals("map", BodyTypeModel.carrierOf("java.util.Map<String, Object>"));
        assertEquals("list", BodyTypeModel.carrierOf("java.util.List"));
        assertEquals("list", BodyTypeModel.carrierOf("java.util.Set"));
        assertEquals("list", BodyTypeModel.carrierOf("String[]"));
        assertEquals("iterator", BodyTypeModel.carrierOf("java.util.Iterator"));
        assertEquals("pojo", BodyTypeModel.carrierOf("org.w3c.dom.Document"));
        assertEquals("pojo", BodyTypeModel.carrierOf("com.foo.Order"));
        assertEquals("unknown", BodyTypeModel.carrierOf("java.lang.Object"));
        assertEquals("unknown", BodyTypeModel.carrierOf(null));
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

    @Test
    void testShouldTellALanguageWithoutOptionsFromAnOlderCatalog() {
        LanguageModel language = new LanguageModel();
        language.setName("quickjs");
        LanguageModel.LanguageOptionModel expression = new LanguageModel.LanguageOptionModel();
        expression.setName("resultType");
        expression.setKind("attribute");
        expression.setType("string");
        expression.setJavaType("java.lang.String");
        language.addOption(expression);

        // a catalog older than the language options does not have them, and falls back to the expression options
        String json = JsonMapper.createParameterJsonSchema(language);
        assertFalse(json.contains("languageProperties"));
        LanguageModel older = JsonMapper.generateLanguageModel(json);
        assertNull(older.getLanguageOptions());
        assertEquals(List.of("resultType"), older.getConfigurationOptions().stream().map(BaseOptionModel::getName).toList());

        // a language without options says so
        language.setLanguageOptions(List.of());
        LanguageModel none = JsonMapper.generateLanguageModel(JsonMapper.createParameterJsonSchema(language));
        assertEquals(List.of(), none.getLanguageOptions());
        assertTrue(none.getConfigurationOptions().isEmpty());

        LanguageModel.LanguageOptionModel memory = new LanguageModel.LanguageOptionModel();
        memory.setName("engineMaxMemory");
        memory.setKind("property");
        memory.setType("integer");
        memory.setJavaType("long");
        memory.setDefaultValue(67108864L);
        memory.setDescription("The memory");
        language.setLanguageOptions(List.of(memory));
        LanguageModel some = JsonMapper.generateLanguageModel(JsonMapper.createParameterJsonSchema(language));
        assertEquals(1, some.getLanguageOptions().size());
        assertEquals("engineMaxMemory", some.getConfigurationOptions().get(0).getName());
        assertEquals("long", some.getLanguageOptions().get(0).getJavaType());
        assertEquals(List.of("resultType"), some.getOptions().stream().map(BaseOptionModel::getName).toList());
    }

    @Test
    void testShouldReadAndWriteDstWorlds() {
        // CAMEL-26438: the fake worlds of a component's deterministic simulation tests
        ComponentModel model = new ComponentModel();
        model.setName("aws2-sqs");
        model.setScheme("aws2-sqs");
        String json = JsonMapper.createParameterJsonSchema(model);
        assertFalse(json.contains("\"dstWorlds\""), json);
        assertTrue(JsonMapper.generateComponentModel(json).getDstWorlds().isEmpty());

        DstWorldModel world = new DstWorldModel();
        world.setName("SqsWorld");
        world.setJavaType("org.apache.camel.component.aws2.sqs.dst.SqsWorld");
        world.setFake("FakeSqs");
        world.setCovers(List.of("the visibility timeout extender"));
        world.setFaults(List.of("ReceiveMessage fails", "DeleteMessage is lost"));
        world.setInvariants(List.of("every offered message is routed at least once"));
        world.setIssues(List.of("CAMEL-25700"));
        world.setTests(List.of("SqsRedeliveryDstTest", "SqsVisibilityDstTest"));
        model.addDstWorld(world);

        json = JsonMapper.createParameterJsonSchema(model);
        // a section of its own after the options, one line per world
        assertTrue(json.indexOf("\"dstWorlds\"") > json.indexOf("\"properties\""), json);
        assertTrue(json.contains("\"SqsWorld\": { \"javaType\": \"org.apache.camel.component.aws2.sqs.dst.SqsWorld\","),
                json);
        ComponentModel model2 = JsonMapper.generateComponentModel(json);
        assertEquals(1, model2.getDstWorlds().size());
        DstWorldModel world2 = model2.getDstWorlds().get(0);
        assertEquals("SqsWorld", world2.getName());
        assertEquals("org.apache.camel.component.aws2.sqs.dst.SqsWorld", world2.getJavaType());
        assertEquals("FakeSqs", world2.getFake());
        assertEquals(List.of("the visibility timeout extender"), world2.getCovers());
        assertEquals(List.of("ReceiveMessage fails", "DeleteMessage is lost"), world2.getFaults());
        assertEquals(List.of("every offered message is routed at least once"), world2.getInvariants());
        assertEquals(List.of("CAMEL-25700"), world2.getIssues());
        assertEquals(List.of("SqsRedeliveryDstTest", "SqsVisibilityDstTest"), world2.getTests());
        // the section is written again as it was read
        String json2 = JsonMapper.createParameterJsonSchema(model2);
        assertEquals(json.substring(json.indexOf("\"dstWorlds\"")), json2.substring(json2.indexOf("\"dstWorlds\"")));
    }
}
