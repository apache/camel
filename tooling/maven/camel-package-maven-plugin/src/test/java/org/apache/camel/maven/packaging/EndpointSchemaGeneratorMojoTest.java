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
package org.apache.camel.maven.packaging;

import java.nio.file.Paths;
import java.util.List;

import org.apache.camel.maven.packaging.endpoint.SomeEndpoint;
import org.apache.camel.maven.packaging.endpoint.SomeEndpointExtendingBodyTypes;
import org.apache.camel.maven.packaging.endpoint.SomeEndpointUsingEnumConstants;
import org.apache.camel.maven.packaging.endpoint.SomeEndpointUsingEnumConstantsByField;
import org.apache.camel.maven.packaging.endpoint.SomeEndpointUsingEnumConstantsByMethod;
import org.apache.camel.maven.packaging.endpoint.SomeEndpointUsingInterfaceConstants;
import org.apache.camel.maven.packaging.endpoint.SomeEndpointWithBadBodyTypes;
import org.apache.camel.maven.packaging.endpoint.SomeEndpointWithBadHeaders;
import org.apache.camel.maven.packaging.endpoint.SomeEndpointWithBodyTypes;
import org.apache.camel.maven.packaging.endpoint.SomeEndpointWithFilter;
import org.apache.camel.maven.packaging.endpoint.SomeEndpointWithHeaderClassHierarchy;
import org.apache.camel.maven.packaging.endpoint.SomeEndpointWithHeaderInterfaceHierarchy;
import org.apache.camel.maven.packaging.endpoint.SomeEndpointWithJavadocAsDescription;
import org.apache.camel.maven.packaging.endpoint.SomeEndpointWithoutHeaders;
import org.apache.camel.maven.packaging.endpoint.SomeExtendingEndpoint;
import org.apache.camel.maven.packaging.endpoint.SomeInternalRemoteEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.tooling.model.BodyTypeModel;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.ComponentModel.EndpointHeaderModel;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The unit test of {@link EndpointSchemaGeneratorMojo}.
 */
class EndpointSchemaGeneratorMojoTest {

    private final EndpointSchemaGeneratorMojo mojo = new EndpointSchemaGeneratorMojo();
    private final ComponentModel model = new ComponentModel();

    @BeforeEach
    void init() throws Exception {
        mojo.sourceRoots = List.of(
                Paths.get(EndpointSchemaGeneratorMojoTest.class.getResource("/").toURI())
                        .resolve("../../src/test/java/"));
        mojo.project = new MavenProject();
    }

