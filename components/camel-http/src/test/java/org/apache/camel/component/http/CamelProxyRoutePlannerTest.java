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

package org.apache.camel.component.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashSet;
import java.util.Set;

import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.Test;

public class CamelProxyRoutePlannerTest {

    private static final HttpHost PROXY = new HttpHost("http", "proxy.example.com", 8080);

    @Test
    public void testExactHostnameMatch() throws HttpException {
        Set<String> noProxyHosts = new HashSet<>();
        noProxyHosts.add("localhost");
        noProxyHosts.add("internal.example.com");

        CamelProxyRoutePlanner planner = new CamelProxyRoutePlanner(PROXY, noProxyHosts);

        HttpHost localhostTarget = new HttpHost("http", "localhost", 8080);
        HttpHost internalTarget = new HttpHost("http", "internal.example.com", 80);
        HttpHost externalTarget = new HttpHost("http", "external.example.com", 80);

        assertNull(planner.determineProxy(localhostTarget, null), "localhost should bypass proxy");
        assertNull(planner.determineProxy(internalTarget, null), "internal.example.com should bypass proxy");
        assertNotNull(planner.determineProxy(externalTarget, null), "external.example.com should use proxy");
        assertEquals(PROXY, planner.determineProxy(externalTarget, null));
    }

    @Test
    public void testExactHostnameMatchCaseInsensitive() throws HttpException {
        Set<String> noProxyHosts = new HashSet<>();
        noProxyHosts.add("LocalHost");
        noProxyHosts.add("INTERNAL.EXAMPLE.COM");

        CamelProxyRoutePlanner planner = new CamelProxyRoutePlanner(PROXY, noProxyHosts);

        HttpHost lowercaseTarget = new HttpHost("http", "localhost", 8080);
        HttpHost uppercaseTarget = new HttpHost("http", "LOCALHOST", 8080);
        HttpHost mixedCaseTarget = new HttpHost("http", "internal.Example.Com", 80);

        assertNull(planner.determineProxy(lowercaseTarget, null), "localhost (lowercase) should bypass proxy");
        assertNull(planner.determineProxy(uppercaseTarget, null), "LOCALHOST (uppercase) should bypass proxy");
        assertNull(
                planner.determineProxy(mixedCaseTarget, null), "internal.Example.Com (mixed case) should bypass proxy");
    }

    @Test
    public void testWildcardPatternMatchPrefix() throws HttpException {
        Set<String> noProxyHosts = new HashSet<>();
        noProxyHosts.add("*.internal.com");

        CamelProxyRoutePlanner planner = new CamelProxyRoutePlanner(PROXY, noProxyHosts);

        HttpHost matchingTarget1 = new HttpHost("http", "server1.internal.com", 80);
        HttpHost matchingTarget2 = new HttpHost("http", "server2.internal.com", 80);
        HttpHost nonMatchingTarget = new HttpHost("http", "server1.external.com", 80);

        assertNull(planner.determineProxy(matchingTarget1, null), "server1.internal.com should bypass proxy");
        assertNull(planner.determineProxy(matchingTarget2, null), "server2.internal.com should bypass proxy");
        assertNotNull(planner.determineProxy(nonMatchingTarget, null), "server1.external.com should use proxy");
    }

    @Test
    public void testWildcardPatternMatchSuffix() throws HttpException {
        Set<String> noProxyHosts = new HashSet<>();
        noProxyHosts.add("192.168.*");

        CamelProxyRoutePlanner planner = new CamelProxyRoutePlanner(PROXY, noProxyHosts);

        HttpHost matchingTarget1 = new HttpHost("http", "192.168.1.1", 80);
        HttpHost matchingTarget2 = new HttpHost("http", "192.168.100.50", 80);
        HttpHost nonMatchingTarget = new HttpHost("http", "10.0.0.1", 80);

        assertNull(planner.determineProxy(matchingTarget1, null), "192.168.1.1 should bypass proxy");
        assertNull(planner.determineProxy(matchingTarget2, null), "192.168.100.50 should bypass proxy");
        assertNotNull(planner.determineProxy(nonMatchingTarget, null), "10.0.0.1 should use proxy");
    }

    @Test
    public void testWildcardPatternMatchMiddle() throws HttpException {
        Set<String> noProxyHosts = new HashSet<>();
        noProxyHosts.add("*.dev.*.com");

        CamelProxyRoutePlanner planner = new CamelProxyRoutePlanner(PROXY, noProxyHosts);

        HttpHost matchingTarget = new HttpHost("http", "server.dev.internal.com", 80);
        HttpHost nonMatchingTarget = new HttpHost("http", "server.prod.internal.com", 80);

        assertNull(planner.determineProxy(matchingTarget, null), "server.dev.internal.com should bypass proxy");
        assertNotNull(planner.determineProxy(nonMatchingTarget, null), "server.prod.internal.com should use proxy");
    }

    @Test
    public void testWildcardPatternCaseInsensitive() throws HttpException {
        Set<String> noProxyHosts = new HashSet<>();
        noProxyHosts.add("*.INTERNAL.COM");

        CamelProxyRoutePlanner planner = new CamelProxyRoutePlanner(PROXY, noProxyHosts);

        HttpHost lowercaseTarget = new HttpHost("http", "server.internal.com", 80);
        HttpHost uppercaseTarget = new HttpHost("http", "SERVER.INTERNAL.COM", 80);
        HttpHost mixedCaseTarget = new HttpHost("http", "Server.Internal.Com", 80);

        assertNull(planner.determineProxy(lowercaseTarget, null), "server.internal.com should bypass proxy");
        assertNull(planner.determineProxy(uppercaseTarget, null), "SERVER.INTERNAL.COM should bypass proxy");
        assertNull(planner.determineProxy(mixedCaseTarget, null), "Server.Internal.Com should bypass proxy");
    }

