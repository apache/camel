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
package org.apache.camel.component.jetty;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;
import java.util.Properties;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.main.Main;
import org.apache.camel.support.jsse.ClientAuthentication;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.parallel.ResourceLock;

import static org.apache.camel.component.jetty.BaseJettyTest.SSL_SYSPROPS;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ResourceLock(SSL_SYSPROPS)
@DisabledOnOs(value = OS.WINDOWS, disabledReason = "does not run well on Windows")
public class MainHttpsRouteTest extends BaseJettyTest {

    public static final String NULL_VALUE_MARKER = CamelTestSupport.class.getCanonicalName();
    protected final Properties originalValues = new Properties();

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        URL trustStoreUrl = this.getClass().getClassLoader().getResource("jsse/localhost.p12");
        setSystemProp("javax.net.ssl.trustStore", trustStoreUrl.toURI().getPath());
        setSystemProp("javax.net.ssl.trustStorePassword", "changeit");
        setSystemProp("javax.net.ssl.trustStoreType", "PKCS12");
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        restoreSystemProperties();
        super.tearDown();
    }

    @Test
    public void testHelloEndpoint() throws Exception {
        Main main = new Main();
        main.configure().sslConfig().setEnabled(true);
        main.configure().sslConfig().setKeyStore(
                this.getClass().getClassLoader().getResource("jsse/localhost.p12").toString());
        main.configure().sslConfig().setKeystorePassword("changeit");
        main.configure().sslConfig().setClientAuthentication(ClientAuthentication.WANT.name());
        main.addProperty("camel.component.jetty.useglobalsslcontextparameters", "true");

        main.configure().addRoutesBuilder(new RouteBuilder() {

            public void configure() {
                Processor proc = exchange -> exchange.getMessage().setBody("<b>Hello World</b>");
                from("jetty:https://localhost:" + port1 + "/hello").process(proc);
            }
        });

        main.start();
        try {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            URL url = new URL("https://localhost:" + port1 + "/hello");
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            SSLContext ssl = SSLContext.getInstance("TLSv1.3");
            ssl.init(null, null, null);
            connection.setSSLSocketFactory(ssl.getSocketFactory());
            InputStream is = connection.getInputStream();
            int c;
            while ((c = is.read()) >= 0) {
                os.write(c);
            }

            String data = new String(os.toByteArray());
            assertEquals("<b>Hello World</b>", data);
        } finally {
            main.stop();
        }
    }

    protected void setSystemProp(String key, String value) {
        String originalValue = System.setProperty(key, value);
        originalValues.put(key, originalValue != null ? originalValue : NULL_VALUE_MARKER);
    }

    protected void restoreSystemProperties() {
        for (Map.Entry<Object, Object> entry : originalValues.entrySet()) {
            Object key = entry.getKey();
            Object value = entry.getValue();
            if (NULL_VALUE_MARKER.equals(value)) {
                System.clearProperty((String) key);
            } else {
                System.setProperty((String) key, (String) value);
            }
        }
    }
}