    @ParameterizedTest
    @ValueSource(classes = {
            SomeEndpoint.class, SomeEndpointUsingEnumConstants.class, SomeEndpointUsingInterfaceConstants.class })
    void testCanRetrieveMetadataOfHeaders(Class<?> clazz) {
        UriEndpoint endpoint = clazz.getAnnotation(UriEndpoint.class);
        mojo.addEndpointHeaders(model, endpoint, "some");
        List<EndpointHeaderModel> endpointHeaders = model.getEndpointHeaders();
        assertEquals(3, endpointHeaders.size());
        // Full
        EndpointHeaderModel headerFull = endpointHeaders.get(0);
        assertEquals("header", headerFull.getKind());
        assertEquals("KEY_FULL", headerFull.getName());
        assertEquals("key full desc", headerFull.getDescription());
        assertEquals("my display name", headerFull.getDisplayName());
        assertEquals("org.apache.camel.maven.packaging.endpoint.SomeEndpoint$MyEnum", headerFull.getJavaType());
        assertTrue(headerFull.isRequired());
        assertEquals("VAL1", headerFull.getDefaultValue());
        assertTrue(headerFull.isDeprecated());
        assertEquals("my deprecated note", headerFull.getDeprecationNote());
        assertTrue(headerFull.isSecret());
        assertEquals("my label", headerFull.getLabel());
        assertEquals(3, headerFull.getEnums().size());
        assertEquals("my label", headerFull.getGroup());
        assertEquals(String.format("%s#%s", endpoint.headersClass().getName(), headerFull.getName()),
                headerFull.getConstantName());
        // Empty
        EndpointHeaderModel headerEmpty = endpointHeaders.get(1);
        assertEquals("header", headerEmpty.getKind());
        assertEquals("KEY_EMPTY", headerEmpty.getName());
        assertTrue(headerEmpty.getDescription().isEmpty());
        assertTrue(headerEmpty.getDisplayName().isEmpty());
        assertTrue(headerEmpty.getJavaType().isEmpty());
        assertFalse(headerEmpty.isRequired());
        assertInstanceOf(String.class, headerEmpty.getDefaultValue());
        assertTrue(((String) headerEmpty.getDefaultValue()).isEmpty());
        assertFalse(headerEmpty.isDeprecated());
        assertTrue(headerEmpty.getDeprecationNote().isEmpty());
        assertFalse(headerEmpty.isSecret());
        assertTrue(headerEmpty.getLabel().isEmpty());
        assertNull(headerEmpty.getEnums());
        assertEquals("common", headerEmpty.getGroup());
        assertEquals(String.format("%s#%s", endpoint.headersClass().getName(), headerEmpty.getName()),
                headerEmpty.getConstantName());
        // Empty with Javadoc as description
        EndpointHeaderModel headerEmptyWithJavadoc = endpointHeaders.get(2);
        assertEquals("header", headerEmptyWithJavadoc.getKind());
        assertEquals("KEY_EMPTY_WITH_JAVA_DOC", headerEmptyWithJavadoc.getName());
        assertEquals("Some description", headerEmptyWithJavadoc.getDescription());
        assertTrue(headerEmptyWithJavadoc.getDisplayName().isEmpty());
        assertTrue(headerEmptyWithJavadoc.getJavaType().isEmpty());
        assertFalse(headerEmptyWithJavadoc.isRequired());
        assertInstanceOf(String.class, headerEmptyWithJavadoc.getDefaultValue());
        assertTrue(((String) headerEmptyWithJavadoc.getDefaultValue()).isEmpty());
        assertFalse(headerEmptyWithJavadoc.isDeprecated());
        assertTrue(headerEmptyWithJavadoc.getDeprecationNote().isEmpty());
        assertFalse(headerEmptyWithJavadoc.isSecret());
        assertTrue(headerEmptyWithJavadoc.getLabel().isEmpty());
        assertNull(headerEmptyWithJavadoc.getEnums());
        assertEquals("common", headerEmptyWithJavadoc.getGroup());
        assertEquals(String.format("%s#%s", endpoint.headersClass().getName(), headerEmptyWithJavadoc.getName()),
                headerEmptyWithJavadoc.getConstantName());
    }

    @Test
    void testHeadersNotProperlyDefinedAreIgnored() {
        mojo.addEndpointHeaders(model, SomeEndpointWithBadHeaders.class.getAnnotation(UriEndpoint.class), "some");
        assertEquals(0, model.getEndpointHeaders().size());
    }

    @Test
    void testInternalEndpointMustNotBeRemote() {
        UriEndpoint endpoint = SomeInternalRemoteEndpoint.class.getAnnotation(UriEndpoint.class);
        MojoExecutionException e = assertThrows(MojoExecutionException.class,
                () -> mojo.findComponentProperties(endpoint, SomeInternalRemoteEndpoint.class, "some", "some", null, null,
                        null));
        assertTrue(e.getMessage().contains("needs remote = false"), e.getMessage());
    }

    @Test
    void testEndpointWithoutHeadersAreIgnored() {
        mojo.addEndpointHeaders(model, SomeEndpointWithoutHeaders.class.getAnnotation(UriEndpoint.class), "some");
        assertEquals(0, model.getEndpointHeaders().size());
    }

