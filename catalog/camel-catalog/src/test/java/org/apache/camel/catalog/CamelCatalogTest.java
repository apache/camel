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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.tooling.model.ArtifactModel;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.DataFormatModel;
import org.apache.camel.tooling.model.LanguageModel;
import org.apache.camel.tooling.model.ReleaseModel;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.catalog.impl.CatalogHelper.loadText;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CamelCatalogTest {

    static CamelCatalog catalog;

    private static final Logger LOG = LoggerFactory.getLogger(CamelCatalogTest.class);

    @BeforeAll
    public static void createCamelCatalog() {
        catalog = new DefaultCamelCatalog();
    }

    @Test
    public void testGetVersion() {
        String version = catalog.getCatalogVersion();
        assertNotNull(version);

        String loaded = catalog.getLoadedVersion();
        assertNotNull(loaded);
        assertEquals(version, loaded);
    }

    @Test
    public void testLoadVersion() {
        boolean result = catalog.loadVersion("1.0");
        assertFalse(result);

        String version = catalog.getCatalogVersion();
        result = catalog.loadVersion(version);
        assertTrue(result);
    }

    @Test
    public void testFindComponentNames() {
        List<String> names = catalog.findComponentNames();
        assertNotNull(names);
        assertTrue(names.contains("file"));
        assertTrue(names.contains("log"));
        assertTrue(names.contains("docker"));
        assertTrue(names.contains("jms"));
        assertTrue(names.contains("activemq"));
        assertTrue(names.contains("zookeeper-master"));
    }

    @Test
    public void testFindOtherNames() {
        List<String> names = catalog.findOtherNames();

        assertTrue(names.contains("test-spring-junit5"));

        assertFalse(names.contains("http-common"));
        assertFalse(names.contains("kura"));
        assertFalse(names.contains("core-osgi"));
        assertFalse(names.contains("file"));
        assertFalse(names.contains("ftp"));
        assertFalse(names.contains("jetty"));
    }

    @Test
    public void testFindDataFormatNames() {
        List<String> names = catalog.findDataFormatNames();
        assertNotNull(names);
        assertTrue(names.contains("bindyCsv"));
        assertTrue(names.contains("hl7"));
        assertTrue(names.contains("jaxb"));
        assertTrue(names.contains("syslog"));
        assertTrue(names.contains("asn1"));
        assertTrue(names.contains("zipFile"));
    }

    @Test
    public void testFindLanguageNames() {
        List<String> names = catalog.findLanguageNames();

        assertTrue(names.contains("simple"));
        assertTrue(names.contains("groovy"));
        assertTrue(names.contains("mvel"));
        assertTrue(names.contains("bean"));
        assertTrue(names.contains("file"));
        assertTrue(names.contains("xtokenize"));
        assertTrue(names.contains("hl7terser"));
    }

    @Test
    public void testFindTransformerNames() {
        List<String> names = catalog.findTransformerNames();

        assertTrue(names.contains("application-cloudevents-json"));
        assertTrue(names.contains("application-x-java-object"));
        assertTrue(names.contains("aws-cloudtrail-application-cloudevents"));
        assertTrue(names.contains("azure-storage-queue-application-cloudevents"));
        assertTrue(names.contains("http-application-cloudevents"));
    }

    @Test
    public void testFindModelNames() {
        List<String> names = catalog.findModelNames();
        assertNotNull(names);
        assertTrue(names.contains("from"));
        assertTrue(names.contains("to"));
        assertTrue(names.contains("recipientList"));
        assertTrue(names.contains("aggregate"));
        assertTrue(names.contains("split"));
        assertTrue(names.contains("loadBalance"));
        assertTrue(names.contains("circuitBreaker"));
        assertTrue(names.contains("saga"));
    }

    @Test
    public void testJsonSchema() {
        String schema = catalog.componentJSonSchema("docker");
        assertNotNull(schema);

        schema = catalog.dataFormatJSonSchema("hl7");
        assertNotNull(schema);

        schema = catalog.languageJSonSchema("groovy");
        assertNotNull(schema);

        schema = catalog.modelJSonSchema("aggregate");
        assertNotNull(schema);

        // lets make it possible to find bean/method using both names
        schema = catalog.modelJSonSchema("method");
        assertNotNull(schema);
        schema = catalog.modelJSonSchema("bean");
        assertNotNull(schema);
    }

    @Test
    public void testXmlSchema() {
        String schema = catalog.springSchemaAsXml();
        assertNotNull(schema);
    }

    @Test
    public void testMain() {
        String schema = catalog.mainJsonSchema();
        assertNotNull(schema);
    }

    @Test
    public void testAsEndpointUriMapFile() throws Exception {
        Map<String, String> map = new HashMap<>();
        map.put("directoryName", "src/data/inbox");
        map.put("noop", "true");
        map.put("delay", "5000");

        String uri = catalog.asEndpointUri("file", map, true);
        assertEquals("file:src/data/inbox?delay=5000&noop=true", uri);

        String uri2 = catalog.asEndpointUriXml("file", map, true);
        assertEquals("file:src/data/inbox?delay=5000&amp;noop=true", uri2);
    }

    @Test
    public void testAsEndpointUriMapFtp() throws Exception {
        Map<String, String> map = new HashMap<>();
        map.put("host", "someserver");
        map.put("port", "21");
        map.put("directoryName", "foo");
        map.put("connectTimeout", "5000");

        String uri = catalog.asEndpointUri("ftp", map, true);
        assertEquals("ftp:someserver:21/foo?connectTimeout=5000", uri);

        String uri2 = catalog.asEndpointUriXml("ftp", map, true);
        assertEquals("ftp:someserver:21/foo?connectTimeout=5000", uri2);
    }

    @Test
    public void testAsEndpointUriMapJms() throws Exception {
        Map<String, String> map = new HashMap<>();
        map.put("destinationType", "queue");
        map.put("destinationName", "foo");

        String uri = catalog.asEndpointUri("jms", map, true);
        assertEquals("jms:queue:foo", uri);
    }

    @Test
    public void testAsEndpointUriNettyhttp() throws Exception {
        Map<String, String> map = new HashMap<>();
        // use http protocol
        map.put("protocol", "http");
        map.put("host", "localhost");
        map.put("port", "8080");
        map.put("path", "foo/bar");
        map.put("disconnect", "true");

        String uri = catalog.asEndpointUri("netty-http", map, true);
        assertEquals("netty-http:http://localhost:8080/foo/bar?disconnect=true", uri);

        // lets switch protocol
        map.put("protocol", "https");

        uri = catalog.asEndpointUri("netty-http", map, true);
        assertEquals("netty-http:https://localhost:8080/foo/bar?disconnect=true", uri);

        // lets set a query parameter in the path
        map.put("path", "foo/bar?verbose=true");
        map.put("disconnect", "true");

        uri = catalog.asEndpointUri("netty-http", map, true);
        assertEquals("netty-http:https://localhost:8080/foo/bar?verbose=true&disconnect=true", uri);
    }

    @Test
    public void testAsEndpointUriTimer() throws Exception {
        Map<String, String> map = new HashMap<>();
        map.put("timerName", "foo");
        map.put("period", "5000");

        String uri = catalog.asEndpointUri("timer", map, true);
        assertEquals("timer:foo?period=5000", uri);
    }

    @Test
    public void testAsEndpointDefaultValue() throws Exception {
        Map<String, String> map = new HashMap<>();
        map.put("destinationName", "cheese");
        map.put("maxMessagesPerTask", "-1");

        String uri = catalog.asEndpointUri("jms", map, true);
        assertEquals("jms:cheese?maxMessagesPerTask=-1", uri);
    }

    @Test
    public void testAsEndpointUriPropertiesPlaceholders() throws Exception {
        Map<String, String> map = new HashMap<>();
        map.put("timerName", "foo");
        map.put("period", "{{howoften}}");
        map.put("repeatCount", "5");

        String uri = catalog.asEndpointUri("timer", map, true);
        assertEquals("timer:foo?period=%7B%7Bhowoften%7D%7D&repeatCount=5", uri);

        uri = catalog.asEndpointUri("timer", map, false);
        assertEquals("timer:foo?period={{howoften}}&repeatCount=5", uri);
    }

    @Test
    public void testAsEndpointUriBeanLookup() throws Exception {
        Map<String, String> map = new HashMap<>();
        map.put("resourceUri", "foo.xslt");
        map.put("converter", "#myConverter");

        String uri = catalog.asEndpointUri("xslt", map, true);
        assertEquals("xslt:foo.xslt?converter=%23myConverter", uri);

        uri = catalog.asEndpointUri("xslt", map, false);
        assertEquals("xslt:foo.xslt?converter=#myConverter", uri);
    }

    @Test
    public void testAsEndpointUriMapJmsRequiredOnly() throws Exception {
        Map<String, String> map = new HashMap<>();
        map.put("destinationName", "foo");
        String uri = catalog.asEndpointUri("jms", map, true);
        assertEquals("jms:foo", uri);

        map.put("deliveryPersistent", "false");
        map.put("allowNullBody", "true");

        uri = catalog.asEndpointUri("jms", map, true);
        assertEquals("jms:foo?allowNullBody=true&deliveryPersistent=false", uri);

        String uri2 = catalog.asEndpointUriXml("jms", map, true);
        assertEquals("jms:foo?allowNullBody=true&amp;deliveryPersistent=false", uri2);
    }

    @Test
    public void testAsEndpointUriRestUriTemplate() throws Exception {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("method", "get");
        map.put("path", "api");
        map.put("uriTemplate", "user/{id}");
        String uri = catalog.asEndpointUri("rest", map, true);

        assertEquals("rest:get:api:user/{id}", uri);
    }

    @Test
    public void testAsEndpointUriNettyHttpHostnameWithDash() throws Exception {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("protocol", "http");
        map.put("host", "a-b-c.hostname.tld");
        map.put("port", "8080");
        map.put("path", "anything");
        String uri = catalog.asEndpointUri("netty-http", map, false);
        assertEquals("netty-http:http://a-b-c.hostname.tld:8080/anything", uri);

        map = new LinkedHashMap<>();
        map.put("protocol", "http");
        map.put("host", "a-b-c.server.net");
        map.put("port", "8888");
        map.put("path", "service/v3");
        uri = catalog.asEndpointUri("netty-http", map, true);
        assertEquals("netty-http:http://a-b-c.server.net:8888/service/v3", uri);
    }

    @Test
    public void testNettyHttpDynamicToIssueHost() throws Exception {
        String uri = "netty-http:http://a-b-c.hostname.tld:8080/anything";
        Map<String, String> params = catalog.endpointProperties(uri);
        assertEquals("http", params.get("protocol"));
        assertEquals("a-b-c.hostname.tld", params.get("host"));
        assertEquals("8080", params.get("port"));
        assertEquals("anything", params.get("path"));

        // remove path
        params.remove("path");

        String resolved = catalog.asEndpointUri("netty-http", params, false);
        assertEquals("netty-http:http://a-b-c.hostname.tld:8080", resolved);
    }

    @Test
    public void testEndpointProperties() throws Exception {
        Map<String, String> map = catalog.endpointProperties("ftp:someserver:21/foo?connectTimeout=5000");
        assertNotNull(map);
        assertEquals(4, map.size());

        assertEquals("someserver", map.get("host"));
        assertEquals("21", map.get("port"));
        assertEquals("foo", map.get("directoryName"));
        assertEquals("5000", map.get("connectTimeout"));
    }

    @Test
    public void testEndpointLenientProperties() throws Exception {
        Map<String, String> map
                = catalog.endpointLenientProperties("http:myserver?throwExceptionOnFailure=false&foo=123&bar=456");
        assertNotNull(map);
        assertEquals(2, map.size());

        assertEquals("123", map.get("foo"));
        assertEquals("456", map.get("bar"));

        map = catalog.endpointLenientProperties(
                "http:myserver?throwExceptionOnFailure=false&foo=123&bar=456&httpClient.timeout=5000&httpClient.soTimeout=10000");
        assertNotNull(map);
        assertEquals(2, map.size());

        assertEquals("123", map.get("foo"));
        assertEquals("456", map.get("bar"));

        map = catalog.endpointLenientProperties(
                "http:myserver?throwExceptionOnFailure=false&foo=123&bar=456&httpClient.timeout=5000&httpClient.soTimeout=10000&myPrefix.baz=beer");
        assertNotNull(map);
        assertEquals(3, map.size());

        assertEquals("123", map.get("foo"));
        assertEquals("456", map.get("bar"));
        assertEquals("beer", map.get("myPrefix.baz"));
    }

    @Test
    public void testEndpointPropertiesPlaceholders() throws Exception {
        Map<String, String> map = catalog.endpointProperties("timer:foo?period={{howoften}}&repeatCount=5");
        assertNotNull(map);
        assertEquals(3, map.size());

        assertEquals("foo", map.get("timerName"));
        assertEquals("{{howoften}}", map.get("period"));
        assertEquals("5", map.get("repeatCount"));
    }

    @Test
    public void testEndpointPropertiesNettyHttp() throws Exception {
        Map<String, String> map
                = catalog.endpointProperties("netty-http:http://localhost:8080/foo/bar?disconnect=true&keepAlive=false");
        assertNotNull(map);
        assertEquals(6, map.size());

        assertEquals("http", map.get("protocol"));
        assertEquals("localhost", map.get("host"));
        assertEquals("8080", map.get("port"));
        assertEquals("foo/bar", map.get("path"));
        assertEquals("true", map.get("disconnect"));
        assertEquals("false", map.get("keepAlive"));
    }

    @Test
    public void testEndpointPropertiesNettyHttpDefaultPort() throws Exception {
        Map<String, String> map
                = catalog.endpointProperties("netty-http:http:localhost/foo/bar?disconnect=true&keepAlive=false");
        assertNotNull(map);
        assertEquals(5, map.size());

        assertEquals("http", map.get("protocol"));
        assertEquals("localhost", map.get("host"));
        assertEquals("foo/bar", map.get("path"));
        assertEquals("true", map.get("disconnect"));
        assertEquals("false", map.get("keepAlive"));
    }

    @Test
    public void testEndpointPropertiesNettyHttpPlaceholder() throws Exception {
        Map<String, String> map
                = catalog.endpointProperties("netty-http:http://{{myhost}}:{{myport}}/foo/bar?disconnect=true&keepAlive=false");
        assertNotNull(map);
        assertEquals(6, map.size());

        assertEquals("http", map.get("protocol"));
        assertEquals("{{myhost}}", map.get("host"));
        assertEquals("{{myport}}", map.get("port"));
        assertEquals("foo/bar", map.get("path"));
        assertEquals("true", map.get("disconnect"));
        assertEquals("false", map.get("keepAlive"));
    }

    @Test
    public void testEndpointPropertiesNettyHttpWithDoubleSlash() throws Exception {
        Map<String, String> map
                = catalog.endpointProperties("netty-http:http://localhost:8080/foo/bar?disconnect=true&keepAlive=false");
        assertNotNull(map);
        assertEquals(6, map.size());

        assertEquals("http", map.get("protocol"));
        assertEquals("localhost", map.get("host"));
        assertEquals("8080", map.get("port"));
        assertEquals("foo/bar", map.get("path"));
        assertEquals("true", map.get("disconnect"));
        assertEquals("false", map.get("keepAlive"));
    }

    @Test
    public void testAsEndpointUriLog() throws Exception {
        Map<String, String> map = new HashMap<>();
        map.put("loggerName", "foo");
        map.put("loggerLevel", "WARN");
        map.put("multiline", "true");
        map.put("showAll", "true");
        map.put("showBody", "false");
        map.put("showBodyType", "false");
        map.put("showExchangePattern", "false");
        map.put("style", "Tab");

        assertEquals("log:foo?loggerLevel=WARN&multiline=true&showAll=true&style=Tab",
                catalog.asEndpointUri("log", map, false));
    }

    @Test
    public void testAsEndpointUriLogShort() throws Exception {
        Map<String, String> map = new HashMap<>();
        map.put("loggerName", "foo");
        map.put("loggerLevel", "DEBUG");

        assertEquals("log:foo?loggerLevel=DEBUG", catalog.asEndpointUri("log", map, false));
    }

    @Test
    public void testAsEndpointUriWithplaceholder() throws Exception {
        Map<String, String> map = new HashMap<>();
        map.put("query", "{{insert}}");
        assertEquals("sql:{{insert}}", catalog.asEndpointUri("sql", map, false));

        map.put("useMessageBodyForSql", "true");
        assertEquals("sql:{{insert}}?useMessageBodyForSql=true", catalog.asEndpointUri("sql", map, false));

        map.put("parametersCount", "{{count}}");
        assertEquals("sql:{{insert}}?parametersCount={{count}}&useMessageBodyForSql=true",
                catalog.asEndpointUri("sql", map, false));
    }

    @Test
    public void testAsEndpointUriStream() throws Exception {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("kind", "url");
        map.put("url", "http://camel.apache.org");

        assertEquals("stream:url?url=http://camel.apache.org", catalog.asEndpointUri("stream", map, false));
    }

    @Test
    public void testEndpointPropertiesJms() throws Exception {
        Map<String, String> map = catalog.endpointProperties("jms:queue:foo");
        assertNotNull(map);
        assertEquals(2, map.size());

        assertEquals("queue", map.get("destinationType"));
        assertEquals("foo", map.get("destinationName"));

        map = catalog.endpointProperties("jms:foo");
        assertNotNull(map);
        assertEquals(1, map.size());

        assertEquals("foo", map.get("destinationName"));
    }

    @Test
    public void testEndpointPropertiesJmsWithDotInName() throws Exception {
        Map<String, String> map = catalog.endpointProperties("jms:browse.me");
        assertNotNull(map);
        assertEquals(1, map.size());

        assertEquals("browse.me", map.get("destinationName"));

        map = catalog.endpointProperties("jms:browse.me");
        assertNotNull(map);
        assertEquals(1, map.size());

        assertEquals("browse.me", map.get("destinationName"));
    }

    @Test
    public void testEndpointPropertiesJmsRequired() throws Exception {
        Map<String, String> map = catalog.endpointProperties("jms:foo");
        assertNotNull(map);
        assertEquals(1, map.size());

        assertEquals("foo", map.get("destinationName"));

        map = catalog.endpointProperties("jms:foo?allowNullBody=true&deliveryPersistent=false");
        assertNotNull(map);
        assertEquals(3, map.size());

        assertEquals("foo", map.get("destinationName"));
        assertEquals("true", map.get("allowNullBody"));
        assertEquals("false", map.get("deliveryPersistent"));
    }

    @Test
    public void testEndpointPropertiesAtom() throws Exception {
        Map<String, String> map = catalog.endpointProperties("atom:file:src/test/data/feed.atom");
        assertNotNull(map);
        assertEquals(1, map.size());

        assertEquals("file:src/test/data/feed.atom", map.get("feedUri"));

        map = catalog.endpointProperties("atom:file:src/test/data/feed.atom?splitEntries=false&delay=5000");
        assertNotNull(map);
        assertEquals(3, map.size());

        assertEquals("file:src/test/data/feed.atom", map.get("feedUri"));
        assertEquals("false", map.get("splitEntries"));
        assertEquals("5000", map.get("delay"));
    }

    @Test
    public void testEndpointPropertiesMultiValued() throws Exception {
        Map<String, String> map
                = catalog.endpointProperties("http:helloworld?httpClientOptions=httpClient.foo=123&httpClient.bar=456");
        assertNotNull(map);
        assertEquals(2, map.size());

        assertEquals("helloworld", map.get("httpUri"));
        assertEquals("httpClient.foo=123&httpClient.bar=456", map.get("httpClientOptions"));
    }

    @Test
    public void testEndpointPropertiesSshWithUserInfo() throws Exception {
        Map<String, String> map = catalog.endpointProperties("ssh:localhost:8101?username=scott&password=tiger");
        assertNotNull(map);
        assertEquals(4, map.size());
        assertEquals("8101", map.get("port"));
        assertEquals("localhost", map.get("host"));
        assertEquals("scott", map.get("username"));
        assertEquals("tiger", map.get("password"));

        map = catalog.endpointProperties("ssh://scott:tiger@localhost:8101");
        assertNotNull(map);
        assertEquals(4, map.size());
        assertEquals("8101", map.get("port"));
        assertEquals("localhost", map.get("host"));
        assertEquals("scott", map.get("username"));
        assertEquals("tiger", map.get("password"));
    }

    @Test
    public void validateActiveMQProperties() {
        // add activemq as known component
        catalog.addComponent("activemq", "org.apache.camel.component.activemq.ActiveMQComponent");

        // activemq
        EndpointValidationResult result = catalog.validateEndpointProperties("activemq:temp-queue:cheese?jmsMessageType=Bytes");
        assertTrue(result.isSuccess());
        result = catalog.validateEndpointProperties("activemq:temp-queue:cheese?jmsMessageType=Bytes");
        assertTrue(result.isSuccess());
        result = catalog.validateEndpointProperties("activemq:temp-queue:cheese?jmsMessageType=Bytes", false, true, false);
        assertTrue(result.isSuccess());
        result = catalog.validateEndpointProperties("activemq:temp-queue:cheese?jmsMessageType=Bytes", false, false, true);
        assertTrue(result.isSuccess());

        // connection factory
        result = catalog.validateEndpointProperties(
                "activemq:Consumer.Baz.VirtualTopic.FooRequest?connectionFactory=#pooledJmsConnectionFactory");
        assertTrue(result.isSuccess());
    }

    @Test
    public void validateJmsProperties() {
        // jms
        EndpointValidationResult result = catalog.validateEndpointProperties("jms:temp-queue:cheese?jmsMessageType=Bytes");
        assertTrue(result.isSuccess());
        result = catalog.validateEndpointProperties("jms:temp-queue:cheese?jmsMessageType=Bytes");
        assertTrue(result.isSuccess());
        result = catalog.validateEndpointProperties("jms:temp-queue:cheese?jmsMessageType=Bytes", false, true, false);
        assertTrue(result.isSuccess());
        result = catalog.validateEndpointProperties("jms:temp-queue:cheese?jmsMessageType=Bytes", false, false, true);
        assertTrue(result.isSuccess());
    }

    @Test
    public void validateProperties() {
        // valid
        EndpointValidationResult result = catalog.validateEndpointProperties("log:mylog");
        assertTrue(result.isSuccess());

        // unknown
        result = catalog.validateEndpointProperties("log:mylog?level=WARN&foo=bar");
        assertFalse(result.isSuccess());
        assertTrue(result.getUnknown().contains("foo"));
        assertEquals(1, result.getNumberOfErrors());

        // enum
        result = catalog.validateEndpointProperties("jms:unknown:myqueue");
        assertFalse(result.isSuccess());
        assertEquals("unknown", result.getInvalidEnum().get("destinationType"));
        assertEquals("queue", result.getDefaultValues().get("destinationType"));
        assertEquals(1, result.getNumberOfErrors());

        // reference okay
        result = catalog.validateEndpointProperties("jms:queue:myqueue?jmsKeyFormatStrategy=#key");
        assertTrue(result.isSuccess());
        assertEquals(0, result.getNumberOfErrors());

        // reference
        result = catalog.validateEndpointProperties("jms:queue:myqueue?jmsKeyFormatStrategy=foo");
        assertFalse(result.isSuccess());
        assertEquals("foo", result.getInvalidEnum().get("jmsKeyFormatStrategy"));
        assertEquals(1, result.getNumberOfErrors());

        // okay
        result = catalog.validateEndpointProperties(
                "yammer:MESSAGES?accessToken=aaa&consumerKey=bbb&consumerSecret=ccc&useJson=true&initialDelay=500");
        assertTrue(result.isSuccess());

        // unknown component
        result = catalog.validateEndpointProperties("foo:bar?me=you");
        assertTrue(result.isSuccess());
        assertTrue(result.hasWarnings());
        assertEquals("foo", result.getUnknownComponent());
        assertEquals(0, result.getNumberOfErrors());
        assertEquals(1, result.getNumberOfWarnings());

        // invalid boolean but default value
        result = catalog.validateEndpointProperties("log:output?showAll=ggg");
        assertFalse(result.isSuccess());
        assertEquals("ggg", result.getInvalidBoolean().get("showAll"));
        assertEquals(1, result.getNumberOfErrors());

        // dataset
        result = catalog.validateEndpointProperties("dataset:foo?minRate=50");
        assertTrue(result.isSuccess());

        // time pattern
        result = catalog.validateEndpointProperties("timer://foo?fixedRate=true&delay=0&period=2000");
        assertTrue(result.isSuccess());

        // reference lookup
        result = catalog.validateEndpointProperties("timer://foo?fixedRate=#fixed&delay=#myDelay");
        assertTrue(result.isSuccess());

        // optional consumer. prefix
        result = catalog.validateEndpointProperties("file:inbox?consumer.delay=5000&consumer.greedy=true");
        assertTrue(result.isSuccess());

        // optional without consumer. prefix
        result = catalog.validateEndpointProperties("file:inbox?delay=5000&greedy=true");
        assertTrue(result.isSuccess());

        // mixed optional without consumer. prefix
        result = catalog.validateEndpointProperties("file:inbox?delay=5000&consumer.greedy=true");
        assertTrue(result.isSuccess());

        // prefix
        result = catalog.validateEndpointProperties("file:inbox?delay=5000&scheduler.foo=123&scheduler.bar=456");
        assertTrue(result.isSuccess());

        // stub
        result = catalog.validateEndpointProperties("stub:foo?me=123&you=456");
        assertTrue(result.isSuccess());

        // lenient on
        result = catalog.validateEndpointProperties("dataformat:string:marshal?foo=bar");
        assertTrue(result.isSuccess());

        // lenient off
        result = catalog.validateEndpointProperties("dataformat:string:marshal?foo=bar", true);
        assertFalse(result.isSuccess());
        assertTrue(result.getUnknown().contains("foo"));

        // lenient off consumer only
        result = catalog.validateEndpointProperties("netty-http:http://myserver?foo=bar", false, true, false);
        assertFalse(result.isSuccess());
        // consumer should still fail because we cannot use lenient option in consumer mode
        assertEquals("foo", result.getUnknown().iterator().next());
        assertNull(result.getLenient());
        // lenient off producer only
        result = catalog.validateEndpointProperties("netty-http:http://myserver?foo=bar", false, false, true);
        assertTrue(result.isSuccess());
        // foo is the lenient option
        assertEquals(1, result.getLenient().size());
        assertEquals("foo", result.getLenient().iterator().next());

        // lenient on consumer only
        result = catalog.validateEndpointProperties("netty-http:http://myserver?foo=bar", true, true, false);
        assertFalse(result.isSuccess());
        // consumer should still fail because we cannot use lenient option in consumer mode
        assertEquals("foo", result.getUnknown().iterator().next());
        assertNull(result.getLenient());
        // lenient on producer only
        result = catalog.validateEndpointProperties("netty-http:http://myserver?foo=bar", true, false, true);
        assertFalse(result.isSuccess());
        assertEquals("foo", result.getUnknown().iterator().next());
        assertNull(result.getLenient());

        // lenient on rss consumer only
        result = catalog.validateEndpointProperties(
                "rss:file:src/test/data/rss20.xml?splitEntries=true&sortEntries=true&consumer.delay=50&foo=bar", false, true,
                false);
        assertTrue(result.isSuccess());
        assertEquals("foo", result.getLenient().iterator().next());

        // data format
        result = catalog.validateEndpointProperties("dataformat:zipDeflater:marshal?compressionLevel=2", true);
        assertTrue(result.isSuccess());

        // 2 slash after component name
        result = catalog.validateEndpointProperties("atmos://put?remotePath=/dummy.txt");
        assertTrue(result.isSuccess());

        // userinfo in authority with username and password
        result = catalog.validateEndpointProperties("ssh://karaf:karaf@localhost:8101");
        assertTrue(result.isSuccess());

        // userinfo in authority without password
        result = catalog.validateEndpointProperties(
                "ssh://scott@localhost:8101?certResource=classpath:test_rsa&useFixedDelay=true&delay=5000&pollCommand=features:list%0A");
        assertTrue(result.isSuccess());

        // userinfo with both user and password and placeholder
        result = catalog.validateEndpointProperties("ssh://smx:smx@localhost:8181?timeout=3000");
        assertTrue(result.isSuccess());
        // and should also work when port is using a placeholder
        result = catalog.validateEndpointProperties("ssh://smx:smx@localhost:{{port}}?timeout=3000");
        assertTrue(result.isSuccess());

        // placeholder for a bunch of optional options
        result = catalog.validateEndpointProperties("aws-swf://activity?{{options}}");
        assertTrue(result.isSuccess());

        // incapable to parse
        result = catalog.validateEndpointProperties("{{getFtpUrl}}?recursive=true");
        assertTrue(result.isSuccess());
        assertTrue(result.hasWarnings());
        assertNotNull(result.getIncapable());
    }

    @Test
    public void validatePropertiesSummary() {
        EndpointValidationResult result = catalog.validateEndpointProperties(
                "file:foo?blah=yada");
        assertFalse(result.isSuccess());
        String reason = result.summaryErrorMessage(true);
        LOG.info(reason);

        result = catalog.validateEndpointProperties("jms:unknown:myqueue");
        assertFalse(result.isSuccess());
        reason = result.summaryErrorMessage(false);
        LOG.info(reason);
    }

    @Test
    public void validateTimePattern() {
        assertTrue(catalog.validateTimePattern("0"));
        assertTrue(catalog.validateTimePattern("500"));
        assertTrue(catalog.validateTimePattern("10000"));
        assertTrue(catalog.validateTimePattern("5s"));
        assertTrue(catalog.validateTimePattern("5sec"));
        assertTrue(catalog.validateTimePattern("5secs"));
        assertTrue(catalog.validateTimePattern("3m"));
        assertTrue(catalog.validateTimePattern("3min"));
        assertTrue(catalog.validateTimePattern("3minutes"));
        assertTrue(catalog.validateTimePattern("5m15s"));
        assertTrue(catalog.validateTimePattern("1h"));
        assertTrue(catalog.validateTimePattern("1hour"));
        assertTrue(catalog.validateTimePattern("2hours"));

        assertFalse(catalog.validateTimePattern("bla"));
        assertFalse(catalog.validateTimePattern("2year"));
        assertFalse(catalog.validateTimePattern("60darn"));
    }

    @Test
    public void testEndpointComponentName() {
        String name = catalog.endpointComponentName("jms:queue:foo");
        assertEquals("jms", name);
    }

    @Test
    public void testListComponentsAsJson() throws Exception {
        String json = catalog.listComponentsAsJson();
        assertNotNull(json);

        // validate we can parse the json
        ObjectMapper mapper = new ObjectMapper();
        JsonNode tree = mapper.readTree(json);
        assertNotNull(tree);
    }

    @Test
    public void testListDataFormatsAsJson() throws Exception {
        String json = catalog.listDataFormatsAsJson();
        assertNotNull(json);

        // validate we can parse the json
        ObjectMapper mapper = new ObjectMapper();
        JsonNode tree = mapper.readTree(json);
        assertNotNull(tree);
    }

    @Test
    public void testListLanguagesAsJson() throws Exception {
        String json = catalog.listLanguagesAsJson();
        assertNotNull(json);

        // validate we can parse the json
        ObjectMapper mapper = new ObjectMapper();
        JsonNode tree = mapper.readTree(json);
        assertNotNull(tree);
    }

    @Test
    public void testListTransformersAsJson() throws Exception {
        String json = catalog.listTransformersAsJson();
        assertNotNull(json);

        // validate we can parse the json
        ObjectMapper mapper = new ObjectMapper();
        JsonNode tree = mapper.readTree(json);
        assertNotNull(tree);
    }

    @Test
    public void testListModelsAsJson() throws Exception {
        String json = catalog.listModelsAsJson();
        assertNotNull(json);

        // validate we can parse the json
        ObjectMapper mapper = new ObjectMapper();
        JsonNode tree = mapper.readTree(json);
        assertNotNull(tree);
    }

    @Test
    public void testListOthersAsJson() throws Exception {
        String json = catalog.listOthersAsJson();
        assertNotNull(json);

        // validate we can parse the json
        ObjectMapper mapper = new ObjectMapper();
        JsonNode tree = mapper.readTree(json);
        assertNotNull(tree);
    }

    @Test
    public void testSummaryAsJson() throws Exception {
        String json = catalog.summaryAsJson();
        assertNotNull(json);

        // validate we can parse the json
        ObjectMapper mapper = new ObjectMapper();
        JsonNode tree = mapper.readTree(json);
        assertNotNull(tree);
    }

    @Test
    public void testAddComponent() throws Exception {
        catalog.addComponent("dummy", "org.foo.camel.DummyComponent");

        assertTrue(catalog.findComponentNames().contains("dummy"));

        String json = catalog.componentJSonSchema("dummy");
        assertNotNull(json);

        // validate we can parse the json
        ObjectMapper mapper = new ObjectMapper();
        JsonNode tree = mapper.readTree(json);
        assertNotNull(tree);
    }

    @Test
    public void testAddComponentWithJson() throws Exception {
        String json = loadText(new FileInputStream("src/test/resources/org/foo/camel/dummy.json"));
        assertNotNull(json);
        catalog.addComponent("dummy", "org.foo.camel.DummyComponent", json);

        assertTrue(catalog.findComponentNames().contains("dummy"));

        json = catalog.componentJSonSchema("dummy");
        assertNotNull(json);

        // validate we can parse the json
        ObjectMapper mapper = new ObjectMapper();
        JsonNode tree = mapper.readTree(json);
        assertNotNull(tree);
    }

    @Test
    public void testAddComponentWithPrettyJson() throws Exception {
        String json = loadText(new FileInputStream("src/test/resources/org/foo/camel/dummy-pretty.json"));
        assertNotNull(json);
        catalog.addComponent("dummy", "org.foo.camel.DummyComponent", json);

        assertTrue(catalog.findComponentNames().contains("dummy"));

        json = catalog.componentJSonSchema("dummy");
        assertNotNull(json);

        // validate we can parse the json
        ObjectMapper mapper = new ObjectMapper();
        JsonNode tree = mapper.readTree(json);
        assertNotNull(tree);
    }

    @Test
    public void testAddDataFormat() throws Exception {
        catalog.addDataFormat("dummyformat", "org.foo.camel.DummyDataFormat");

        assertTrue(catalog.findDataFormatNames().contains("dummyformat"));

        String json = catalog.dataFormatJSonSchema("dummyformat");
        assertNotNull(json);

        // validate we can parse the json
        ObjectMapper mapper = new ObjectMapper();
        JsonNode tree = mapper.readTree(json);
        assertNotNull(tree);
    }

    @Test
    public void testAddDataFormatWithJSon() throws Exception {
        String json = loadText(new FileInputStream("src/test/resources/org/foo/camel/dummyformat.json"));
        assertNotNull(json);
        catalog.addDataFormat("dummyformat", "org.foo.camel.DummyDataFormat", json);

        assertTrue(catalog.findDataFormatNames().contains("dummyformat"));

        json = catalog.dataFormatJSonSchema("dummyformat");
        assertNotNull(json);

        // validate we can parse the json
        ObjectMapper mapper = new ObjectMapper();
        JsonNode tree = mapper.readTree(json);
        assertNotNull(tree);
    }

    @Test
    public void testAddDataFormatWithPrettyJSon() throws Exception {
        String json = loadText(new FileInputStream("src/test/resources/org/foo/camel/dummyformat-pretty.json"));
        assertNotNull(json);
        catalog.addDataFormat("dummyformat", "org.foo.camel.DummyDataFormat", json);

        assertTrue(catalog.findDataFormatNames().contains("dummyformat"));

        json = catalog.dataFormatJSonSchema("dummyformat");
        assertNotNull(json);

        // validate we can parse the json
        ObjectMapper mapper = new ObjectMapper();
        JsonNode tree = mapper.readTree(json);
        assertNotNull(tree);
    }

    @Test
    public void testSimpleExpression() {
        LanguageValidationResult result = catalog.validateLanguageExpression(null, "simple", "${body}");
        assertTrue(result.isSuccess());
        assertEquals("${body}", result.getText());

        result = catalog.validateLanguageExpression(null, "simple", "${body");
        assertFalse(result.isSuccess());
        assertEquals("${body", result.getText());
        LOG.info(result.getError());
        assertTrue(result.getError().startsWith("expected symbol functionEnd but was eol at location 5"));
        assertEquals("expected symbol functionEnd but was eol", result.getShortError());
        assertEquals(5, result.getIndex());

        result = catalog.validateLanguageExpression(null, "simple", "${bodyxxx}");
        assertFalse(result.isSuccess());
        assertEquals("${bodyxxx}", result.getText());
        LOG.info(result.getError());
        assertEquals("Valid syntax: ${body.OGNL} was: bodyxxx", result.getShortError());
        assertEquals(0, result.getIndex());
    }

    @Test
    public void testSimplePredicate() {
        LanguageValidationResult result = catalog.validateLanguagePredicate(null, "simple", "${body} == 'abc'");
        assertTrue(result.isSuccess());
        assertEquals("${body} == 'abc'", result.getText());

        result = catalog.validateLanguagePredicate(null, "simple", "${body} > ${header.size");
        assertFalse(result.isSuccess());
        assertEquals("${body} > ${header.size", result.getText());
        LOG.info(result.getError());
        assertTrue(result.getError().startsWith("expected symbol functionEnd but was eol at location 22"));
        assertEquals("expected symbol functionEnd but was eol", result.getShortError());
        assertEquals(22, result.getIndex());
    }

    @Test
    public void testPredicatePlaceholder() {
        LanguageValidationResult result = catalog.validateLanguagePredicate(null, "simple", "${body} contains '{{danger}}'");
        assertTrue(result.isSuccess());
        assertEquals("${body} contains '{{danger}}'", result.getText());

        result = catalog.validateLanguagePredicate(null, "simple", "${bdy} contains '{{danger}}'");
        assertFalse(result.isSuccess());
        assertEquals("${bdy} contains '{{danger}}'", result.getText());
        LOG.info(result.getError());
        assertTrue(result.getError().startsWith("Unknown function: bdy at location 0"));
        assertTrue(result.getError().contains("'{{danger}}'"));
        assertEquals("Unknown function: bdy", result.getShortError());
        assertEquals(0, result.getIndex());
    }

    @Test
    public void testValidateLanguage() {
        LanguageValidationResult result = catalog.validateLanguageExpression(null, "simple", "${body}");
        assertTrue(result.isSuccess());
        assertEquals("${body}", result.getText());

        result = catalog.validateLanguageExpression(null, "header", "foo");
        assertTrue(result.isSuccess());
        assertEquals("foo", result.getText());

        result = catalog.validateLanguagePredicate(null, "simple", "${body} > 10");
        assertTrue(result.isSuccess());
        assertEquals("${body} > 10", result.getText());

        result = catalog.validateLanguagePredicate(null, "header", "bar");
        assertTrue(result.isSuccess());
        assertEquals("bar", result.getText());

        result = catalog.validateLanguagePredicate(null, "foobar", "bar");
        assertFalse(result.isSuccess());
        assertEquals("Unknown language foobar", result.getError());

        result = catalog.validateLanguagePredicate(null, "simple", "${body.length} =!= 12");
        assertFalse(result.isSuccess());
        assertEquals("Unexpected token =", result.getShortError());
    }

    @Test
    public void testValidateJSonPathLanguage() {
        LanguageValidationResult result = catalog.validateLanguageExpression(null, "jsonpath", "$.store.book[?(@.price < 10)]");
        assertTrue(result.isSuccess());
        assertEquals("$.store.book[?(@.price < 10)]", result.getText());

        result = catalog.validateLanguageExpression(null, "jsonpath", "$.store.book[?(@.price ^^^ 10)]");
        assertFalse(result.isSuccess());
        assertEquals("$.store.book[?(@.price ^^^ 10)]", result.getText());
        assertEquals("Expected character: )", result.getError());

        // just to call via a configuration option
        result = catalog.validateLanguageExpression(null, "jsonpath?unpackArray=true", "$.store.book[?(@.price < 10)]");
        assertTrue(result.isSuccess());
        assertEquals("$.store.book[?(@.price < 10)]", result.getText());
    }

    @Test
    public void testSpringCamelContext() {
        String xml = catalog.springSchemaAsXml();
        assertNotNull(xml);

        assertTrue(xml.contains("CamelContext using XML configuration"));
    }

    @Test
    public void testTransactedAndPolicyNoOutputs() {
        String json = catalog.modelJSonSchema("transacted");
        assertNotNull(json);
        assertTrue(json.contains("\"output\": false"));
        assertFalse(json.contains("\"outputs\":"));

        json = catalog.modelJSonSchema("policy");
        assertNotNull(json);
        assertTrue(json.contains("\"output\": false"));
        assertFalse(json.contains("\"outputs\":"));
    }

    @Test
    public void testValidateEndpointTwitterSpecial() {
        String uri = "twitter-search://java?{{%s}}";

        EndpointValidationResult result = catalog.validateEndpointProperties(uri);
        assertTrue(result.isSuccess());
    }

    @Test
    public void testValidateApiEndpoint() {
        // there is a type converter that converts from and to to phone number
        String uri = "twilio:call/create?applicationSid=123&from=#555&to=#999";
        EndpointValidationResult result = catalog.validateEndpointProperties(uri);
        assertTrue(result.isSuccess());

        // there is a type converter that converts from and to to phone number
        uri = "twilio:call/create?applicationSid=123&from=#555&to=#999&unknown=true";
        result = catalog.validateEndpointProperties(uri);
        assertFalse(result.isSuccess());
        assertTrue(result.getUnknown().contains("unknown"));

        // call/fetcher does not have from and to parameters
        uri = "twilio:Call/Fetch?applicationSid=123&from=#555&to=#999";
        result = catalog.validateEndpointProperties(uri);
        assertFalse(result.isSuccess());
        assertTrue(result.getUnknown().contains("from"));
        assertTrue(result.getUnknown().contains("to"));

        uri = "zendesk:getTopicsByUser?user_id=123";
        result = catalog.validateEndpointProperties(uri);
        assertTrue(result.isSuccess());

        uri = "zendesk:GET_TOPICS_BY_USER?user_id=123";
        result = catalog.validateEndpointProperties(uri);
        assertTrue(result.isSuccess());

        uri = "zendesk:get-topics-by-user?user_id=123&unknown=true";
        result = catalog.validateEndpointProperties(uri);
        assertFalse(result.isSuccess());
        assertTrue(result.getUnknown().contains("unknown"));

        uri = "twilio:account/fetch";
        result = catalog.validateEndpointProperties(uri);
        assertTrue(result.isSuccess());
        uri = "twilio:account/fetch?pathSid=123";
        result = catalog.validateEndpointProperties(uri);
        assertTrue(result.isSuccess());

        uri = "twilio:account/update";
        result = catalog.validateEndpointProperties(uri);
        assertTrue(result.isSuccess());
        uri = "twilio:account/update?pathSid=123";
        result = catalog.validateEndpointProperties(uri);
        assertTrue(result.isSuccess());
        uri = "twilio:account/read";
        result = catalog.validateEndpointProperties(uri);
        assertFalse(result.isSuccess());
        assertEquals(2, result.getEnumChoices("methodName").size());
        assertTrue(result.getEnumChoices("methodName").contains("fetch"));
        assertTrue(result.getEnumChoices("methodName").contains("update"));

        uri = "twilio:account/read?pathSid=123";
        result = catalog.validateEndpointProperties(uri);
        assertFalse(result.isSuccess());
    }

    @Test
    public void testValidateEndpointTimerDuration() {
        String uri = "timer:foo?period=5s";
        EndpointValidationResult result = catalog.validateEndpointProperties(uri);
        assertTrue(result.isSuccess());

        uri = "timer:foo?period=5p";
        result = catalog.validateEndpointProperties(uri);
        assertFalse(result.isSuccess());
        assertEquals("5p", result.getInvalidDuration().get("period"));
    }

    @Test
    public void testValidateEndpointHttpPropertyPlaceholder() {
        String uri = "http://api.openweathermap.org/data/2.5/weather?{{property.weatherUri}}";
        EndpointValidationResult result = catalog.validateEndpointProperties(uri);
        assertTrue(result.isSuccess());
        result = catalog.validateEndpointProperties(uri, true);
        assertTrue(result.isSuccess());

        // use incorrect style using ${ } as placeholder
        uri = "http://api.openweathermap.org/data/2.5/weather?${property.weatherUri}";
        result = catalog.validateEndpointProperties(uri);
        assertTrue(result.isSuccess());
        result = catalog.validateEndpointProperties(uri, true);
        assertFalse(result.isSuccess());
        assertEquals("${property.weatherUri}", result.getUnknown().iterator().next());
    }

    @Test
    public void testValidateEndpointJmsDefault() {
        String uri = "jms:cheese?maxMessagesPerTask=-1";

        EndpointValidationResult result = catalog.validateEndpointProperties(uri);
        assertTrue(result.isSuccess());
        assertEquals(1, result.getDefaultValues().size());
        assertEquals("-1", result.getDefaultValues().get("maxMessagesPerTask"));
    }

    @Test
    public void testValidateEndpointConsumerOnly() {
        String uri = "file:inbox?bufferSize=4096&readLock=changed&delete=true";
        EndpointValidationResult result = catalog.validateEndpointProperties(uri, false, true, false);
        assertTrue(result.isSuccess());

        uri = "file:inbox?bufferSize=4096&readLock=changed&delete=true&fileExist=Append";
        result = catalog.validateEndpointProperties(uri, false, true, false);
        assertFalse(result.isSuccess());

        assertEquals("fileExist", result.getNotConsumerOnly().iterator().next());
    }

    @Test
    public void testValidateEndpointProducerOnly() {
        String uri = "file:outbox?bufferSize=4096&fileExist=Append";
        EndpointValidationResult result = catalog.validateEndpointProperties(uri, false, false, true);
        assertTrue(result.isSuccess());

        uri = "file:outbox?bufferSize=4096&fileExist=Append&delete=true";
        result = catalog.validateEndpointProperties(uri, false, false, true);
        assertFalse(result.isSuccess());

        assertEquals("delete", result.getNotProducerOnly().iterator().next());
    }

    @Test
    public void testNettyHttpDynamicToIssue() throws Exception {
        String uri = "netty-http:http://10.192.1.10:8080/client/alerts/summary?throwExceptionOnFailure=false";
        Map<String, String> params = catalog.endpointProperties(uri);
        params.remove("path");
        params.remove("throwExceptionOnFailure");

        String resolved = catalog.asEndpointUri("netty-http", params, false);
        assertEquals("netty-http:http://10.192.1.10:8080", resolved);

        // another example with dash in hostname
        uri = "netty-http:http://a-b-c.hostname.tld:8080/anything";
        params = catalog.endpointProperties(uri);
        resolved = catalog.asEndpointUri("netty-http", params, false);
        assertEquals("netty-http:http://a-b-c.hostname.tld:8080/anything", resolved);
    }

    @Test
    public void testValidateConfigurationPropertyComponent() {
        String text = "camel.component.seda.queueSize=1234";
        ConfigurationPropertiesValidationResult result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());

        text = "camel.component.seda.queue-size=1234";
        result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());

        text = "camel.component.seda.queuesize=1234";
        result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());

        text = "camel.component.seda.queueSize=abc";
        result = catalog.validateConfigurationProperty(text);
        assertFalse(result.isSuccess());
        assertEquals("abc", result.getInvalidInteger().get("camel.component.seda.queueSize"));

        text = "camel.component.seda.foo=abc";
        result = catalog.validateConfigurationProperty(text);
        assertFalse(result.isSuccess());
        assertTrue(result.getUnknown().contains("camel.component.seda.foo"));

        text = "camel.component.jms.acknowledgementModeName=abc";
        result = catalog.validateConfigurationProperty(text);
        assertFalse(result.isSuccess());
        assertEquals("abc", result.getInvalidEnum().get("camel.component.jms.acknowledgementModeName"));
        List<String> list = result.getEnumChoices("camel.component.jms.acknowledgementModeName");
        assertEquals(4, list.size());
        assertEquals("SESSION_TRANSACTED", list.get(0));
        assertEquals("CLIENT_ACKNOWLEDGE", list.get(1));
        assertEquals("AUTO_ACKNOWLEDGE", list.get(2));
        assertEquals("DUPS_OK_ACKNOWLEDGE", list.get(3));
    }

    @Test
    public void testValidateConfigurationPropertyLanguage() {
        String text = "camel.language.tokenize.token=;";
        ConfigurationPropertiesValidationResult result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());

        text = "camel.language.tokenize.regex=true";
        result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());

        text = "camel.language.tokenize.regex=abc";
        result = catalog.validateConfigurationProperty(text);
        assertFalse(result.isSuccess());
        assertEquals("abc", result.getInvalidBoolean().get("camel.language.tokenize.regex"));

        text = "camel.language.tokenize.foo=abc";
        result = catalog.validateConfigurationProperty(text);
        assertFalse(result.isSuccess());
        assertTrue(result.getUnknown().contains("camel.language.tokenize.foo"));
    }

    @Test
    public void testValidateConfigurationPropertyDataformat() {
        String text = "camel.dataformat.bindyCsv.type=csv";
        ConfigurationPropertiesValidationResult result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());

        text = "camel.dataformat.bindyCsv.locale=us";
        result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());

        text = "camel.dataformat.bindyCsv.allowEmptyStream=abc";
        result = catalog.validateConfigurationProperty(text);
        assertFalse(result.isSuccess());
        assertEquals("abc", result.getInvalidBoolean().get("camel.dataformat.bindyCsv.allowEmptyStream"));

        text = "camel.dataformat.bindyCsv.foo=abc";
        result = catalog.validateConfigurationProperty(text);
        assertFalse(result.isSuccess());
        assertTrue(result.getUnknown().contains("camel.dataformat.bindyCsv.foo"));

        text = "camel.dataformat.bindyCsv.type=abc";
        result = catalog.validateConfigurationProperty(text);
        assertFalse(result.isSuccess());
        assertEquals("abc", result.getInvalidEnum().get("camel.dataformat.bindyCsv.type"));
        List<String> list = result.getEnumChoices("camel.dataformat.bindyCsv.type");
        assertEquals(3, list.size());
        assertEquals("Csv", list.get(0));
        assertEquals("Fixed", list.get(1));
        assertEquals("KeyValue", list.get(2));
    }

    @Test
    public void testValidateConfigurationPropertyComponentQuartz() {
        String text = "camel.component.quartz.auto-start-scheduler=true";
        ConfigurationPropertiesValidationResult result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());

        text = "camel.component.quartz.properties=#myProp";
        result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());

        text = "camel.component.quartz.properties=123";
        result = catalog.validateConfigurationProperty(text);
        assertFalse(result.isSuccess());

        text = "camel.component.quartz.properties.foo=123";
        result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());

        text = "camel.component.quartz.properties.bar=true";
        result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());

        text = "camel.component.quartz.properties[0]=yes";
        result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());

        text = "camel.component.quartz.properties[1]=no";
        result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());

        text = "camel.component.quartz.properties[foo]=abc";
        result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());

        text = "camel.component.quartz.properties[foo].beer=yes";
        result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());

        text = "camel.component.quartz.properties[foo].drink=no";
        result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());
    }

    @Test
    public void testValidateConfigurationPropertyMain() {
        String text = "camel.main.allow-use-original-message=true";
        ConfigurationPropertiesValidationResult result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());

        // spaces around
        text = "camel.main.allow-use-original-message = true";
        result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());
        text = "camel.main.allow-use-original-message= true";
        result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());
        text = "camel.main.allow-use-original-message =true";
        result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());
        text = "camel.main.allow-use-original-message  =   true";
        result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());

        text = "camel.main.allow-use-original-message=abc";
        result = catalog.validateConfigurationProperty(text);
        assertFalse(result.isSuccess());
        assertEquals("abc", result.getInvalidBoolean().get("camel.main.allow-use-original-message"));

        text = "camel.main.foo=abc";
        result = catalog.validateConfigurationProperty(text);
        assertFalse(result.isSuccess());
        assertTrue(result.getUnknown().contains("camel.main.foo"));

        text = "camel.resilience4j.minimum-number-of-calls=123";
        result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());

        text = "camel.resilience4j.minimum-number-of-calls=abc";
        result = catalog.validateConfigurationProperty(text);
        assertFalse(result.isSuccess());
        assertEquals("abc", result.getInvalidInteger().get("camel.resilience4j.minimum-number-of-calls"));

        text = "camel.resilience4j.slow-call-rate-threshold=12.5";
        result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());

        text = "camel.resilience4j.slow-call-rate-threshold=12x5";
        result = catalog.validateConfigurationProperty(text);
        assertFalse(result.isSuccess());
        assertEquals("12x5", result.getInvalidNumber().get("camel.resilience4j.slow-call-rate-threshold"));

        text = "camel.faulttolerance.timeoutPoolSize=5";
        result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());

        text = "camel.lra.coordinatorUrl=foobar";
        result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());

        text = "camel.threadpool.maxQueueSize=123";
        result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());

        text = "camel.threadpool.maxQueueSize=12x5";
        result = catalog.validateConfigurationProperty(text);
        assertFalse(result.isSuccess());
        assertEquals("12x5", result.getInvalidInteger().get("camel.threadpool.maxQueueSize"));

        text = "camel.health.routesEnabled=abc";
        result = catalog.validateConfigurationProperty(text);
        assertFalse(result.isSuccess());
        assertEquals("abc", result.getInvalidBoolean().get("camel.health.routesEnabled"));
    }

    @Test
    public void testValidateConfigurationPropertyMainMap() {
        String text = "camel.rest.api-properties=#foo";
        ConfigurationPropertiesValidationResult result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());

        text = "camel.rest.api-properties=bar";
        result = catalog.validateConfigurationProperty(text);
        assertFalse(result.isSuccess());
        assertEquals("bar", result.getInvalidMap().get("camel.rest.api-properties"));

        text = "camel.rest.api-properties.foo=abc";
        result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());

        text = "camel.rest.api-properties.bar=123";
        result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());

        text = "camel.rest.api-properties.beer=yes";
        result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());

        text = "camel.rest.api-properties[drink]=no";
        result = catalog.validateConfigurationProperty(text);
        assertTrue(result.isSuccess());
    }

    @Test
    public void validateEnvVariableInSyntax() {
        EndpointValidationResult result
                = catalog.validateEndpointProperties("netty-http:http://foo-bar.{{env:NAMESPACE}}.svc.cluster.local/samples");
        assertTrue(result.isSuccess());

        result = catalog.validateEndpointProperties("netty-http:http://foo-bar/?requestTimeout={{env:TIMEOUT}}");
        assertTrue(result.isSuccess());
    }

    @Test
    public void modelFromMavenGAV() {
        ArtifactModel<?> am = catalog.modelFromMavenGAV("org.apache.camel", "camel-ftp", catalog.getCatalogVersion());
        Assertions.assertInstanceOf(ComponentModel.class, am);
        Assertions.assertEquals("Upload and download files to/from FTP servers.", am.getDescription());

        am = catalog.modelFromMavenGAV("org.apache.camel", "camel-ognl", catalog.getCatalogVersion());
        Assertions.assertInstanceOf(LanguageModel.class, am);
        Assertions.assertEquals("Evaluates an OGNL expression (Apache Commons OGNL).", am.getDescription());

        am = catalog.modelFromMavenGAV("org.apache.camel", "camel-bindy", catalog.getCatalogVersion());
        Assertions.assertInstanceOf(DataFormatModel.class, am);
        Assertions.assertEquals("Marshal and unmarshal between POJOs and Comma separated values (CSV) format using Camel Bindy",
                am.getDescription());

        am = catalog.modelFromMavenGAV("org.apache.camel", "camel-unknown", catalog.getCatalogVersion());
        Assertions.assertNull(am);

        am = catalog.modelFromMavenGAV("org.apache.camel", "camel-jms", null);
        Assertions.assertInstanceOf(ComponentModel.class, am);
        Assertions.assertEquals("Sent and receive messages to/from a JMS Queue or Topic.", am.getDescription());
    }

    @Test
    public void loadFooResourceWithBarKind() throws IOException {
        InputStream is = catalog.loadResource("bar", "foo.txt");
        Assertions.assertNotNull(is);

        String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        Assertions.assertEquals("Hello Camel", content);
    }

    @Test
    public void loadNotExistingResource() {
        InputStream is = catalog.loadResource("bar", "not_exists");
        Assertions.assertNull(is);
    }

    @Test
    public void camelReleases() {
        List<ReleaseModel> list = catalog.camelReleases();
        Assertions.assertTrue(list.size() > 100);

        ReleaseModel rel = list.stream().filter(r -> r.getVersion().equals("3.20.1")).findFirst().orElse(null);
        Assertions.assertNotNull(rel);
        Assertions.assertEquals("3.20.1", rel.getVersion());
        Assertions.assertEquals("2023-01-07", rel.getDate());
        Assertions.assertEquals("2023-12-21", rel.getEol());
        Assertions.assertEquals("lts", rel.getKind());
    }

    @Test
    public void camelQuarkusReleases() {
        List<ReleaseModel> list = catalog.camelQuarkusReleases();
        Assertions.assertTrue(list.size() > 20);

        ReleaseModel rel = list.stream().filter(r -> r.getVersion().equals("2.13.2")).findFirst().orElse(null);
        Assertions.assertNotNull(rel);
        Assertions.assertEquals("2.13.2", rel.getVersion());
        Assertions.assertEquals("2022-12-16", rel.getDate());
        Assertions.assertEquals("2023-07-06", rel.getEol());
        Assertions.assertEquals("lts", rel.getKind());
        Assertions.assertEquals("11", rel.getJdk());
    }

}
