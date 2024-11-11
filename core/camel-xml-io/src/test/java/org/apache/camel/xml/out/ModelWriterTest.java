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
package org.apache.camel.xml.out;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import jakarta.xml.bind.annotation.XmlTransient;

import org.w3c.dom.Element;

import org.apache.camel.model.RouteTemplatesDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.model.TemplatedRoutesDefinition;
import org.apache.camel.model.app.BeansDefinition;
import org.apache.camel.model.rest.RestsDefinition;
import org.apache.camel.util.StringHelper;
import org.apache.camel.xml.in.ModelParser;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.xmlunit.assertj3.XmlAssert;
import org.xmlunit.diff.DefaultNodeMatcher;
import org.xmlunit.diff.ElementSelectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.AssertionFailureBuilder.assertionFailure;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;

public class ModelWriterTest {

    public static final String NAMESPACE = "http://camel.apache.org/schema/spring";

    private static final Map<Field, Boolean> TRANSIENT = new ConcurrentHashMap<>();

    @ParameterizedTest
    @MethodSource("routes")
    @DisplayName("Test xml roundtrip for <routes>")
    void testRoutes(String xml, String ns) throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(xml)) {
            RoutesDefinition expected = new ModelParser(is, NAMESPACE).parseRoutesDefinition().get();
            StringWriter sw = new StringWriter();
            new ModelWriter(sw, ns).writeRoutesDefinition(expected);
            RoutesDefinition actual = new ModelParser(new StringReader(sw.toString()), ns).parseRoutesDefinition().get();
            assertDeepEquals(expected, actual, sw.toString());
        }
    }

    @ParameterizedTest
    @MethodSource("routes")
    @DisplayName("Test xml roundtrip for <routes>, then compare generated XML")
    void testRoutesWithDiff(String xml, String ns) throws Exception {
        String original;
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(xml);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            assertNotNull(is);
            IOUtils.copy(is, baos);
            original = baos.toString();
        }

        assertThat(original).isNotBlank();
        RoutesDefinition expected
                = new ModelParser(new ByteArrayInputStream(original.getBytes()), NAMESPACE).parseRoutesDefinition().get();
        StringWriter sw = new StringWriter();
        new ModelWriter(sw, ns).writeRoutesDefinition(expected);
        String generatedXml = sw.toString();
        assertThat(generatedXml).isNotBlank();

        XmlAssert.assertThat(generatedXml)
                .and(original)
                .withNodeMatcher(new DefaultNodeMatcher(ElementSelectors.byNameAndText))
                .withNodeFilter(node -> {
                    // skip comparing namespace as original have namespaces scattered in other places than inside <xpath>
                    if ("namespace".equals(node.getLocalName())) {
                        return false;
                    }
                    return true;
                })
                .ignoreWhitespace()
                .ignoreElementContentWhitespace()
                .ignoreComments()
                .areSimilar();
    }

    @ParameterizedTest
    @MethodSource("rests")
    @DisplayName("Test xml roundtrip for <rests>")
    void testRests(String xml, String ns) throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(xml)) {
            RestsDefinition expected = new ModelParser(is, NAMESPACE).parseRestsDefinition().get();
            StringWriter sw = new StringWriter();
            new ModelWriter(sw, ns).writeRestsDefinition(expected);
            RestsDefinition actual = new ModelParser(new StringReader(sw.toString()), ns).parseRestsDefinition().get();
            assertDeepEquals(expected, actual, sw.toString());
        }
    }

    @ParameterizedTest
    @MethodSource("rests")
    @DisplayName("Test xml roundtrip for <rests>, then compare generated XML")
    void testRestsWithDiff(String xml, String ns) throws Exception {
        String original;
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(xml);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            assertNotNull(is);
            IOUtils.copy(is, baos);
            original = baos.toString();
        }

        RestsDefinition expected
                = new ModelParser(new ByteArrayInputStream(original.getBytes()), NAMESPACE).parseRestsDefinition().get();
        StringWriter sw = new StringWriter();
        new ModelWriter(sw, ns).writeRestsDefinition(expected);
        String generatedXml = sw.toString();
        assertThat(generatedXml).isNotBlank();

        XmlAssert.assertThat(generatedXml)
                .and(original)
                .withNodeMatcher(new DefaultNodeMatcher(ElementSelectors.byNameAndText))
                .withAttributeFilter(attr -> {
                    // skip default values for rest-dsl header params
                    if ("header".equals(attr.getOwnerElement().getTagName()) && "arrayType".equals(attr.getName())) {
                        return false;
                    }
                    if ("header".equals(attr.getOwnerElement().getTagName()) && "collectionFormat".equals(attr.getName())) {
                        return false;
                    }
                    return true;
                })
                .ignoreWhitespace()
                .ignoreElementContentWhitespace()
                .ignoreComments()
                .areSimilar();
    }

    @ParameterizedTest
    @MethodSource("routeTemplates")
    @DisplayName("Test xml roundtrip for <routeTemplates>")
    void testRouteTemplates(String xml, String ns) throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(xml)) {
            RouteTemplatesDefinition expected = new ModelParser(is, NAMESPACE).parseRouteTemplatesDefinition().get();
            StringWriter sw = new StringWriter();
            new ModelWriter(sw, ns).writeRouteTemplatesDefinition(expected);
            RouteTemplatesDefinition actual
                    = new ModelParser(new StringReader(sw.toString()), ns).parseRouteTemplatesDefinition().get();
            assertDeepEquals(expected, actual, sw.toString());
        }
    }

    @ParameterizedTest
    @MethodSource("routeTemplates")
    @DisplayName("Test xml roundtrip for <routeTemplates> then compare generated XML")
    void testRouteTemplatesWithDiff(String xml, String ns) throws Exception {
        String original;
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(xml);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            assertNotNull(is);
            IOUtils.copy(is, baos);
            original = baos.toString();
        }

        RouteTemplatesDefinition expected = new ModelParser(new ByteArrayInputStream(original.getBytes()), NAMESPACE)
                .parseRouteTemplatesDefinition().get();
        StringWriter sw = new StringWriter();
        new ModelWriter(sw, ns).writeRouteTemplatesDefinition(expected);
        String generatedXml = sw.toString();
        assertThat(generatedXml).isNotBlank();

        XmlAssert.assertThat(generatedXml)
                .and(original)
                .withNodeMatcher(new DefaultNodeMatcher(ElementSelectors.byNameAndText))
                .ignoreWhitespace()
                .ignoreElementContentWhitespace()
                .ignoreComments()
                .areSimilar();
    }

    @ParameterizedTest
    @MethodSource("templatedRoutes")
    @DisplayName("Test xml roundtrip for <templatedRoutes>")
    void testTemplatedRoutes(String xml, String ns) throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(xml)) {
            TemplatedRoutesDefinition expected = new ModelParser(is, NAMESPACE).parseTemplatedRoutesDefinition().get();
            StringWriter sw = new StringWriter();
            new ModelWriter(sw, ns).writeTemplatedRoutesDefinition(expected);
            TemplatedRoutesDefinition actual
                    = new ModelParser(new StringReader(sw.toString()), ns).parseTemplatedRoutesDefinition().get();
            assertDeepEquals(expected, actual, sw.toString());
        }
    }

    @ParameterizedTest
    @MethodSource("templatedRoutes")
    @DisplayName("Test xml roundtrip for <templatedRoutes> then compare generated XML")
    void testTemplatedRoutesWithDiff(String xml, String ns) throws Exception {
        String original;
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(xml);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            assertNotNull(is);
            IOUtils.copy(is, baos);
            original = baos.toString();
        }
        TemplatedRoutesDefinition expected = new ModelParser(new ByteArrayInputStream(original.getBytes()), NAMESPACE)
                .parseTemplatedRoutesDefinition().get();
        StringWriter sw = new StringWriter();
        new ModelWriter(sw, ns).writeTemplatedRoutesDefinition(expected);
        String generatedXml = sw.toString();
        assertThat(generatedXml).isNotBlank();

        XmlAssert.assertThat(generatedXml)
                .and(original)
                .withNodeMatcher(new DefaultNodeMatcher(ElementSelectors.byNameAndText))
                .ignoreWhitespace()
                .ignoreElementContentWhitespace()
                .ignoreComments()
                .areSimilar();
    }

    @ParameterizedTest
    @MethodSource("beans")
    @DisplayName("Test xml roundtrip for <beans>")
    void testBeans(String xml, String ns) throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(xml)) {
            BeansDefinition expected = new ModelParser(is, NAMESPACE).parseBeansDefinition().get();
            StringWriter sw = new StringWriter();
            new ModelWriter(sw, ns).writeBeansDefinition(expected);
            BeansDefinition actual
                    = new ModelParser(new StringReader(sw.toString()), ns).parseBeansDefinition().get();
            assertDeepEquals(expected, actual, sw.toString());
        }
    }

    @ParameterizedTest
    @MethodSource("beans")
    @DisplayName("Test xml roundtrip for <beans> then compare generated XML")
    void testBeansWithDiff(String xml, String ns) throws Exception {
        String original;
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(xml);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            assertNotNull(is);
            IOUtils.copy(is, baos);
            original = baos.toString();
        }
        BeansDefinition expected
                = new ModelParser(new ByteArrayInputStream(original.getBytes()), NAMESPACE).parseBeansDefinition().get();
        StringWriter sw = new StringWriter();
        new ModelWriter(sw, ns).writeBeansDefinition(expected);
        String generatedXml = sw.toString();
        assertThat(generatedXml).isNotBlank();

        XmlAssert.assertThat(generatedXml)
                .and(original)
                .withNodeMatcher(new DefaultNodeMatcher(ElementSelectors.byNameAndText))
                .withAttributeFilter(attr -> {
                    // bean constructor index is optional
                    if ("constructor".equals(attr.getOwnerElement().getTagName()) && "index".equals(attr.getName())) {
                        return false;
                    }
                    return true;
                })
                .ignoreWhitespace()
                .ignoreElementContentWhitespace()
                .ignoreComments()
                .areSimilar();
    }

    private static Stream<Arguments> routes() {
        return definitions("routes");
    }

    private static Stream<Arguments> rests() {
        return definitions("rests");
    }

    private static Stream<Arguments> routeTemplates() {
        return definitions("routeTemplates");
    }

    private static Stream<Arguments> templatedRoutes() {
        return definitions("templatedRoutes");
    }

    private static Stream<Arguments> beans() {
        return definitions("beans");
    }

    private static Stream<Arguments> definitions(String xml) {
        try {
            return Files.list(Paths.get("src/test/resources"))
                    .filter(p -> {
                        try {
                            return Files.isRegularFile(p)
                                    && p.getFileName().toString().endsWith(".xml")
                                    && Files.readString(p).contains("<" + xml);
                        } catch (IOException e) {
                            throw new IOError(e);
                        }
                    })
                    .map(p -> p.getFileName().toString())
                    .flatMap(p -> Stream.of(Arguments.of(p, ""), Arguments.of(p, NAMESPACE)));
        } catch (IOException e) {
            throw new IOError(e);
        }
    }

    private static void assertDeepEquals(Object expected, Object actual, String message) {
        try {
            try {
                deepEquals(expected, actual, "");
            } catch (AssertionError e) {
                assertionFailure() //
                        .cause(e)
                        .message(message) //
                        .expected(expected) //
                        .actual(actual) //
                        .buildAndThrow();
            }
        } catch (IllegalAccessException e) {
            assertionFailure().cause(e).buildAndThrow();
        }
    }

    private static void deepEquals(Object expected, Object actual, String path) throws IllegalAccessException {
        if (expected == null || actual == null) {
            assertSame(expected, actual, path);
        } else if (expected.getClass() != actual.getClass()) {
            fail("Not equals at " + path);
        } else if (expected instanceof Collection) {
            Iterator<?> ie = ((Collection) expected).iterator();
            Iterator<?> ia = ((Collection) actual).iterator();
            int i = 0;
            while (ie.hasNext() && ia.hasNext()) {
                deepEquals(ie.next(), ia.next(), path + "[" + (i++) + "]");
            }
            assertEquals(ie.hasNext(), ia.hasNext(), path);
        } else if (expected.getClass() == AtomicBoolean.class) {
            assertEquals(((AtomicBoolean) expected).get(), ((AtomicBoolean) actual).get(), path);
        } else if (expected.getClass().isEnum()) {
            assertEquals(((Enum) expected).name(), ((Enum) actual).name(), path);
        } else if (expected.getClass().getName().startsWith("java.")) {
            assertEquals(expected, actual, path);
        } else if (Element.class.isAssignableFrom(expected.getClass())) {
            // TODO: deep check
            assertEquals(((Element) expected).getTagName(), ((Element) actual).getTagName(), path);
            assertEquals(((Element) expected).getNamespaceURI(), ((Element) actual).getNamespaceURI(), path);
        } else {
            for (Class<?> clazz = expected.getClass(); clazz != Object.class; clazz = clazz.getSuperclass()) {
                for (Field field : clazz.getDeclaredFields()) {
                    if (isTransient(field)) {
                        continue;
                    }
                    field.setAccessible(true);
                    Object fe = field.get(expected);
                    Object fa = field.get(actual);
                    deepEquals(fe, fa, (!path.isEmpty() ? path + "." : path) + field.getName());
                }
            }
        }
    }

    private static boolean isTransient(Field field) {
        return TRANSIENT.computeIfAbsent(field, ModelWriterTest::checkIsTransient);
    }

    private static boolean checkIsTransient(Field field) {
        if (Modifier.isStatic(field.getModifiers())) {
            return true;
        }
        if (field.getAnnotation(XmlTransient.class) != null) {
            return true;
        }
        String name = field.getName();
        try {
            Method method = field.getDeclaringClass().getDeclaredMethod(
                    "get" + StringHelper.capitalize(name));
            if (method.getAnnotation(XmlTransient.class) != null) {
                return true;
            }
        } catch (Exception t) {
            // ignore
        }
        try {
            Method method = field.getDeclaringClass().getDeclaredMethod(
                    "set" + StringHelper.capitalize(name),
                    field.getType());
            if (method.getAnnotation(XmlTransient.class) != null) {
                return true;
            }
        } catch (Exception t) {
            // ignore
        }
        return false;
    }
}
