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
package org.apache.camel.support.jsse;

import java.util.List;
import java.util.Properties;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JsseEdgeCasesTest {

    private static CamelContext createContext(String key, String value) {
        CamelContext context = new DefaultCamelContext();
        Properties prop = new Properties();
        prop.setProperty(key, value);
        context.getPropertiesComponent().setInitialProperties(prop);
        return context;
    }

    @Test
    public void testFilterWithOnlyExcludes() throws Exception {
        FilterParameters filter = new FilterParameters();
        filter.getExclude().add(".*_CBC_.*");
        SSLContextParameters scp = new SSLContextParameters();
        scp.setCipherSuitesFilter(filter);

        SSLEngine engine = scp.createSSLContext(null).createSSLEngine();
        String[] suites = engine.getEnabledCipherSuites();
        assertTrue(suites.length > 0);
        for (String suite : suites) {
            assertFalse(suite.contains("_CBC_"), suite);
        }
    }

    @Test
    public void testFilterPatternWithPlaceholder() throws Exception {
        FilterParameters filter = new FilterParameters();
        filter.getInclude().add("{{inc}}");
        SSLContextParameters scp = new SSLContextParameters();
        scp.setCipherSuitesFilter(filter);

        SSLEngine engine = scp.createSSLContext(createContext("inc", "TLS_AES_.*")).createSSLEngine();
        String[] suites = engine.getEnabledCipherSuites();
        assertTrue(suites.length > 0);
        for (String suite : suites) {
            assertTrue(suite.startsWith("TLS_AES_"), suite);
        }
    }

    @Test
    public void testSniOnSSLEngine() throws Exception {
        SSLContextClientParameters client = new SSLContextClientParameters();
        client.setSniHostName("{{sni}}");
        SSLContextParameters scp = new SSLContextParameters();
        scp.setClientParameters(client);

        SSLContext context = scp.createSSLContext(createContext("sni", "example.com"));
        SSLEngine engine = context.createSSLEngine();
        engine.setUseClientMode(true);
        List<SNIServerName> names = engine.getSSLParameters().getServerNames();
        assertEquals(List.of(new SNIHostName("example.com")), names);

        engine = context.createSSLEngine("localhost", 8443);
        assertEquals(List.of(new SNIHostName("example.com")), engine.getSSLParameters().getServerNames());
    }

    @Test
    public void testClientAuthenticationIgnoreCase() throws Exception {
        SSLContextServerParameters server = new SSLContextServerParameters();
        server.setClientAuthentication("want");
        SSLContextParameters scp = new SSLContextParameters();
        scp.setServerParameters(server);

        SSLEngine engine = scp.createSSLContext(null).createSSLEngine();
        engine.setUseClientMode(false);
        assertTrue(engine.getWantClientAuth());
    }
}
