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
package org.apache.camel.catalog;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Edge cases of parsing, validating and building endpoint uris and configuration properties.
 */
public class CamelCatalogEdgeCasesTest {

    static DefaultCamelCatalog catalog;

    @BeforeAll
    public static void createCamelCatalog() {
        catalog = new DefaultCamelCatalog();
    }

    @Test
    public void testObjectOptionWithStringValue() {
        EndpointValidationResult result = catalog.validateEndpointProperties("file:inbox?scheduler=spring");
        assertTrue(result.isSuccess(), result.summaryErrorMessage(false));

        result = catalog.validateEndpointProperties("google-storage:b?storageClass=STANDARD");
        assertTrue(result.isSuccess(), result.summaryErrorMessage(false));

        result = catalog.validateEndpointProperties("jms:queue:foo?connectionFactory={{cf}}");
        assertTrue(result.isSuccess(), result.summaryErrorMessage(false));

        // a reference is still validated
        result = catalog.validateEndpointProperties("jms:queue:foo?connectionFactory=cf");
        assertFalse(result.isSuccess());
        assertNotNull(result.getInvalidReference());
    }

    @Test
    public void testOptionalPathOptionInMiddle() throws Exception {
        Map<String, String> map = catalog.endpointProperties("ftp://h:21");
        assertEquals("h", map.get("host"));
        assertEquals("21", map.get("port"));
        assertFalse(map.containsKey("directoryName"));

        map = catalog.endpointProperties("xmpp://h:5222");
        assertEquals("h", map.get("host"));
        assertEquals("5222", map.get("port"));
        assertFalse(map.containsKey("participant"));

        map = catalog.endpointProperties("ftp://h/dir");
        assertEquals("h", map.get("host"));
        assertEquals("dir", map.get("directoryName"));
        assertFalse(map.containsKey("port"));

        // separators that are the same cannot tell which option is omitted
        EndpointValidationResult result = catalog.validateEndpointProperties("spring-ws:http://foo.com/bar");
        assertTrue(result.isSuccess(), result.summaryErrorMessage(false));

        result = catalog.validateEndpointProperties("ftp://h:abc");
        assertFalse(result.isSuccess());
        assertEquals("abc", result.getInvalidInteger().get("port"));
    }

    @Test
    public void testAsEndpointUriPlaceholderInPath() throws Exception {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("host", "h");
        map.put("directoryName", "{{dir}}");
        assertEquals("ftp:h/{{dir}}", catalog.asEndpointUri("ftp", map, false));

        map.put("directoryName", "d/{{x}}");
        assertEquals("ftp:h/d/{{x}}", catalog.asEndpointUri("ftp", map, false));

        map.put("directoryName", "a/{{env:X:def}}");
        assertEquals("ftp:h/a/{{env:X:def}}", catalog.asEndpointUri("ftp", map, false));

        map.put("port", "21");
        map.remove("directoryName");
        assertEquals("ftp:h:21", catalog.asEndpointUri("ftp", map, false));
    }

    @Test
    public void testEnvOrSysPlaceholderInPath() throws Exception {
        EndpointValidationResult result = catalog.validateEndpointProperties("kafka:{{env:TOPIC}}");
        assertTrue(result.isSuccess(), result.summaryErrorMessage(false));

        result = catalog.validateEndpointProperties("jms:queue:{{sys:Q}}");
        assertTrue(result.isSuccess(), result.summaryErrorMessage(false));

        result = catalog.validateEndpointProperties("netty-http:http://foo.{{env:NS:default}}.svc/x");
        assertTrue(result.isSuccess(), result.summaryErrorMessage(false));

        Map<String, String> map = catalog.endpointProperties("kafka:{{env:TOPIC}}");
        assertEquals("{{env:TOPIC}}", map.get("topic"));

        map = catalog.endpointProperties("netty-http:http://foo.{{env:NS:default}}.svc:8080/x");
        assertEquals("foo.{{env:NS:default}}.svc", map.get("host"));
        assertEquals("8080", map.get("port"));
    }

    @Test
    public void testRawValueWithAmpersand() throws Exception {
        EndpointValidationResult result = catalog.validateEndpointProperties("aws2-s3://b?secretKey=RAW(a&b)");
        assertTrue(result.isSuccess(), result.summaryErrorMessage(false));

        result = catalog.validateEndpointProperties("ftp://h/d?password=RAW{a&b=c}&binary=true");
        assertTrue(result.isSuccess(), result.summaryErrorMessage(false));

        Map<String, String> map = catalog.endpointProperties("ftp://h/d?password=RAW(a&b=c)&binary=true");
        assertEquals("RAW(a&b=c)", map.get("password"));
        assertEquals("true", map.get("binary"));
    }

