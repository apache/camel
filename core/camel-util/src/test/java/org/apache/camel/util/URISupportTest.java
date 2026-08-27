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
package org.apache.camel.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class URISupportTest {

    @Test
    public void testNormalizeEndpointUri() throws Exception {
        String out1 = URISupport.normalizeUri("smtp://localhost?username=davsclaus&password=secret");
        String out2 = URISupport.normalizeUri("smtp://localhost?password=secret&username=davsclaus");
        assertEquals(out1, out2);

        out1 = URISupport.normalizeUri("smtp://localhost?username=davsclaus&password=secret");
        out2 = URISupport.normalizeUri("smtp:localhost?password=secret&username=davsclaus");
        assertEquals(out1, out2);

        out1 = URISupport.normalizeUri("smtp:localhost?password=secret&username=davsclaus");
        out2 = URISupport.normalizeUri("smtp://localhost?username=davsclaus&password=secret");
        assertEquals(out1, out2);

        out1 = URISupport.normalizeUri("seda:foo?concurrentConsumer=2");
        out2 = URISupport.normalizeUri("seda:foo?concurrentConsumer=2");
        assertEquals(out1, out2);

        out1 = URISupport.normalizeUri("seda:foo?concurrentConsumer=2");
        out2 = URISupport.normalizeUri("seda:foo");
        assertNotSame(out1, out2);

        out1 = URISupport.normalizeUri("foo:?test=1");
        out2 = URISupport.normalizeUri("foo://?test=1");
        assertEquals("foo://?test=1", out2);
        assertEquals(out1, out2);
    }

    @Test
    public void testNormalizeEndpointUriNoParam() throws Exception {
        String out1 = URISupport.normalizeUri("direct:foo");
        String out2 = URISupport.normalizeUri("direct:foo");
        assertEquals(out1, out2);

        out1 = URISupport.normalizeUri("direct://foo");
        out2 = URISupport.normalizeUri("direct://foo");
        assertEquals(out1, out2);

        out1 = URISupport.normalizeUri("direct:foo");
        out2 = URISupport.normalizeUri("direct://foo");
        assertEquals(out1, out2);

        out1 = URISupport.normalizeUri("direct://foo");
        out2 = URISupport.normalizeUri("direct:foo");
        assertEquals(out1, out2);

        out1 = URISupport.normalizeUri("direct://foo");
        out2 = URISupport.normalizeUri("direct:bar");
        assertNotSame(out1, out2);
    }

    @Test
    public void testNormalizeEndpointUriWithFragments() throws Exception {
        String out1 = URISupport.normalizeUri("irc://someserver/#camel?user=davsclaus");
        String out2 = URISupport.normalizeUri("irc:someserver/#camel?user=davsclaus");
        assertEquals(out1, out2);

        out1 = URISupport.normalizeUri("irc://someserver/#camel?user=davsclaus");
        out2 = URISupport.normalizeUri("irc:someserver/#camel?user=hadrian");
        assertNotSame(out1, out2);
    }

    @Test
    public void testNormalizeHttpEndpoint() throws Exception {
        String out1 = URISupport.normalizeUri("http://www.google.com?q=Camel");
        String out2 = URISupport.normalizeUri("http:www.google.com?q=Camel");
        assertEquals(out1, out2);
        assertTrue(out1.startsWith("http://"), "Should have //");
        assertTrue(out2.startsWith("http://"), "Should have //");

    }

    @Test
    public void testNormalizeIPv6HttpEndpoint() throws Exception {
        String result = URISupport.normalizeUri("http://[2a00:8a00:6000:40::1413]:30300/test");
        assertEquals("http://[2a00:8a00:6000:40::1413]:30300/test", result);
    }

    @Test
    public void testNormalizeHttpEndpointUnicodedParameter() throws Exception {
        String out = URISupport.normalizeUri("http://www.google.com?q=S\u00F8ren");
        assertEquals("http://www.google.com?q=S%C3%B8ren", out);
    }

    @Test
    public void testParseParametersUnicodedValue() throws Exception {
        String out = URISupport.normalizeUri("http://www.google.com?q=S\u00F8ren");
        URI uri = new URI(out);

        Map<String, Object> parameters = URISupport.parseParameters(uri);

        assertEquals(1, parameters.size());
        assertEquals("S\u00F8ren", parameters.get("q"));
    }

    @Test
    public void testNormalizeHttpEndpointURLEncodedParameter() throws Exception {
        String out = URISupport.normalizeUri("http://www.google.com?q=S%C3%B8ren%20Hansen");
        assertEquals("http://www.google.com?q=S%C3%B8ren+Hansen", out);
    }

    @Test
    public void testParseParametersURLEncodedValue() throws Exception {
        String out = URISupport.normalizeUri("http://www.google.com?q=S%C3%B8ren%20Hansen");
        URI uri = new URI(out);

        Map<String, Object> parameters = URISupport.parseParameters(uri);

        assertEquals(1, parameters.size());
        assertEquals("S\u00F8ren Hansen", parameters.get("q"));
    }

    @Test
    public void testNormalizeUriWhereParameterIsFaulty() throws Exception {
        String out = URISupport.normalizeUri("stream:uri?file:///d:/temp/data/log/quickfix.log&scanStream=true");
        assertNotNull(out);
    }

    @Test
    public void testCreateRemainingURI() throws Exception {
        URI original = new URI("http://camel.apache.org");
        Map<String, Object> param = new HashMap<>();
        param.put("foo", "123");
        URI newUri = URISupport.createRemainingURI(original, param);
        assertNotNull(newUri);

        String s = newUri.toString();
        assertEquals("http://camel.apache.org?foo=123", s);
    }

    @Test
    public void testCreateURIWithQueryHasOneFragment() throws Exception {
        URI uri = new URI("smtp://localhost#fragmentOne");
        URI resultUri = URISupport.createURIWithQuery(uri, null);
        assertNotNull(resultUri);
        assertEquals("smtp://localhost#fragmentOne", resultUri.toString());
    }

    @Test
    public void testCreateURIWithQueryHasOneFragmentAndQueryParameter() throws Exception {
        URI uri = new URI("smtp://localhost#fragmentOne");
        URI resultUri = URISupport.createURIWithQuery(uri, "utm_campaign=launch");
        assertNotNull(resultUri);
        assertEquals("smtp://localhost?utm_campaign=launch#fragmentOne", resultUri.toString());
    }

    @Test
    public void testCreateURIWithQueryEmptyQueryString() throws Exception {
        URI uri = new URI("https://api.example.com/users/myuser/repos");
        URI resultUri = URISupport.createURIWithQuery(uri, "");
        assertNotNull(resultUri);
        assertEquals("https://api.example.com/users/myuser/repos", resultUri.toString());
    }

    @Test
    public void testNormalizeEndpointWithEqualSignInParameter() throws Exception {
        String out = URISupport.normalizeUri("jms:queue:foo?selector=somekey='somevalue'&foo=bar");
        assertNotNull(out);
        // Camel will safe encode the URI - '=' stays escaped as it is structurally significant in
        // the query syntax, but the single quotes (legal unescaped in a URI query) are left as-is
        assertEquals("jms://queue:foo?foo=bar&selector=somekey%3D'somevalue'", out);
    }

    @Test
    public void testNormalizeEndpointWithPercentSignInParameter() throws Exception {
        String out = URISupport.normalizeUri("http://someendpoint?username=james&password=%25test");
        assertNotNull(out);
        // Camel will safe encode the URI
        assertEquals("http://someendpoint?password=%25test&username=james", out);
    }

    @Test
    public void testParseParameters() throws Exception {
        URI u = new URI("quartz:myGroup/myTimerName?cron=0%200%20*%20*%20*%20?");
        Map<String, Object> params = URISupport.parseParameters(u);
        assertEquals(1, params.size());
        assertEquals("0 0 * * * ?", params.get("cron"));

        u = new URI("quartz:myGroup/myTimerName?cron=0%200%20*%20*%20*%20?&bar=123");
        params = URISupport.parseParameters(u);
        assertEquals(2, params.size());
        assertEquals("0 0 * * * ?", params.get("cron"));
        assertEquals("123", params.get("bar"));
    }

    @Test
    public void testCreateRemainingURIEncoding() throws Exception {
        // the uri is already encoded, but we create a new one with new query parameters
        String uri = "http://localhost:23271/myapp/mytest?columns=name%2Ctotalsens%2Cupsens&username=apiuser";

        // these are the parameters which is tricky to encode
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("foo", "abc def");
        map.put("bar", "123,456");
        map.put("name", "S\u00F8ren"); // danish letter

        // create new uri with the parameters
        URI out = URISupport.createRemainingURI(new URI(uri), map);
        assertNotNull(out);
        assertEquals("http://localhost:23271/myapp/mytest?foo=abc+def&bar=123%2C456&name=S%C3%B8ren", out.toString());
        assertEquals("http://localhost:23271/myapp/mytest?foo=abc+def&bar=123%2C456&name=S%C3%B8ren", out.toASCIIString());
    }

    @Test
    public void testNormalizeEndpointUriWithDualParameters() throws Exception {
        String out1 = URISupport.normalizeUri("smtp://localhost?to=foo&to=bar&from=me");
        assertEquals("smtp://localhost?from=me&to=foo&to=bar", out1);

        String out2 = URISupport.normalizeUri("smtp://localhost?to=foo&to=bar&from=me&from=you");
        assertEquals("smtp://localhost?from=me&from=you&to=foo&to=bar", out2);
    }

    @Test
    public void testNormalizeEndpointUriSort() throws Exception {
        String out1 = URISupport.normalizeUri("smtp://localhost?to=foo&from=me");
        assertEquals("smtp://localhost?from=me&to=foo", out1);

        String out2 = URISupport.normalizeUri("smtp://localhost?from=me&to=foo");
        assertEquals("smtp://localhost?from=me&to=foo", out2);

        assertEquals(out1, out2);
    }

    @Test
    public void testNormalizeEndpointUriOrderIndependentWithColonValue() throws Exception {
        // CAMEL-24524: a value containing a colon (eg host:port) must normalize the same way
        // regardless of whether the original parameter order already happened to be alphabetical.
        // ':' is legal unescaped in a URI query (RFC 3986 pchar) so it must not be percent-encoded.
        String out1 = URISupport.normalizeUri("kafka:mytopic?brokers=localhost:19092&groupId=mygroup");
        String out2 = URISupport.normalizeUri("kafka:mytopic?groupId=mygroup&brokers=localhost:19092");

        assertThat(out1).isEqualTo(out2);
        assertThat(out1).isEqualTo("kafka://mytopic?brokers=localhost:19092&groupId=mygroup");
    }

    @Test
    public void testNormalizeEndpointUriOrderIndependentWithThreeParameters() throws Exception {
        // all 6 permutations of 3 keys (one already alphabetical, some not) must normalize identically
        String[] permutations = new String[] {
                "kafka:mytopic?brokers=localhost:19092&groupId=mygroup&clientId=myclient",
                "kafka:mytopic?brokers=localhost:19092&clientId=myclient&groupId=mygroup",
                "kafka:mytopic?groupId=mygroup&brokers=localhost:19092&clientId=myclient",
                "kafka:mytopic?groupId=mygroup&clientId=myclient&brokers=localhost:19092",
                "kafka:mytopic?clientId=myclient&brokers=localhost:19092&groupId=mygroup",
                "kafka:mytopic?clientId=myclient&groupId=mygroup&brokers=localhost:19092" };

        String expected = "kafka://mytopic?brokers=localhost:19092&clientId=myclient&groupId=mygroup";
        for (String uri : permutations) {
            assertThat(URISupport.normalizeUri(uri)).as("normalizing: " + uri).isEqualTo(expected);
        }
    }

    @Test
    public void testNormalizeEndpointUriOrderIndependentSingleParameterWithColonValue() throws Exception {
        // CAMEL-24524: the single-parameter shortcut must also normalize the value consistently
        String out = URISupport.normalizeUri("kafka:mytopic?brokers=localhost:19092");
        assertThat(out).isEqualTo("kafka://mytopic?brokers=localhost:19092");
    }

    @Test
    public void testNormalizeEndpointUriOrderIndependentWithCommaValue() throws Exception {
        // a value with a comma (safe for the fast parser, and legal unescaped in a URI query)
        // must also normalize the same regardless of key order, without being percent-encoded
        String out1 = URISupport.normalizeUri("smtp://localhost?subject=Hello,World&username=davsclaus");
        String out2 = URISupport.normalizeUri("smtp://localhost?username=davsclaus&subject=Hello,World");

        assertThat(out1).isEqualTo(out2);
        assertThat(out1).isEqualTo("smtp://localhost?subject=Hello,World&username=davsclaus");
    }

    @Test
    public void testNormalizeEndpointUriOrderIndependentWithSlashValue() throws Exception {
        // CAMEL-24524 follow-up: a value with a slash (eg a MIME type such as
        // produces=application/json, as built by camel-rest-openapi's RestOpenApiEndpoint) must not
        // be percent-encoded - '/' is legal unescaped in a URI query (RFC 3986 pchar). This previously
        // broke RestOpenApiEndpointV3Test#shouldNotCrossContaminateProducersForSameOperation once the
        // fast-path normalizer started always re-encoding the query, even when the keys ("host",
        // "produces") were already in alphabetical order.
        String out1 = URISupport.normalizeUri("foo:bar?host=http://petstore.example.com&produces=application/json");
        String out2 = URISupport.normalizeUri("foo:bar?produces=application/json&host=http://petstore.example.com");

        assertThat(out1).isEqualTo(out2);
        assertThat(out1).isEqualTo("foo://bar?host=http://petstore.example.com&produces=application/json");
    }

    @Test
    public void testNormalizeEndpointUriOrderIndependentIsIdempotent() throws Exception {
        // normalizing an already-normalized uri must return the exact same string
        String out1 = URISupport.normalizeUri("kafka:mytopic?groupId=mygroup&brokers=localhost:19092");
        String out2 = URISupport.normalizeUri(out1);

        assertThat(out2).isEqualTo(out1);
    }

    @Test
    public void testNormalizeEndpointUriOrderIndependentWithRawValue() throws Exception {
        // RAW() values must not be further encoded, regardless of key order
        String out1 = URISupport.normalizeUri("kafka:mytopic?password=RAW(p@ss:word)&username=scott");
        String out2 = URISupport.normalizeUri("kafka:mytopic?username=scott&password=RAW(p@ss:word)");

        assertThat(out1).isEqualTo(out2);
        assertThat(out1).isEqualTo("kafka://mytopic?password=RAW(p@ss:word)&username=scott");
    }

    @Test
    public void testNormalizeEndpointUriOrderIndependentWithDualParametersAndColonValue() throws Exception {
        // duplicate keys (list values) combined with a colon value, which is legal unescaped
        String out1 = URISupport.normalizeUri("smtp://localhost?to=foo:1&to=bar:2&from=me");
        String out2 = URISupport.normalizeUri("smtp://localhost?from=me&to=foo:1&to=bar:2");

        assertThat(out1).isEqualTo(out2);
        assertThat(out1).isEqualTo("smtp://localhost?from=me&to=foo:1&to=bar:2");
    }

    @Test
    public void testSanitizeAccessToken() {
        String out1 = URISupport
                .sanitizeUri("google-sheets-stream://spreadsheets?accessToken=MY_TOKEN&clientId=foo&clientSecret=MY_SECRET");
        assertEquals("google-sheets-stream://spreadsheets?accessToken=xxxxxx&clientId=xxxxxx&clientSecret=xxxxxx", out1);
    }

    @Test
    public void testSanitizeAuthorizationToken() {
        String out1 = URISupport.sanitizeUri("telegram:bots?authorizationToken=1234567890:XXAuthTokenHereXX");
        assertEquals("telegram:bots?authorizationToken=xxxxxx", out1);
    }

    @Test
    void testSanitizeApiKeyHeader() {
        // a hyphenated api-key header name (e.g. the Azure OpenAI auth header supplied via additionalHeader.*)
        // must be redacted from a sanitized URI, just like the camelCase apiKey option
        String out = URISupport.sanitizeUri("openai:CHAT?baseUrl=https://host&additionalHeader.api-key=MY_SECRET");
        assertThat(out).isEqualTo("openai:CHAT?baseUrl=https://host&additionalHeader.api-key=xxxxxx");
    }

    @Test
    void testSanitizeAuthorizationHeader() {
        // a bare Authorization header (e.g. supplied via additionalHeader.*) must be redacted
        String out = URISupport.sanitizeUri("openai:CHAT?additionalHeader.Authorization=Bearer%20MY_SECRET");
        assertThat(out).isEqualTo("openai:CHAT?additionalHeader.Authorization=xxxxxx");
    }

    @Test
    public void testSanitizeUriWithUserInfo() {
        String uri = "jt400://GEORGE:HARRISON@LIVERPOOL/QSYS.LIB/BEATLES.LIB/PENNYLANE.DTAQ";
        String expected = "jt400://GEORGE:xxxxxx@LIVERPOOL/QSYS.LIB/BEATLES.LIB/PENNYLANE.DTAQ";
        assertEquals(expected, URISupport.sanitizeUri(uri));
    }

    @Test
    public void testSanitizeUriWithUserInfoAndColonPassword() {
        String uri = "sftp://USERNAME:HARRISON:COLON@sftp.server.test";
        String expected = "sftp://USERNAME:xxxxxx@sftp.server.test";
        assertEquals(expected, URISupport.sanitizeUri(uri));
    }

    @Test
    public void testSanitizePathWithUserInfo() {
        String path = "GEORGE:HARRISON@LIVERPOOL/QSYS.LIB/BEATLES.LIB/PENNYLANE.PGM";
        String expected = "GEORGE:xxxxxx@LIVERPOOL/QSYS.LIB/BEATLES.LIB/PENNYLANE.PGM";
        assertEquals(expected, URISupport.sanitizePath(path));
    }

    @Test
    public void testSanitizePathWithUserInfoAndColonPassword() {
        String path = "USERNAME:HARRISON:COLON@sftp.server.test";
        String expected = "USERNAME:xxxxxx@sftp.server.test";
        assertEquals(expected, URISupport.sanitizePath(path));
    }

    @Test
    public void testSanitizePathWithoutSensitiveInfoIsUnchanged() {
        String path = "myhost:8080/mypath";
        assertEquals(path, URISupport.sanitizePath(path));
    }

    @Test
    public void testSanitizeUriWithRawPassword() {
        String uri1 = "http://foo?username=me&password=RAW(me#@123)&foo=bar";
        String uri2 = "http://foo?username=me&password=RAW{me#@123}&foo=bar";
        String expected = "http://foo?username=xxxxxx&password=xxxxxx&foo=bar";
        assertEquals(expected, URISupport.sanitizeUri(uri1));
        assertEquals(expected, URISupport.sanitizeUri(uri2));
    }

    @Test
    public void testSanitizeUriRawUnsafePassword() {
        String uri1 = "sftp://localhost/target?password=RAW(beforeAmp&afterAmp)&username=jrandom";
        String uri2 = "sftp://localhost/target?password=RAW{beforeAmp&afterAmp}&username=jrandom";
        String expected = "sftp://localhost/target?password=xxxxxx&username=xxxxxx";
        assertEquals(expected, URISupport.sanitizeUri(uri1));
        assertEquals(expected, URISupport.sanitizeUri(uri2));
    }

    @Test
    public void testSanitizeUriWithRawPasswordAndSimpleExpression() {
        String uriPlain
                = "http://foo?username=me&password=RAW(me#@123)&foo=bar&port=21&tempFileName=${file:name.noext}.tmp&anotherOption=true";
        String uriCurly
                = "http://foo?username=me&password=RAW{me#@123}&foo=bar&port=21&tempFileName=${file:name.noext}.tmp&anotherOption=true";
        String expected
                = "http://foo?username=xxxxxx&password=xxxxxx&foo=bar&port=21&tempFileName=${file:name.noext}.tmp&anotherOption=true";
        assertEquals(expected, URISupport.sanitizeUri(uriPlain));
        assertEquals(expected, URISupport.sanitizeUri(uriCurly));
    }

    @Test
    public void testSanitizeSaslJaasConfig() {
        String out1 = URISupport.sanitizeUri(
                "kafka://MY-TOPIC-NAME?saslJaasConfig=org.apache.kafka.common.security.plain.PlainLoginModule required username=scott password=tiger");
        assertEquals("kafka://MY-TOPIC-NAME?saslJaasConfig=xxxxxx", out1);
    }

    @Test
    public void testNormalizeEndpointUriWithUserInfoSpecialSign() throws Exception {
        String out1 = URISupport.normalizeUri("ftp://us%40r:t%st@localhost:21000/tmp3/camel?foo=us@r");
        assertEquals("ftp://us%40r:t%25st@localhost:21000/tmp3/camel?foo=us%40r", out1);

        String out2 = URISupport.normalizeUri("ftp://us%40r:t%25st@localhost:21000/tmp3/camel?foo=us@r");
        assertEquals("ftp://us%40r:t%25st@localhost:21000/tmp3/camel?foo=us%40r", out2);

        String out3 = URISupport.normalizeUri("ftp://us@r:t%st@localhost:21000/tmp3/camel?foo=us@r");
        assertEquals("ftp://us%40r:t%25st@localhost:21000/tmp3/camel?foo=us%40r", out3);

        String out4 = URISupport.normalizeUri("ftp://us@r:t%25st@localhost:21000/tmp3/camel?foo=us@r");
        assertEquals("ftp://us%40r:t%25st@localhost:21000/tmp3/camel?foo=us%40r", out4);
    }

    @Test
    public void testSpecialUriFromXmppComponent() throws Exception {
        String out1 = URISupport
                .normalizeUri("xmpp://camel-user@localhost:123/test-user@localhost?password=secret&serviceName=someCoolChat");
        assertEquals("xmpp://camel-user@localhost:123/test-user@localhost?password=secret&serviceName=someCoolChat", out1);
    }

    @Test
    public void testRawParameter() throws Exception {
        String out = URISupport.normalizeUri(
                "xmpp://camel-user@localhost:123/test-user@localhost?password=RAW(++?w0rd)&serviceName=some chat");
        assertEquals("xmpp://camel-user@localhost:123/test-user@localhost?password=RAW(++?w0rd)&serviceName=some+chat", out);

        String out2 = URISupport.normalizeUri(
                "xmpp://camel-user@localhost:123/test-user@localhost?password=RAW(foo %% bar)&serviceName=some chat");
        // Just make sure the RAW parameter can be resolved rightly, we need to replace the % into %25
        assertEquals("xmpp://camel-user@localhost:123/test-user@localhost?password=RAW(foo %25%25 bar)&serviceName=some+chat",
                out2);
    }

    @Test
    public void testRawParameterCurly() throws Exception {
        String out = URISupport.normalizeUri(
                "xmpp://camel-user@localhost:123/test-user@localhost?password=RAW{++?w0rd}&serviceName=some chat");
        assertEquals("xmpp://camel-user@localhost:123/test-user@localhost?password=RAW{++?w0rd}&serviceName=some+chat", out);

        String out2 = URISupport.normalizeUri(
                "xmpp://camel-user@localhost:123/test-user@localhost?password=RAW{foo %% bar}&serviceName=some chat");
        // Just make sure the RAW parameter can be resolved rightly, we need to replace the % into %25
        assertEquals("xmpp://camel-user@localhost:123/test-user@localhost?password=RAW{foo %25%25 bar}&serviceName=some+chat",
                out2);
    }

    @Test
    public void testParseQuery() throws Exception {
        Map<String, Object> map = URISupport.parseQuery("password=secret&serviceName=somechat");
        assertEquals(2, map.size());
        assertEquals("secret", map.get("password"));
        assertEquals("somechat", map.get("serviceName"));

        map = URISupport.parseQuery("password=RAW(++?w0rd)&serviceName=somechat");
        assertEquals(2, map.size());
        assertEquals("RAW(++?w0rd)", map.get("password"));
        assertEquals("somechat", map.get("serviceName"));

        map = URISupport.parseQuery("password=RAW(++?)w&rd)&serviceName=somechat");
        assertEquals(2, map.size());
        assertEquals("RAW(++?)w&rd)", map.get("password"));
        assertEquals("somechat", map.get("serviceName"));

        map = URISupport.parseQuery("password=RAW(%2520w&rd)&serviceName=somechat");
        assertEquals(2, map.size());
        assertEquals("RAW(%2520w&rd)", map.get("password"));
        assertEquals("somechat", map.get("serviceName"));
    }

    @Test
    public void testParseQueryCurly() throws Exception {
        Map<String, Object> map = URISupport.parseQuery("password=RAW{++?w0rd}&serviceName=somechat");
        assertEquals(2, map.size());
        assertEquals("RAW{++?w0rd}", map.get("password"));
        assertEquals("somechat", map.get("serviceName"));

        map = URISupport.parseQuery("password=RAW{++?)w&rd}&serviceName=somechat");
        assertEquals(2, map.size());
        assertEquals("RAW{++?)w&rd}", map.get("password"));
        assertEquals("somechat", map.get("serviceName"));

        map = URISupport.parseQuery("password=RAW{%2520w&rd}&serviceName=somechat");
        assertEquals(2, map.size());
        assertEquals("RAW{%2520w&rd}", map.get("password"));
        assertEquals("somechat", map.get("serviceName"));
    }

    @Test
    public void testParseQueryLenient() throws Exception {
        assertThrows(URISyntaxException.class,
                () -> URISupport.parseQuery("password=secret&serviceName=somechat&", false, false),
                "Should have thrown a URISyntaxException");

        Map<String, Object> map = URISupport.parseQuery("password=secret&serviceName=somechat&", false, true);
        assertEquals(2, map.size());
        assertEquals("secret", map.get("password"));
        assertEquals("somechat", map.get("serviceName"));
    }

    @Test
    public void testScanRaw() {
        List<Pair<Integer>> pairs1 = URISupport.scanRaw("password=RAW(++?5w0rd)&serviceName=somechat");
        assertEquals(1, pairs1.size());
        assertEquals(new Pair<Integer>(9, 21), pairs1.get(0));

        List<Pair<Integer>> pairs2 = URISupport.scanRaw("password=RAW{++?5w0rd}&serviceName=somechat");
        assertEquals(1, pairs2.size());
        assertEquals(new Pair<Integer>(9, 21), pairs2.get(0));

        List<Pair<Integer>> pairs3 = URISupport.scanRaw("password=RAW{++?)&0rd}&serviceName=somechat");
        assertEquals(1, pairs3.size());
        assertEquals(new Pair<Integer>(9, 21), pairs3.get(0));

        List<Pair<Integer>> pairs4 = URISupport.scanRaw("password1=RAW(++?}&0rd)&password2=RAW{++?)&0rd}&serviceName=somechat");
        assertEquals(2, pairs4.size());
        assertEquals(new Pair<Integer>(10, 22), pairs4.get(0));
        assertEquals(new Pair<Integer>(34, 46), pairs4.get(1));
    }

    @Test
    public void testIsRaw() {
        List<Pair<Integer>> pairs = Arrays.asList(
                new Pair<Integer>(3, 5),
                new Pair<Integer>(8, 10));
        for (int i = 0; i < 3; i++) {
            assertFalse(URISupport.isRaw(i, pairs));
        }
        for (int i = 3; i < 6; i++) {
            assertTrue(URISupport.isRaw(i, pairs));
        }
        for (int i = 6; i < 8; i++) {
            assertFalse(URISupport.isRaw(i, pairs));
        }
        for (int i = 8; i < 11; i++) {
            assertTrue(URISupport.isRaw(i, pairs));
        }
        for (int i = 11; i < 15; i++) {
            assertFalse(URISupport.isRaw(i, pairs));
        }
    }

    @Test
    public void testResolveRawParameterValues() throws Exception {
        Map<String, Object> map = URISupport.parseQuery("password=secret&serviceName=somechat");
        URISupport.resolveRawParameterValues(map);
        assertEquals(2, map.size());
        assertEquals("secret", map.get("password"));
        assertEquals("somechat", map.get("serviceName"));

        map = URISupport.parseQuery("password=RAW(++?w0rd)&serviceName=somechat");
        URISupport.resolveRawParameterValues(map);
        assertEquals(2, map.size());
        assertEquals("++?w0rd", map.get("password"));
        assertEquals("somechat", map.get("serviceName"));

        map = URISupport.parseQuery("password=RAW(++?)w&rd)&serviceName=somechat");
        URISupport.resolveRawParameterValues(map);
        assertEquals(2, map.size());
        assertEquals("++?)w&rd", map.get("password"));
        assertEquals("somechat", map.get("serviceName"));
    }

    @Test
    public void testResolveRawParameterValuesCurly() throws Exception {
        Map<String, Object> map = URISupport.parseQuery("password=RAW{++?w0rd}&serviceName=somechat");
        URISupport.resolveRawParameterValues(map);
        assertEquals(2, map.size());
        assertEquals("++?w0rd", map.get("password"));
        assertEquals("somechat", map.get("serviceName"));

        map = URISupport.parseQuery("password=RAW{++?)w&rd}&serviceName=somechat");
        URISupport.resolveRawParameterValues(map);
        assertEquals(2, map.size());
        assertEquals("++?)w&rd", map.get("password"));
        assertEquals("somechat", map.get("serviceName"));
    }

    @Test
    public void testAppendParameterToUriAndReplaceExistingOne() throws Exception {
        Map<String, Object> newParameters = new HashMap<>();
        newParameters.put("foo", "456");
        newParameters.put("bar", "yes");
        String newUri = URISupport.appendParametersToURI("stub:foo?foo=123", newParameters);

        assertEquals("stub://foo?foo=456&bar=yes", newUri);
    }

    @Test
    public void testPathAndQueryOf() {
        assertEquals("/", URISupport.pathAndQueryOf(URI.create("http://localhost")));
        assertEquals("/", URISupport.pathAndQueryOf(URI.create("http://localhost:80")));
        assertEquals("/", URISupport.pathAndQueryOf(URI.create("http://localhost:80/")));
        assertEquals("/path", URISupport.pathAndQueryOf(URI.create("http://localhost:80/path")));
        assertEquals("/path/", URISupport.pathAndQueryOf(URI.create("http://localhost:80/path/")));
        assertEquals("/path?query=value", URISupport.pathAndQueryOf(URI.create("http://localhost:80/path?query=value")));
    }

    @Test
    public void shouldStripPrefixes() {
        assertThat(URISupport.stripPrefix(null, null)).isNull();
        assertThat(URISupport.stripPrefix("", null)).isEmpty();
        assertThat(URISupport.stripPrefix(null, "")).isNull();
        assertThat(URISupport.stripPrefix("", "")).isEmpty();
        assertThat(URISupport.stripPrefix("a", "b")).isEqualTo("a");
        assertThat(URISupport.stripPrefix("a", "a")).isEmpty();
        assertThat(URISupport.stripPrefix("ab", "b")).isEqualTo("ab");
        assertThat(URISupport.stripPrefix("a", "ab")).isEqualTo("a");
    }

    @Test
    public void shouldStripSuffixes() {
        assertThat(URISupport.stripSuffix(null, null)).isNull();
        assertThat(URISupport.stripSuffix("", null)).isEmpty();
        assertThat(URISupport.stripSuffix(null, "")).isNull();
        assertThat(URISupport.stripSuffix("", "")).isEmpty();
        assertThat(URISupport.stripSuffix("a", "b")).isEqualTo("a");
        assertThat(URISupport.stripSuffix("a", "a")).isEmpty();
        assertThat(URISupport.stripSuffix("ab", "b")).isEqualTo("a");
        assertThat(URISupport.stripSuffix("a", "ab")).isEqualTo("a");
    }

    @Test
    public void shouldJoinPaths() {
        assertThat(URISupport.joinPaths(null, null)).isEmpty();
        assertThat(URISupport.joinPaths("", null)).isEmpty();
        assertThat(URISupport.joinPaths(null, "")).isEmpty();
        assertThat(URISupport.joinPaths("", "")).isEmpty();
        assertThat(URISupport.joinPaths("a", "")).isEqualTo("a");
        assertThat(URISupport.joinPaths("a", "b")).isEqualTo("a/b");
        assertThat(URISupport.joinPaths("/a", "b")).isEqualTo("/a/b");
        assertThat(URISupport.joinPaths("/a", "b/")).isEqualTo("/a/b/");
        assertThat(URISupport.joinPaths("/a/", "b/")).isEqualTo("/a/b/");
        assertThat(URISupport.joinPaths("/a/", "/b/")).isEqualTo("/a/b/");
        assertThat(URISupport.joinPaths("a", "b", "c")).isEqualTo("a/b/c");
        assertThat(URISupport.joinPaths("a", null, "c")).isEqualTo("a/c");
        assertThat(URISupport.joinPaths("/a/", "/b", "c/", "/d/")).isEqualTo("/a/b/c/d/");
        assertThat(URISupport.joinPaths("/a/", "/b", "c/", null)).isEqualTo("/a/b/c/");
        assertThat(URISupport.joinPaths("/a/", null, null, null)).isEqualTo("/a/");
        assertThat(URISupport.joinPaths("a/", "/b", null, null)).isEqualTo("a/b");
    }

    @Test
    public void testExtractQuery() {
        assertNull(URISupport.extractQuery(null));
        assertNull(URISupport.extractQuery(""));
        assertNull(URISupport.extractQuery("file:foo"));
        assertEquals("recursive=true", URISupport.extractQuery("file:foo?recursive=true"));
        assertEquals("recursive=true&delete=true", URISupport.extractQuery("file:foo?recursive=true&delete=true"));
    }

    @Test
    public void testPlusInQuery() {
        Map<String, Object> map = new HashMap<>();
        map.put("param1", "+447777111222");
        String q = URISupport.createQueryString(map);
        assertEquals("param1=%2B447777111222", q);

        // will be double encoded however
        map.put("param1", "%2B447777111222");
        q = URISupport.createQueryString(map);
        assertEquals("param1=%252B447777111222", q);
    }

    @Test
    public void testBuildMultiValueQuery() {
        List<Object> list = new ArrayList<>();
        assertEquals("", URISupport.buildMultiValueQuery("id", list));
        list = List.of("hello");
        assertEquals("id=hello", URISupport.buildMultiValueQuery("id", list));
        list = List.of(1, 2, 3);
        assertEquals("id=1&id=2&id=3", URISupport.buildMultiValueQuery("id", list));
        list = List.of("foo", "bar", 3, true, "baz");
        assertEquals("hey=foo&hey=bar&hey=3&hey=true&hey=baz", URISupport.buildMultiValueQuery("hey", list));
    }

    @Test
    public void testGetDecodeQuery() throws Exception {
        String out = URISupport.normalizeUri("smtp://localhost?username=davsclaus&password=secret");
        String enc = UnsafeUriCharactersEncoder.encode(out);
        String dec = URISupport.getDecodeQuery(enc);
        assertEquals(out, dec);

        out = URISupport.normalizeUri("smtp://localhost?password=secret&username=davsclaus");
        assertEquals(out, dec);

        out = URISupport.normalizeUri("http://localhost?username=davsclaus&password=RAW(#@a)");
        enc = UnsafeUriCharactersEncoder.encode(out);
        assertNotEquals(out, enc);

        dec = URISupport.getDecodeQuery(enc);
        assertEquals(out, dec);

        out = URISupport.normalizeUri("bean://MyBean?method=RAW(addString(%22#@a%23, test))");
        enc = UnsafeUriCharactersEncoder.encode(out);
        assertNotEquals(out, enc);

        dec = URISupport.getDecodeQuery(enc);
        assertEquals(out, dec);
    }

    @Test
    public void testSanitizeCustomKeys() {
        String uri1 = "http://foo?username=me&password=secret&domain=foobar&database=customers";
        String expected = "http://foo?username=xxxxxx&password=xxxxxx&domain=foobar&database=customers";
        assertEquals(expected, URISupport.sanitizeUri(uri1));

        URISupport.addSanitizeKeywords("domain,system,cheese");
        assertNotEquals(expected, URISupport.sanitizeUri(uri1));
        expected = "http://foo?username=xxxxxx&password=xxxxxx&domain=xxxxxx&database=customers";
        assertEquals(expected, URISupport.sanitizeUri(uri1));
    }

}
