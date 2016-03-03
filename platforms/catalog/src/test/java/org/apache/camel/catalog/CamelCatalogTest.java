/**
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.catalog.CatalogHelper.loadText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CamelCatalogTest {

    static CamelCatalog catalog;

    private static final Logger LOG = LoggerFactory.getLogger(CamelCatalogTest.class);

    @BeforeClass
    public static void createCamelCatalog() {
        catalog = new DefaultCamelCatalog();
    }

    @Test
    public void testGetVersion() throws Exception {
        String version = catalog.getCatalogVersion();
        assertNotNull(version);
    }

    @Test
    public void testFindLanguageNames() throws Exception {
        List<String> names = catalog.findLanguageNames();

        assertTrue(names.contains("el"));
        assertTrue(names.contains("simple"));
        assertTrue(names.contains("spel"));
        assertTrue(names.contains("xpath"));
    }

    @Test
    public void testFindNames() throws Exception {
        List<String> names = catalog.findComponentNames();
        assertNotNull(names);
        assertTrue(names.contains("file"));
        assertTrue(names.contains("log"));
        assertTrue(names.contains("docker"));
        assertTrue(names.contains("jms"));

        names = catalog.findDataFormatNames();
        assertNotNull(names);
        assertTrue(names.contains("bindy-csv"));
        assertTrue(names.contains("hl7"));
        assertTrue(names.contains("jaxb"));
        assertTrue(names.contains("syslog"));

        names = catalog.findLanguageNames();
        assertNotNull(names);
        assertTrue(names.contains("simple"));
        assertTrue(names.contains("groovy"));
        assertTrue(names.contains("mvel"));

        names = catalog.findModelNames();
        assertNotNull(names);
        assertTrue(names.contains("from"));
        assertTrue(names.contains("to"));
        assertTrue(names.contains("recipientList"));
        assertTrue(names.contains("aggregate"));
        assertTrue(names.contains("split"));
        assertTrue(names.contains("loadBalance"));
    }

    @Test
    public void testJsonSchema() throws Exception {
        String schema = catalog.componentJSonSchema("docker");
        assertNotNull(schema);

        schema = catalog.dataFormatJSonSchema("hl7");
        assertNotNull(schema);

        schema = catalog.languageJSonSchema("groovy");
        assertNotNull(schema);

        schema = catalog.modelJSonSchema("aggregate");
        assertNotNull(schema);
    }

    @Test
    public void testXmlSchema() throws Exception {
        String schema = catalog.blueprintSchemaAsXml();
        assertNotNull(schema);

        schema = catalog.springSchemaAsXml();
        assertNotNull(schema);
    }

    @Test
    public void testArchetypeCatalog() throws Exception {
        String schema = catalog.archetypeCatalogAsXml();
        assertNotNull(schema);
    }

    @Test
    public void testAsEndpointUriMapFile() throws Exception {
        Map<String, String> map = new HashMap<String, String>();
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
        Map<String, String> map = new HashMap<String, String>();
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
        Map<String, String> map = new HashMap<String, String>();
        map.put("destinationType", "queue");
        map.put("destinationName", "foo");

        String uri = catalog.asEndpointUri("jms", map, true);
        assertEquals("jms:queue:foo", uri);
    }

    @Test
    public void testAsEndpointUriNetty4http() throws Exception {
        Map<String, String> map = new HashMap<String, String>();
        // use http protocol
        map.put("protocol", "http");
        map.put("host", "localhost");
        map.put("port", "8080");
        map.put("path", "foo/bar");
        map.put("disconnect", "true");

        String uri = catalog.asEndpointUri("netty4-http", map, true);
        assertEquals("netty4-http:http:localhost:8080/foo/bar?disconnect=true", uri);

        // lets switch protocol
        map.put("protocol", "https");

        uri = catalog.asEndpointUri("netty4-http", map, true);
        assertEquals("netty4-http:https:localhost:8080/foo/bar?disconnect=true", uri);

        // lets set a query parameter in the path
        map.put("path", "foo/bar?verbose=true");
        map.put("disconnect", "true");

        uri = catalog.asEndpointUri("netty4-http", map, true);
        assertEquals("netty4-http:https:localhost:8080/foo/bar?verbose=true&disconnect=true", uri);
    }

    @Test
    public void testAsEndpointUriTimer() throws Exception {
        Map<String, String> map = new HashMap<String, String>();
        map.put("timerName", "foo");
        map.put("period", "5000");

        String uri = catalog.asEndpointUri("timer", map, true);
        assertEquals("timer:foo?period=5000", uri);
    }

    @Test
    public void testAsEndpointUriPropertiesPlaceholders() throws Exception {
        Map<String, String> map = new HashMap<String, String>();
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
        Map<String, String> map = new HashMap<String, String>();
        map.put("resourceUri", "foo.xslt");
        map.put("converter", "#myConverter");

        String uri = catalog.asEndpointUri("xslt", map, true);
        assertEquals("xslt:foo.xslt?converter=%23myConverter", uri);

        uri = catalog.asEndpointUri("xslt", map, false);
        assertEquals("xslt:foo.xslt?converter=#myConverter", uri);
    }

    @Test
    public void testAsEndpointUriMapJmsRequiredOnly() throws Exception {
        Map<String, String> map = new HashMap<String, String>();
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
    public void testAsEndpointUriJson() throws Exception {
        String json = loadText(CamelCatalogTest.class.getClassLoader().getResourceAsStream("sample.json"));
        String uri = catalog.asEndpointUri("ftp", json, true);
        assertEquals("ftp:someserver:21/foo?connectTimeout=5000", uri);
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
    public void testEndpointPropertiesPlaceholders() throws Exception {
        Map<String, String> map = catalog.endpointProperties("timer:foo?period={{howoften}}&repeatCount=5");
        assertNotNull(map);
        assertEquals(3, map.size());

        assertEquals("foo", map.get("timerName"));
        assertEquals("{{howoften}}", map.get("period"));
        assertEquals("5", map.get("repeatCount"));
    }

    @Test
    public void testEndpointPropertiesNetty4Http() throws Exception {
        Map<String, String> map = catalog.endpointProperties("netty4-http:http:localhost:8080/foo/bar?disconnect=true&keepAlive=false");
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
    public void testEndpointPropertiesNetty4HttpDefaultPort() throws Exception {
        Map<String, String> map = catalog.endpointProperties("netty4-http:http:localhost/foo/bar?disconnect=true&keepAlive=false");
        assertNotNull(map);
        assertEquals(5, map.size());

        assertEquals("http", map.get("protocol"));
        assertEquals("localhost", map.get("host"));
        assertEquals("foo/bar", map.get("path"));
        assertEquals("true", map.get("disconnect"));
        assertEquals("false", map.get("keepAlive"));
    }

    @Test
    public void testEndpointPropertiesNetty4HttpPlaceholder() throws Exception {
        Map<String, String> map = catalog.endpointProperties("netty4-http:http:{{myhost}}:{{myport}}/foo/bar?disconnect=true&keepAlive=false");
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
    public void testEndpointPropertiesNetty4HttpWithDoubleSlash() throws Exception {
        Map<String, String> map = catalog.endpointProperties("netty4-http:http://localhost:8080/foo/bar?disconnect=true&keepAlive=false");
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
        Map<String, String> map = new HashMap<String, String>();
        map.put("loggerName", "foo");
        map.put("loggerLevel", "WARN");
        map.put("multiline", "true");
        map.put("showAll", "true");
        map.put("showBody", "false");
        map.put("showBodyType", "false");
        map.put("showExchangePattern", "false");
        map.put("style", "Tab");

        assertEquals("log:foo?loggerLevel=WARN&multiline=true&showAll=true&style=Tab", catalog.asEndpointUri("log", map, false));
    }

    @Test
    public void testAsEndpointUriLogShort() throws Exception {
        Map<String, String> map = new HashMap<String, String>();
        map.put("loggerName", "foo");
        map.put("loggerLevel", "DEBUG");

        assertEquals("log:foo?loggerLevel=DEBUG", catalog.asEndpointUri("log", map, false));
    }

    @Test
    public void testAsEndpointUriWithplaceholder() throws Exception {
        Map<String, String> map = new HashMap<String, String>();
        map.put("query", "{{insert}}");
        assertEquals("sql:{{insert}}", catalog.asEndpointUri("sql", map, false));

        map.put("useMessageBodyForSql", "true");
        assertEquals("sql:{{insert}}?useMessageBodyForSql=true", catalog.asEndpointUri("sql", map, false));

        map.put("parametersCount", "{{count}}");
        assertEquals("sql:{{insert}}?parametersCount={{count}}&useMessageBodyForSql=true", catalog.asEndpointUri("sql", map, false));
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
        Map<String, String> map = catalog.endpointProperties("http:helloworld?httpClientOptions=httpClient.foo=123&httpClient.bar=456");
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
    public void validateActiveMQProperties() throws Exception {
        // add activemq as known component
        catalog.addComponent("activemq", "org.apache.activemq.camel.component.ActiveMQComponent");

        // activemq
        EndpointValidationResult result = catalog.validateEndpointProperties("activemq:temp:queue:cheese");
        assertTrue(result.isSuccess());
    }

    @Test
    public void validateProperties() throws Exception {
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
        result = catalog.validateEndpointProperties("yammer:MESSAGES?accessToken=aaa&consumerKey=bbb&consumerSecret=ccc&useJson=true&initialDelay=500");
        assertTrue(result.isSuccess());

        // required / boolean / integer
        result = catalog.validateEndpointProperties("yammer:MESSAGES?accessToken=aaa&consumerKey=&useJson=no&initialDelay=five");
        assertFalse(result.isSuccess());
        assertEquals(4, result.getNumberOfErrors());
        assertTrue(result.getRequired().contains("consumerKey"));
        assertTrue(result.getRequired().contains("consumerSecret"));
        assertEquals("no", result.getInvalidBoolean().get("useJson"));
        assertEquals("five", result.getInvalidInteger().get("initialDelay"));

        // okay
        result = catalog.validateEndpointProperties("mqtt:myqtt?reconnectBackOffMultiplier=2.5");
        assertTrue(result.isSuccess());
        assertEquals(0, result.getNumberOfErrors());

        // number
        result = catalog.validateEndpointProperties("mqtt:myqtt?reconnectBackOffMultiplier=five");
        assertFalse(result.isSuccess());
        assertEquals("five", result.getInvalidNumber().get("reconnectBackOffMultiplier"));
        assertEquals(1, result.getNumberOfErrors());

        // unknown component
        result = catalog.validateEndpointProperties("foo:bar?me=you");
        assertFalse(result.isSuccess());
        assertTrue(result.getUnknownComponent().equals("foo"));
        assertEquals(1, result.getNumberOfErrors());

        // invalid boolean but default value
        result = catalog.validateEndpointProperties("log:output?showAll=ggg");
        assertFalse(result.isSuccess());
        assertEquals("ggg", result.getInvalidBoolean().get("showAll"));
        assertEquals(1, result.getNumberOfErrors());

        // dataset
        result = catalog.validateEndpointProperties("dataset:foo?minRate=50");
        assertTrue(result.isSuccess());

        // time pattern
        result = catalog.validateEndpointProperties("timer://foo?fixedRate=true&delay=0&period=2s");
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

        // data format
        result = catalog.validateEndpointProperties("dataformat:string:marshal?charset=utf-8", true);
        assertTrue(result.isSuccess());

        // 2 slash after component name
        result = catalog.validateEndpointProperties("atmos://put?remotePath=/dummy.txt");
        assertTrue(result.isSuccess());

        // userinfo in authority with username and password
        result = catalog.validateEndpointProperties("ssh://karaf:karaf@localhost:8101");
        assertTrue(result.isSuccess());

        // userinfo in authority without password
        result = catalog.validateEndpointProperties("ssh://scott@localhost:8101?certResource=classpath:test_rsa&useFixedDelay=true&delay=5000&pollCommand=features:list%0A");
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
        assertFalse(result.isSuccess());
        assertTrue(result.getIncapable() != null);
    }

    @Test
    public void validatePropertiesSummary() throws Exception {
        EndpointValidationResult result = catalog.validateEndpointProperties("yammer:MESSAGES?blah=yada&accessToken=aaa&consumerKey=&useJson=no&initialDelay=five&pollStrategy=myStrategy");
        assertFalse(result.isSuccess());
        String reason = result.summaryErrorMessage(true);
        LOG.info(reason);

        result = catalog.validateEndpointProperties("jms:unknown:myqueue");
        assertFalse(result.isSuccess());
        reason = result.summaryErrorMessage(false);
        LOG.info(reason);
    }

    @Test
    public void testEndpointComponentName() throws Exception {
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
    public void testListModelsAsJson() throws Exception {
        String json = catalog.listModelsAsJson();
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
        assertFalse(catalog.findComponentNames().contains("dummy"));

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
    public void testAddDataFormat() throws Exception {
        assertFalse(catalog.findDataFormatNames().contains("dummyformat"));

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
    public void testSimpleExpression() throws Exception {
        SimpleValidationResult result = catalog.validateSimpleExpression("${body}");
        assertTrue(result.isSuccess());
        assertEquals("${body}", result.getSimple());

        result = catalog.validateSimpleExpression("${body");
        assertFalse(result.isSuccess());
        assertEquals("${body", result.getSimple());
        LOG.info(result.getError());
        assertTrue(result.getError().startsWith("expected symbol functionEnd but was eol at location 5"));
    }

    @Test
    public void testSimplePredicate() throws Exception {
        SimpleValidationResult result = catalog.validateSimplePredicate("${body} == 'abc'");
        assertTrue(result.isSuccess());
        assertEquals("${body} == 'abc'", result.getSimple());

        result = catalog.validateSimplePredicate("${body} > ${header.size");
        assertFalse(result.isSuccess());
        assertEquals("${body} > ${header.size", result.getSimple());
        LOG.info(result.getError());
        assertTrue(result.getError().startsWith("expected symbol functionEnd but was eol at location 22"));
    }

}