    @Test
    void testEndpointWithFilterKeepOnlyApplicableHeaders() {
        mojo.addEndpointHeaders(model, SomeEndpointWithFilter.class.getAnnotation(UriEndpoint.class), "some");
        List<EndpointHeaderModel> endpointHeaders = model.getEndpointHeaders();
        assertEquals(2, endpointHeaders.size());
        for (int i = 0; i < endpointHeaders.size(); i++) {
            EndpointHeaderModel headerEmpty = endpointHeaders.get(i);
            assertEquals("header", headerEmpty.getKind());
            assertEquals(String.format("keep-%d", i + 1), headerEmpty.getName());
        }
    }

    @Test
    void testEndpointWithCleanedJavadocAsDescription() {
        mojo.addEndpointHeaders(model, SomeEndpointWithJavadocAsDescription.class.getAnnotation(UriEndpoint.class), "some");
        mojo.enhanceComponentModel(model, null, "", "");
        List<EndpointHeaderModel> endpointHeaders = model.getEndpointHeaders();
        assertEquals(1, endpointHeaders.size());
        EndpointHeaderModel headerEmpty = endpointHeaders.get(0);
        assertEquals("header", headerEmpty.getKind());
        assertEquals("no-description", headerEmpty.getName());
        assertEquals("Some description about NO_DESCRIPTION.", headerEmpty.getDescription());
    }

    @ParameterizedTest
    @ValueSource(classes = {
            SomeEndpointWithHeaderClassHierarchy.class, SomeEndpointWithHeaderInterfaceHierarchy.class })
    void testEndpointWithHeadersInHierarchy(Class<?> clazz) {
        mojo.addEndpointHeaders(model, clazz.getAnnotation(UriEndpoint.class), "some");
        List<EndpointHeaderModel> endpointHeaders = model.getEndpointHeaders();
        assertEquals(2, endpointHeaders.size());
        EndpointHeaderModel header = endpointHeaders.get(0);
        assertEquals("header", header.getKind());
        assertEquals("KEY_FROM_SPECIFIC", header.getName());
        header = endpointHeaders.get(1);
        assertEquals("header", header.getKind());
        assertEquals("KEY_FROM_COMMON", header.getName());
    }

    @ParameterizedTest
    @ValueSource(classes = {
            SomeEndpointUsingEnumConstantsByField.class, SomeEndpointUsingEnumConstantsByMethod.class })
    void testEndpointWithNameProvider(Class<?> clazz) {
        UriEndpoint endpoint = clazz.getAnnotation(UriEndpoint.class);
        mojo.addEndpointHeaders(model, endpoint, "some");
        List<EndpointHeaderModel> endpointHeaders = model.getEndpointHeaders();
        assertEquals(1, endpointHeaders.size());
        EndpointHeaderModel header = endpointHeaders.get(0);
        assertEquals("header", header.getKind());
        assertEquals("SomeName", header.getName());
        assertEquals(
                String.format("%s#SOME_VALUE@%s", endpoint.headersClass().getName(),
                        endpoint.headersNameProvider() + (clazz.getName().contains("Method") ? "()" : "")),
                header.getConstantName());
    }

    @Test
    void testEndpointWithHeadersOnSelfAndSuperclass() {
        ComponentModel parentModel = new ComponentModel();
        mojo.addEndpointHeaders(parentModel, SomeEndpoint.class.getAnnotation(UriEndpoint.class), "some");
        assertEquals(3, parentModel.getEndpointHeaders().size());
        mojo.addEndpointHeaders(model, SomeExtendingEndpoint.class.getAnnotation(UriEndpoint.class), "someext");
        mojo.enhanceComponentModel(model, parentModel, "", "");
        List<EndpointHeaderModel> endpointHeaders = model.getEndpointHeaders();
        assertEquals(4, endpointHeaders.size());
        EndpointHeaderModel headerExtended = endpointHeaders.get(1);
        assertEquals("header", headerExtended.getKind());
        assertEquals("key on extended class", headerExtended.getDescription());
        EndpointHeaderModel headerOverridden = endpointHeaders.get(0);
        assertEquals("header", headerOverridden.getKind());
        assertEquals("key on extended overriding parent", headerOverridden.getDescription());
        EndpointHeaderModel headerFromParent = endpointHeaders.get(3);
        assertEquals("header", headerFromParent.getKind());
        assertEquals("KEY_EMPTY_WITH_JAVA_DOC", headerFromParent.getName());

    }