    @Test
    public void testRepeatedQueryParameter() throws Exception {
        Map<String, String> map = catalog.endpointProperties("http://h/p?x=a&x=b");
        assertEquals("[a, b]", map.get("x"));

        map = catalog.endpointProperties("http://h/p?x=a&x=b&x=c");
        assertEquals("[a, b, c]", map.get("x"));
    }

    @Test
    public void testOptionalPrefix() {
        EndpointValidationResult result = catalog.validateEndpointProperties("timer:foo?consumer.delay=1000");
        assertFalse(result.isSuccess());
        assertTrue(result.getUnknown().contains("consumer.delay"));

        result = catalog.validateEndpointProperties("file:inbox?consumer.exceptionHandler=#myHandler");
        assertTrue(result.isSuccess(), result.summaryErrorMessage(false));
    }

    @Test
    public void testLongOption() {
        EndpointValidationResult result = catalog.validateEndpointProperties("timer:foo?repeatCount=5000000000");
        assertTrue(result.isSuccess(), result.summaryErrorMessage(false));

        result = catalog.validateEndpointProperties("timer:foo?repeatCount=5000000000000000000000");
        assertFalse(result.isSuccess());
    }

    @Test
    public void testInvalidDurationSummary() {
        EndpointValidationResult result = catalog.validateEndpointProperties("timer:foo?period=abc");
        assertFalse(result.isSuccess());
        String summary = result.summaryErrorMessage(false);
        assertTrue(summary.contains("Invalid duration value: abc"), summary);
    }

    @Test
    public void testConfigurationPropertySuggestions() {
        ConfigurationPropertiesValidationResult result
                = catalog.validateConfigurationProperty("camel.component.kafka.brokerz=foo");
        assertFalse(result.isSuccess());
        assertTrue(result.getUnknown().contains("camel.component.kafka.brokerz"));
        String[] suggestions = result.getUnknownSuggestions().get("camel.component.kafka.brokerz");
        assertNotNull(suggestions);
        assertTrue(Arrays.asList(suggestions).contains("brokers"), Arrays.toString(suggestions));
    }

    @Test
    public void testConfigurationPropertyWithoutOption() {
        ConfigurationPropertiesValidationResult result = catalog.validateConfigurationProperty("camel.component.kafka=foo");
        assertFalse(result.isSuccess());
        assertTrue(result.getUnknown().contains("camel.component.kafka"));
    }

    @Test
    public void testValidatePropertiesLenient() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("timerName", "foo");
        map.put("lenient", "true");
        map.put("myExtra", "123");
        EndpointValidationResult result = catalog.validateProperties("timer", map);
        assertTrue(result.isSuccess(), result.summaryErrorMessage(false));

        // http is lenient
        map = new LinkedHashMap<>();
        map.put("httpUri", "localhost:8080/foo");
        map.put("myExtra", "123");
        result = catalog.validateProperties("http", map);
        assertTrue(result.isSuccess(), result.summaryErrorMessage(false));

        result = catalog.validateProperties("unknownxyz", map);
        assertEquals("unknownxyz", result.getUnknownComponent());
    }

    @Test
    public void testLanguageNotOnClasspathWithOptions() {
        LanguageValidationResult result = catalog.validateLanguageExpression(null, "xquery?resultType=String", "/foo");
        assertNotNull(result);
    }

    @Test
    public void testSecretValueWithParenthesis() throws Exception {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("bucketNameOrArn", "b");
        map.put("secretKey", "a)&b");
        String uri = catalog.asEndpointUri("aws2-s3", map, false);
        assertEquals("aws2-s3://b?secretKey=RAW{a)&b}", uri);
        assertEquals("RAW{a)&b}", catalog.endpointProperties(uri).get("secretKey"));

        map.put("secretKey", "a(b");
        assertEquals("aws2-s3://b?secretKey=RAW(a(b)", catalog.asEndpointUri("aws2-s3", map, false));
    }

    @Test
    public void testUserInfo() throws Exception {
        Map<String, String> map = catalog.endpointProperties("ftp://u:p:ss@host/dir");
        assertEquals("u", map.get("username"));
        assertEquals("p:ss", map.get("password"));
        assertEquals("host", map.get("host"));
        assertEquals("dir", map.get("directoryName"));

        map = catalog.endpointProperties("ftp://u:p%40ss@host:2121/dir");
        assertEquals("u", map.get("username"));
        assertEquals("p%40ss", map.get("password"));
        assertEquals("host", map.get("host"));
        assertEquals("2121", map.get("port"));
        assertEquals("dir", map.get("directoryName"));
    }
}
