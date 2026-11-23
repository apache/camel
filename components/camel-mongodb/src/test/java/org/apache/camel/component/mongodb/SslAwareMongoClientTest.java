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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.ReadPreference;
import com.mongodb.ServerAddress;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.PropertyBindingSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The settings of the client the MongoDB Kamelets create as a bean: the read preference, and the credentials with
 * characters a connection string must percent-encode.
 */
class SslAwareMongoClientTest {

    private static final List<ServerAddress> HOSTS
            = List.of(new ServerAddress("host1", 27017), new ServerAddress("host2", 27017));

    private static SslAwareMongoClient client(String username, String password) {
        SslAwareMongoClient client = new SslAwareMongoClient();
        client.setHosts("host1:27017,host2:27017");
        client.setUsername(username);
        client.setPassword(password);
        client.setSsl(false);
        return client;
    }

    @Test
    void readPreferenceNotSetIsPrimary() {
        MongoClientSettings settings = client(null, null).createSettings();

        assertEquals(ReadPreference.primary(), settings.getReadPreference());
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "  " })
    void blankReadPreferenceIsPrimary(String value) {
        SslAwareMongoClient client = client(null, null);
        client.setReadPreference(value);

        assertEquals(ReadPreference.primary(), client.createSettings().getReadPreference());
    }

    @ParameterizedTest
    @CsvSource({
            "PRIMARY, primary",
            "PRIMARY_PREFERRED, primaryPreferred",
            "SECONDARY, secondary",
            "SECONDARY_PREFERRED, secondaryPreferred",
            "NEAREST, nearest",
            "secondaryPreferred, secondaryPreferred",
            "nearest, nearest" })
    void readPreferenceIsApplied(String value, String driverName) {
        SslAwareMongoClient client = client(null, null);
        client.setReadPreference(value);

        assertEquals(ReadPreference.valueOf(driverName), client.createSettings().getReadPreference());
    }

    @Test
    void readPreferenceOfTheClient() {
        SslAwareMongoClient client = client(null, null);
        client.setReadPreference("SECONDARY_PREFERRED");
        try {
            // creating the client does not connect to the hosts
            assertEquals(ReadPreference.secondaryPreferred(), client.getReadPreference());
        } finally {
            client.close();
        }
    }

    @Test
    void propertiesAreBoundAsInTheKamelets() throws Exception {
        // the Kamelets configure the client as a bean with properties
        SslAwareMongoClient client = new SslAwareMongoClient();
        Map<String, Object> properties = new HashMap<>();
        properties.put("hosts", "host1:27017");
        properties.put("username", "scott");
        properties.put("password", "t@ger:/?%");
        properties.put("ssl", "false");
        properties.put("readPreference", "SECONDARY_PREFERRED");
        try (CamelContext context = new DefaultCamelContext()) {
            PropertyBindingSupport.build().withReflection(true).withMandatory(true).bind(context, client, properties);
        }

        MongoClientSettings settings = client.createSettings();
        assertEquals(ReadPreference.secondaryPreferred(), settings.getReadPreference());
        assertArrayEquals("t@ger:/?%".toCharArray(), settings.getCredential().getPassword());
        assertFalse(settings.getSslSettings().isEnabled());
    }

    @Test
    void unknownReadPreferenceFails() {
        SslAwareMongoClient client = client(null, null);
        client.setReadPreference("SOMEWHERE");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, client::createSettings);
        assertTrue(e.getMessage().contains("SOMEWHERE"), e.getMessage());
    }

    @Test
    void noUsernameHasNoCredential() {
        MongoClientSettings settings = client(null, null).createSettings();

        assertNull(settings.getCredential());
        assertEquals(HOSTS, settings.getClusterSettings().getHosts());
    }

    @Test
    void credentialIsAuthenticatedAgainstTheAdminDatabase() {
        MongoCredential credential = client("scott", "tiger").createSettings().getCredential();

        assertEquals("scott", credential.getUserName());
        assertArrayEquals("tiger".toCharArray(), credential.getPassword());
        assertEquals("admin", credential.getSource());
        assertNull(credential.getAuthenticationMechanism());
    }

    @ParameterizedTest
    @ValueSource(strings = { "p@ss", "p:ss", "p/ss", "p?ss", "p%ss", "p%41ss", "p+s s#&=", "@:/?%", "pässwörd" })
    void passwordIsGivenAsIs(String password) {
        MongoClientSettings settings = client("scott", password).createSettings();

        assertArrayEquals(password.toCharArray(), settings.getCredential().getPassword());
        assertEquals(HOSTS, settings.getClusterSettings().getHosts());
    }

    @Test
    void usernameIsGivenAsIs() {
        MongoCredential credential = client("us@er:/?%41", "tiger").createSettings().getCredential();

        assertEquals("us@er:/?%41", credential.getUserName());
        assertArrayEquals("tiger".toCharArray(), credential.getPassword());
    }
}