    @Test
    void testEndpointWithBodyTypes() throws Exception {
        // CAMEL-24961: the body a step needs and the body it leaves, in the order of the annotation
        addEndpointOptions("resultHandlerFactory", "output");
        mojo.addBodyTypes(model, SomeEndpointWithBodyTypes.class);
        assertEquals(List.of(BodyTypeModel.CONSUMER_OUT, BodyTypeModel.PRODUCER_IN, BodyTypeModel.PRODUCER_OUT),
                List.copyOf(model.getBodyTypes().keySet()));

        BodyTypeModel consumerOut = model.getBodyType(BodyTypeModel.CONSUMER_OUT);
        assertEquals(List.of("file"), consumerOut.getCarriers());
        assertEquals("org.apache.camel.component.file.GenericFile", consumerOut.getJavaType());
        assertEquals("The file as read", consumerOut.getDescription());
        assertNull(consumerOut.getFormat());
        assertTrue(consumerOut.getDependsOn().isEmpty());

        assertEquals(List.of("text", "bytes", "stream", "file"),
                model.getBodyType(BodyTypeModel.PRODUCER_IN).getCarriers());

        BodyTypeModel producerOut = model.getBodyType(BodyTypeModel.PRODUCER_OUT);
        assertEquals(List.of("text"), producerOut.getCarriers());
        assertEquals("xml", producerOut.getFormat());
        assertEquals(List.of("resultHandlerFactory"), producerOut.getDependsOn());
        assertEquals(1, producerOut.getByOption().size());
        BodyTypeModel.OptionBodyTypeModel bytes = producerOut.getByOption().get(0);
        assertEquals("output", bytes.getOption());
        assertEquals("bytes", bytes.getValue());
        assertEquals(List.of("bytes"), bytes.getCarriers());
        assertEquals("byte[]", bytes.getJavaType());
    }

    @Test
    void testEndpointDoesNotInheritBodyTypes() throws Exception {
        // a subclass may change the body, so it declares its own
        mojo.addBodyTypes(model, SomeEndpointExtendingBodyTypes.class);
        assertTrue(model.getBodyTypes().isEmpty());
    }

    @Test
    void testEndpointWithoutBodyTypes() throws Exception {
        mojo.addBodyTypes(model, SomeEndpointWithoutHeaders.class);
        assertTrue(model.getBodyTypes().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(classes = {
            SomeEndpointWithBadBodyTypes.DataFormatSide.class, SomeEndpointWithBadBodyTypes.ProducerSideOfConsumer.class,
            SomeEndpointWithBadBodyTypes.UnchangedInput.class, SomeEndpointWithBadBodyTypes.AnyOutput.class,
            SomeEndpointWithBadBodyTypes.UnknownWithOthers.class, SomeEndpointWithBadBodyTypes.NoCarriers.class,
            SomeEndpointWithBadBodyTypes.UnknownOption.class, SomeEndpointWithBadBodyTypes.OptionValueWithoutCarriers.class })
    void testEndpointWithBadBodyTypes(Class<?> clazz) {
        addEndpointOptions("output");
        model.setConsumerOnly(clazz.getAnnotation(UriEndpoint.class).consumerOnly());
        MojoExecutionException e = assertThrows(MojoExecutionException.class, () -> mojo.addBodyTypes(model, clazz));
        assertTrue(e.getMessage().startsWith(clazz.getName() + ": the @BodyTypes "), e.getMessage());
        assertTrue(model.getBodyTypes().isEmpty());
    }

    private void addEndpointOptions(String... names) {
        for (String name : names) {
            ComponentModel.EndpointOptionModel option = new ComponentModel.EndpointOptionModel();
            option.setName(name);
            model.addEndpointOption(option);
        }
    }
}
