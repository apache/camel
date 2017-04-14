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
package org.apache.camel.runtimecatalog;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.impl.DefaultCamelContext;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class RuntimeCamelCatalogTest {

    static RuntimeCamelCatalog catalog;

    private static final Logger LOG = LoggerFactory.getLogger(RuntimeCamelCatalogTest.class);

    @BeforeClass
    public static void createCamelCatalog() {
        catalog = new DefaultRuntimeCamelCatalog(new DefaultCamelContext());
    }

    @Test
    public void testFromCamelContext() throws Exception {
        String schema = new DefaultCamelContext().getRuntimeCamelCatalog().modelJSonSchema("choice");
        assertNotNull(schema);
    }

    @Test
    public void testJsonSchema() throws Exception {
        String schema = catalog.modelJSonSchema("aggregate");
        assertNotNull(schema);

        // lets make it possible to find bean/method using both names
        schema = catalog.modelJSonSchema("method");
        assertNotNull(schema);
        schema = catalog.modelJSonSchema("bean");
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
    public void testEndpointPropertiesPlaceholders() throws Exception {
        Map<String, String> map = catalog.endpointProperties("timer:foo?period={{howoften}}&repeatCount=5");
        assertNotNull(map);
        assertEquals(3, map.size());

        assertEquals("foo", map.get("timerName"));
        assertEquals("{{howoften}}", map.get("period"));
        assertEquals("5", map.get("repeatCount"));
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
        map.put("name", "foo");
        map.put("blockWhenFull", "{{block}}");
        assertEquals("seda:foo?blockWhenFull={{block}}", catalog.asEndpointUri("seda", map, false));
    }

    @Test
    public void testEndpointPropertiesSedaRequired() throws Exception {
        Map<String, String> map = catalog.endpointProperties("seda:foo");
        assertNotNull(map);
        assertEquals(1, map.size());

        assertEquals("foo", map.get("name"));

        map = catalog.endpointProperties("seda:foo?blockWhenFull=true");
        assertNotNull(map);
        assertEquals(2, map.size());

        assertEquals("foo", map.get("name"));
        assertEquals("true", map.get("blockWhenFull"));
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
        result = catalog.validateEndpointProperties("seda:foo?waitForTaskToComplete=blah");
        assertFalse(result.isSuccess());
        assertEquals("blah", result.getInvalidEnum().get("waitForTaskToComplete"));
        assertEquals(1, result.getNumberOfErrors());

        // reference okay
        result = catalog.validateEndpointProperties("seda:foo?queue=#queue");
        assertTrue(result.isSuccess());
        assertEquals(0, result.getNumberOfErrors());

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
    public void validateTimePattern() throws Exception {
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
    public void testEndpointComponentName() throws Exception {
        String name = catalog.endpointComponentName("jms:queue:foo");
        assertEquals("jms", name);
    }

    @Test
    public void testSimpleExpression() throws Exception {
        SimpleValidationResult result = catalog.validateSimpleExpression(null, "${body}");
        assertTrue(result.isSuccess());
        assertEquals("${body}", result.getSimple());

        result = catalog.validateSimpleExpression(null, "${body");
        assertFalse(result.isSuccess());
        assertEquals("${body", result.getSimple());
        LOG.info(result.getError());
        assertTrue(result.getError().startsWith("expected symbol functionEnd but was eol at location 5"));
        assertEquals("expected symbol functionEnd but was eol", result.getShortError());
        assertEquals(5, result.getIndex());
    }

    @Test
    public void testSimplePredicate() throws Exception {
        SimpleValidationResult result = catalog.validateSimplePredicate(null, "${body} == 'abc'");
        assertTrue(result.isSuccess());
        assertEquals("${body} == 'abc'", result.getSimple());

        result = catalog.validateSimplePredicate(null, "${body} > ${header.size");
        assertFalse(result.isSuccess());
        assertEquals("${body} > ${header.size", result.getSimple());
        LOG.info(result.getError());
        assertTrue(result.getError().startsWith("expected symbol functionEnd but was eol at location 22"));
        assertEquals("expected symbol functionEnd but was eol", result.getShortError());
        assertEquals(22, result.getIndex());
    }

    @Test
    public void testSimplePredicatePlaceholder() throws Exception {
        SimpleValidationResult result = catalog.validateSimplePredicate(null, "${body} contains '{{danger}}'");
        assertTrue(result.isSuccess());
        assertEquals("${body} contains '{{danger}}'", result.getSimple());

        result = catalog.validateSimplePredicate(null, "${bdy} contains '{{danger}}'");
        assertFalse(result.isSuccess());
        assertEquals("${bdy} contains '{{danger}}'", result.getSimple());
        LOG.info(result.getError());
        assertTrue(result.getError().startsWith("Unknown function: bdy at location 0"));
        assertTrue(result.getError().contains("'{{danger}}'"));
        assertEquals("Unknown function: bdy", result.getShortError());
        assertEquals(0, result.getIndex());
    }

    @Test
    public void testValidateLanguage() throws Exception {
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
    }

    @Test
    public void testValidateEndpointConsumerOnly() throws Exception {
        String uri = "file:inbox?bufferSize=4096&readLock=changed&delete=true";
        EndpointValidationResult result = catalog.validateEndpointProperties(uri, false, true, false);
        assertTrue(result.isSuccess());

        uri = "file:inbox?bufferSize=4096&readLock=changed&delete=true&fileExist=Append";
        result = catalog.validateEndpointProperties(uri, false, true, false);
        assertFalse(result.isSuccess());

        assertEquals("fileExist", result.getNotConsumerOnly().iterator().next());
    }

    @Test
    public void testValidateEndpointProducerOnly() throws Exception {
        String uri = "file:outbox?bufferSize=4096&fileExist=Append";
        EndpointValidationResult result = catalog.validateEndpointProperties(uri, false, false, true);
        assertTrue(result.isSuccess());

        uri = "file:outbox?bufferSize=4096&fileExist=Append&delete=true";
        result = catalog.validateEndpointProperties(uri, false, false, true);
        assertFalse(result.isSuccess());

        assertEquals("delete", result.getNotProducerOnly().iterator().next());
    }

}
