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
package org.apache.camel.component.pg.replication.slot;

import java.util.Properties;

import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.ObjectHelper;
import org.junit.jupiter.api.Test;
import org.postgresql.PGProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The TLS options become properties of the JDBC connection; no database is needed.
 */
public class PgReplicationSlotTlsTest extends CamelTestSupport {

    private static final String BASE = "pg-replication-slot:localhost:5432/mydb/myslot:test_decoding";

    @Test
    public void tlsOptions() {
        PgReplicationSlotEndpoint endpoint = context.getEndpoint(BASE
                                                                 + "?sslMode=verify-full&sslRootCert=/certs/root.crt"
                                                                 + "&sslCert=/certs/client.crt&sslKey=/certs/client.pk8"
                                                                 + "&sslPassword=secret",
                PgReplicationSlotEndpoint.class);

        Properties props = endpoint.createConnectionProperties();

        assertEquals("verify-full", PGProperty.SSL_MODE.getOrDefault(props));
        assertEquals("/certs/root.crt", PGProperty.SSL_ROOT_CERT.getOrDefault(props));
        assertEquals("/certs/client.crt", PGProperty.SSL_CERT.getOrDefault(props));
        assertEquals("/certs/client.pk8", PGProperty.SSL_KEY.getOrDefault(props));
        assertEquals("secret", PGProperty.SSL_PASSWORD.getOrDefault(props));
        // the replication settings are still there
        assertEquals("database", PGProperty.REPLICATION.getOrDefault(props));
    }

    @Test
    public void noTlsOptionsKeepTheDriverDefaults() {
        PgReplicationSlotEndpoint endpoint = context.getEndpoint(BASE, PgReplicationSlotEndpoint.class);

        Properties props = endpoint.createConnectionProperties();

        for (PGProperty property : new PGProperty[] {
                PGProperty.SSL_MODE, PGProperty.SSL_ROOT_CERT, PGProperty.SSL_CERT, PGProperty.SSL_KEY,
                PGProperty.SSL_PASSWORD }) {
            assertFalse(props.containsKey(property.getName()), property.getName() + " must not be set");
        }
        assertEquals("postgres", PGProperty.USER.getOrDefault(props));
    }

    @Test
    public void invalidSslMode() {
        Exception e = assertThrows(Exception.class, () -> context.getEndpoint(BASE + "?sslMode=verify"));
        IllegalArgumentException cause = ObjectHelper.getException(IllegalArgumentException.class, e);
        assertNotNull(cause, "IllegalArgumentException expected as cause of " + e);
        assertTrue(cause.getMessage().contains("Invalid sslMode: verify"), cause.getMessage());
    }
}
