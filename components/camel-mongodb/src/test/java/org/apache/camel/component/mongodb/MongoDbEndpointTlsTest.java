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
package org.apache.camel.component.mongodb;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.internal.MongoClientImpl;
import com.mongodb.connection.SslSettings;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The TLS options of a mongodb endpoint are applied to the client it creates when hosts is set, whether the connection
 * string comes from hosts or from connectionUriString.
 */
class MongoDbEndpointTlsTest extends CamelTestSupport {

    private static final String HOSTS = "mongodb:dummy?hosts=localhost:27017&username=user&password=pass"
                                        + "&database=test&collection=test&operation=findAll";
    private static final String CONNECTION_URI_STRING
            = "mongodb:dummy?hosts=localhost:27017&connectionUriString=RAW(mongodb://user:pass@localhost:27017/)"
              + "&database=test&collection=test&operation=findAll";

    private SslSettings sslSettings(String uri) {
        MongoDbEndpoint endpoint = context.getEndpoint(uri, MongoDbEndpoint.class);
        return endpoint.createMongoClientSettings().getSslSettings();
    }

    @Test
    void hostsWithoutTlsIsPlain() {
        assertFalse(sslSettings(HOSTS).isEnabled());
    }

    @Test
    void hostsWithTlsUsesTls() {
        SslSettings ssl = sslSettings(HOSTS + "&tls=true");
        assertTrue(ssl.isEnabled());
        assertFalse(ssl.isInvalidHostNameAllowed());
    }

    @Test
    void hostsWithTlsAllowInvalidHostnames() {
        SslSettings ssl = sslSettings(HOSTS + "&tls=true&tlsAllowInvalidHostnames=true");
        assertTrue(ssl.isEnabled());
        assertTrue(ssl.isInvalidHostNameAllowed());
    }

    @Test
    void hostsWithSslContextParametersUsesTls() {
        context.getRegistry().bind("ssl", new SSLContextParameters());

        SslSettings ssl = sslSettings(HOSTS + "&sslContextParameters=#ssl");
        assertTrue(ssl.isEnabled());
        assertNotNull(ssl.getContext());
    }

    @Test
    void connectionUriStringWithoutTlsIsPlain() {
        assertFalse(sslSettings(CONNECTION_URI_STRING).isEnabled());
    }

    @Test
    void connectionUriStringWithTlsUsesTls() {
        SslSettings ssl = sslSettings(CONNECTION_URI_STRING + "&tls=true");
        assertTrue(ssl.isEnabled());
        assertNull(ssl.getContext());
    }

    @Test
    void tlsInsideConnectionUriStringIsKept() {
        SslSettings ssl = sslSettings(
                "mongodb:dummy?hosts=localhost:27017"
                                      + "&connectionUriString=RAW(mongodb://localhost:27017/?tls=true&tlsAllowInvalidHostnames=true)"
                                      + "&database=test&collection=test&operation=findAll");
        assertTrue(ssl.isEnabled());
        assertTrue(ssl.isInvalidHostNameAllowed());
    }

    @Test
    void connectionUriStringWithSslContextParametersUsesTls() {
        context.getRegistry().bind("ssl", new SSLContextParameters());

        SslSettings ssl = sslSettings(CONNECTION_URI_STRING + "&sslContextParameters=#ssl");
        assertTrue(ssl.isEnabled());
        assertNotNull(ssl.getContext());
    }

    @Test
    void clientCreatedFromHostsUsesTls() {
        MongoDbEndpoint endpoint = context.getEndpoint(HOSTS + "&tls=true", MongoDbEndpoint.class);

        MongoClientSettings settings = assertInstanceOf(MongoClientImpl.class, endpoint.getMongoConnection()).getSettings();
        assertTrue(settings.getSslSettings().isEnabled());
    }
}