    @Test
    public void testMultiplePatterns() throws HttpException {
        Set<String> noProxyHosts = new HashSet<>();
        noProxyHosts.add("localhost");
        noProxyHosts.add("127.0.0.1");
        noProxyHosts.add("*.local");
        noProxyHosts.add("*.internal.com");

        CamelProxyRoutePlanner planner = new CamelProxyRoutePlanner(PROXY, noProxyHosts);

        HttpHost target1 = new HttpHost("http", "localhost", 80);
        HttpHost target2 = new HttpHost("http", "127.0.0.1", 80);
        HttpHost target3 = new HttpHost("http", "myserver.local", 80);
        HttpHost target4 = new HttpHost("http", "api.internal.com", 80);
        HttpHost target5 = new HttpHost("http", "external.com", 80);

        assertNull(planner.determineProxy(target1, null), "localhost should bypass proxy");
        assertNull(planner.determineProxy(target2, null), "127.0.0.1 should bypass proxy");
        assertNull(planner.determineProxy(target3, null), "myserver.local should bypass proxy");
        assertNull(planner.determineProxy(target4, null), "api.internal.com should bypass proxy");
        assertNotNull(planner.determineProxy(target5, null), "external.com should use proxy");
    }

    @Test
    public void testNullNoProxyHosts() throws HttpException {
        CamelProxyRoutePlanner planner = new CamelProxyRoutePlanner(PROXY, null);

        HttpHost target = new HttpHost("http", "localhost", 80);

        assertNotNull(planner.determineProxy(target, null), "With null noProxyHosts, all hosts should use proxy");
        assertEquals(PROXY, planner.determineProxy(target, null));
    }

    @Test
    public void testEmptyNoProxyHosts() throws HttpException {
        Set<String> noProxyHosts = new HashSet<>();

        CamelProxyRoutePlanner planner = new CamelProxyRoutePlanner(PROXY, noProxyHosts);

        HttpHost target = new HttpHost("http", "localhost", 80);

        assertNotNull(planner.determineProxy(target, null), "With empty noProxyHosts, all hosts should use proxy");
        assertEquals(PROXY, planner.determineProxy(target, null));
    }

    @Test
    public void testNoProxyHostsWithNullAndEmptyStrings() throws HttpException {
        Set<String> noProxyHosts = new HashSet<>();
        noProxyHosts.add(null);
        noProxyHosts.add("");
        noProxyHosts.add("localhost");

        CamelProxyRoutePlanner planner = new CamelProxyRoutePlanner(PROXY, noProxyHosts);

        HttpHost localhostTarget = new HttpHost("http", "localhost", 80);
        HttpHost externalTarget = new HttpHost("http", "external.com", 80);

        assertNull(planner.determineProxy(localhostTarget, null), "localhost should bypass proxy");
        assertNotNull(planner.determineProxy(externalTarget, null), "external.com should use proxy");
    }

    @Test
    public void testWildcardOnlyPattern() throws HttpException {
        Set<String> noProxyHosts = new HashSet<>();
        noProxyHosts.add("*");

        CamelProxyRoutePlanner planner = new CamelProxyRoutePlanner(PROXY, noProxyHosts);

        HttpHost target1 = new HttpHost("http", "any.host.com", 80);
        HttpHost target2 = new HttpHost("http", "localhost", 80);

        assertNull(planner.determineProxy(target1, null), "* pattern should match any.host.com");
        assertNull(planner.determineProxy(target2, null), "* pattern should match localhost");
    }

    @Test
    public void testSpecialCharactersInHostname() throws HttpException {
        Set<String> noProxyHosts = new HashSet<>();
        noProxyHosts.add("server-1.example.com");

        CamelProxyRoutePlanner planner = new CamelProxyRoutePlanner(PROXY, noProxyHosts);

        HttpHost target = new HttpHost("http", "server-1.example.com", 80);

        assertNull(planner.determineProxy(target, null), "server-1.example.com should bypass proxy");
    }

    @Test
    public void testWildcardDoesNotMatchPartially() throws HttpException {
        Set<String> noProxyHosts = new HashSet<>();
        noProxyHosts.add("*.internal.com");

        CamelProxyRoutePlanner planner = new CamelProxyRoutePlanner(PROXY, noProxyHosts);

        HttpHost target = new HttpHost("http", "internal.com", 80);

        assertNotNull(planner.determineProxy(target, null), "internal.com should NOT match *.internal.com");
    }

    @Test
    public void testConstructorWithSchemePortResolver() throws HttpException {
        Set<String> noProxyHosts = new HashSet<>();
        noProxyHosts.add("localhost");

        CamelProxyRoutePlanner planner = new CamelProxyRoutePlanner(PROXY, null, noProxyHosts);

        HttpHost localhostTarget = new HttpHost("http", "localhost", 80);
        HttpHost externalTarget = new HttpHost("http", "external.com", 80);

        assertNull(planner.determineProxy(localhostTarget, null), "localhost should bypass proxy");
        assertNotNull(planner.determineProxy(externalTarget, null), "external.com should use proxy");
    }
}
