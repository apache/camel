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
package org.apache.camel.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.ContextTestSupport;

/**
 * @version
 */
public class URISupportTest extends ContextTestSupport {

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

    public void testNormalizeEndpointUriWithFragments() throws Exception {
        String out1 = URISupport.normalizeUri("irc://someserver/#camel?user=davsclaus");
        String out2 = URISupport.normalizeUri("irc:someserver/#camel?user=davsclaus");
        assertEquals(out1, out2);

        out1 = URISupport.normalizeUri("irc://someserver/#camel?user=davsclaus");
        out2 = URISupport.normalizeUri("irc:someserver/#camel?user=hadrian");
        assertNotSame(out1, out2);
    }

    public void testNormalizeHttpEndpoint() throws Exception {
        String out1 = URISupport.normalizeUri("http://www.google.com?q=Camel");
        String out2 = URISupport.normalizeUri("http:www.google.com?q=Camel");
        assertEquals(out1, out2);
        assertTrue("Should have //", out1.startsWith("http://"));
        assertTrue("Should have //", out2.startsWith("http://"));

    }

    public void testNormalizeIPv6HttpEndpoint() throws Exception {
        String result = URISupport.normalizeUri("http://[2a00:8a00:6000:40::1413]:30300/test");
        assertEquals("http://[2a00:8a00:6000:40::1413]:30300/test", result);
    }

    public void testNormalizeHttpEndpointUnicodedParameter() throws Exception {
        String out = URISupport.normalizeUri("http://www.google.com?q=S\u00F8ren");
        assertEquals("http://www.google.com?q=S%C3%B8ren", out);
    }

    public void testParseParametersUnicodedValue() throws Exception {
        String out = URISupport.normalizeUri("http://www.google.com?q=S\u00F8ren");
        URI uri = new URI(out);

        Map<String, Object> parameters = URISupport.parseParameters(uri);

        assertEquals(1, parameters.size());
        assertEquals("S\u00F8ren", parameters.get("q"));
    }

    public void testNormalizeHttpEndpointURLEncodedParameter() throws Exception {
        String out = URISupport.normalizeUri("http://www.google.com?q=S%C3%B8ren%20Hansen");
        assertEquals("http://www.google.com?q=S%C3%B8ren+Hansen", out);
    }

    public void testParseParametersURLEncodeddValue() throws Exception {
        String out = URISupport.normalizeUri("http://www.google.com?q=S%C3%B8ren+Hansen");
        URI uri = new URI(out);

        Map<String, Object> parameters = URISupport.parseParameters(uri);

        assertEquals(1, parameters.size());
        assertEquals("S\u00F8ren Hansen", parameters.get("q"));
    }

    public void testNormalizeUriWhereParamererIsFaulty() throws Exception {
        String out = URISupport.normalizeUri("stream:uri?file:///d:/temp/data/log/quickfix.log&scanStream=true");
        assertNotNull(out);
    }

    public void testCreateRemaingURI() throws Exception {
        URI original = new URI("http://camel.apache.org");
        Map<String, Object> param = new HashMap<String, Object>();
        param.put("foo", "123");
        URI newUri = URISupport.createRemainingURI(original, param);
        assertNotNull(newUri);

        String s = newUri.toString();
        assertEquals("http://camel.apache.org?foo=123", s);
    }

    public void testCreateURIWithQueryHasOneFragment() throws Exception {
        URI uri = new URI("smtp://localhost#fragmentOne");
        URI resultUri = URISupport.createURIWithQuery(uri, null);
        assertNotNull(resultUri);
        assertEquals("smtp://localhost#fragmentOne", resultUri.toString());
    }

    public void testCreateURIWithQueryHasOneFragmentAndQueryParameter() throws Exception {
        URI uri = new URI("smtp://localhost#fragmentOne");
        URI resultUri = URISupport.createURIWithQuery(uri, "utm_campaign=launch");
        assertNotNull(resultUri);
        assertEquals("smtp://localhost?utm_campaign=launch#fragmentOne", resultUri.toString());
    }

    public void testNormalizeEndpointWithEqualSignInParameter() throws Exception {
        String out = URISupport.normalizeUri("jms:queue:foo?selector=somekey='somevalue'&foo=bar");
        assertNotNull(out);
        // Camel will safe encode the URI
        assertEquals("jms://queue:foo?foo=bar&selector=somekey%3D%27somevalue%27", out);
    }

    public void testNormalizeEndpointWithPercentSignInParameter() throws Exception {
        String out = URISupport.normalizeUri("http://someendpoint?username=james&password=%25test");
        assertNotNull(out);
        // Camel will safe encode the URI
        assertEquals("http://someendpoint?password=%25test&username=james", out);
    }

    public void testParseParameters() throws Exception {
        URI u = new URI("quartz:myGroup/myTimerName?cron=0+0+*+*+*+?");
        Map<String, Object> params = URISupport.parseParameters(u);
        assertEquals(1, params.size());
        assertEquals("0 0 * * * ?", params.get("cron"));

        u = new URI("quartz:myGroup/myTimerName?cron=0+0+*+*+*+?&bar=123");
        params = URISupport.parseParameters(u);
        assertEquals(2, params.size());
        assertEquals("0 0 * * * ?", params.get("cron"));
        assertEquals("123", params.get("bar"));
    }

    public void testCreateRemainingURIEncoding() throws Exception {
        // the uri is already encoded, but we create a new one with new query parameters
        String uri = "http://localhost:23271/myapp/mytest?columns=name%2Ctotalsens%2Cupsens&username=apiuser";

        // these are the parameters which is tricky to encode
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("foo", "abc def");
        map.put("bar", "123,456");
        map.put("name", "S\u00F8ren"); // danish letter

        // create new uri with the parameters
        URI out = URISupport.createRemainingURI(new URI(uri), map);
        assertNotNull(out);
        assertEquals("http://localhost:23271/myapp/mytest?foo=abc+def&bar=123%2C456&name=S%C3%B8ren", out.toString());
        assertEquals("http://localhost:23271/myapp/mytest?foo=abc+def&bar=123%2C456&name=S%C3%B8ren", out.toASCIIString());
    }

    public void testNormalizeEndpointUriWithDualParameters() throws Exception {
        String out1 = URISupport.normalizeUri("smtp://localhost?to=foo&to=bar&from=me");
        assertEquals("smtp://localhost?from=me&to=foo&to=bar", out1);

        String out2 = URISupport.normalizeUri("smtp://localhost?to=foo&to=bar&from=me&from=you");
        assertEquals("smtp://localhost?from=me&from=you&to=foo&to=bar", out2);
    }

    public void testSanitizeUriWithUserInfo() {
        String uri = "jt400://GEORGE:HARRISON@LIVERPOOL/QSYS.LIB/BEATLES.LIB/PENNYLANE.DTAQ";
        String expected = "jt400://GEORGE:xxxxxx@LIVERPOOL/QSYS.LIB/BEATLES.LIB/PENNYLANE.DTAQ";
        assertEquals(expected, URISupport.sanitizeUri(uri));
    }

    public void testSanitizePathWithUserInfo() {
        String path = "GEORGE:HARRISON@LIVERPOOL/QSYS.LIB/BEATLES.LIB/PENNYLANE.PGM";
        String expected = "GEORGE:xxxxxx@LIVERPOOL/QSYS.LIB/BEATLES.LIB/PENNYLANE.PGM";
        assertEquals(expected, URISupport.sanitizePath(path));
    }

    public void testSanitizePathWithoutSensitiveInfoIsUnchanged() {
        String path = "myhost:8080/mypath";
        assertEquals(path, URISupport.sanitizePath(path));
    }

    public void testSanitizeUriWithRawPassword() {
        String uri = "http://foo?username=me&password=RAW(me#@123)&foo=bar";
        String expected = "http://foo?username=me&password=xxxxxx&foo=bar";
        assertEquals(expected, URISupport.sanitizeUri(uri));
    }

    public void testSanitizeUriRawUnsafePassword() {
        String uri = "sftp://localhost/target?password=RAW(beforeAmp&afterAmp)&username=jrandom";
        String expected = "sftp://localhost/target?password=xxxxxx&username=jrandom";
        assertEquals(expected, URISupport.sanitizeUri(uri));
    }

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

    public void testSpecialUriFromXmppComponent() throws Exception {
        String out1 = URISupport.normalizeUri("xmpp://camel-user@localhost:123/test-user@localhost?password=secret&serviceName=someCoolChat");
        assertEquals("xmpp://camel-user@localhost:123/test-user@localhost?password=secret&serviceName=someCoolChat", out1);
    }

    public void testRawParameter() throws Exception {
        String out = URISupport.normalizeUri("xmpp://camel-user@localhost:123/test-user@localhost?password=RAW(++?w0rd)&serviceName=some chat");
        assertEquals("xmpp://camel-user@localhost:123/test-user@localhost?password=RAW(++?w0rd)&serviceName=some+chat", out);

        String out2 = URISupport.normalizeUri("xmpp://camel-user@localhost:123/test-user@localhost?password=RAW(foo %% bar)&serviceName=some chat");
        // Just make sure the RAW parameter can be resolved rightly, we need to replace the % into %25
        assertEquals("xmpp://camel-user@localhost:123/test-user@localhost?password=RAW(foo %25%25 bar)&serviceName=some+chat", out2);
    }

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

    public void testParseQueryLenient() throws Exception {
        try {
            URISupport.parseQuery("password=secret&serviceName=somechat&", false, false);
            fail("Should have thrown exception");
        } catch (URISyntaxException e) {
            // expected
        }

        Map<String, Object> map = URISupport.parseQuery("password=secret&serviceName=somechat&", false, true);
        assertEquals(2, map.size());
        assertEquals("secret", map.get("password"));
        assertEquals("somechat", map.get("serviceName"));
    }

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

    public void testAppendParameterToUriAndReplaceExistingOne() throws Exception {
        Map<String, Object> newParameters = new HashMap<String, Object>();
        newParameters.put("foo", "456");
        newParameters.put("bar", "yes");
        String newUri = URISupport.appendParametersToURI("stub:foo?foo=123", newParameters);

        assertEquals("stub://foo?foo=456&bar=yes", newUri);
    }

    public void testPathAndQueryOf() {
        assertEquals("/", URISupport.pathAndQueryOf(URI.create("http://localhost")));
        assertEquals("/", URISupport.pathAndQueryOf(URI.create("http://localhost:80")));
        assertEquals("/", URISupport.pathAndQueryOf(URI.create("http://localhost:80/")));
        assertEquals("/path", URISupport.pathAndQueryOf(URI.create("http://localhost:80/path")));
        assertEquals("/path/", URISupport.pathAndQueryOf(URI.create("http://localhost:80/path/")));
        assertEquals("/path?query=value", URISupport.pathAndQueryOf(URI.create("http://localhost:80/path?query=value")));
    }
}