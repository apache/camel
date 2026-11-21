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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The settings of the client the MongoDB Kamelets create as a bean: the read preference, and the credentials with
 * characters a connection string must percent-encode.
 */
class SslAwareMongoClientTest {

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

        assertThat(settings.getReadPreference()).isEqualTo(ReadPreference.primary());
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "  " })
    void blankReadPreferenceIsPrimary(String value) {
        SslAwareMongoClient client = client(null, null);
        client.setReadPreference(value);

        assertThat(client.createSettings().getReadPreference()).isEqualTo(ReadPreference.primary());
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

        assertThat(client.createSettings().getReadPreference()).isEqualTo(ReadPreference.valueOf(driverName));
    }

    @Test
    void readPreferenceOfTheClient() {
        SslAwareMongoClient client = client(null, null);
        client.setReadPreference("SECONDARY_PREFERRED");
        try {
            // creating the client does not connect to the hosts
            assertThat(client.getReadPreference()).isEqualTo(ReadPreference.secondaryPreferred());
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
        assertThat(settings.getReadPreference()).isEqualTo(ReadPreference.secondaryPreferred());
        assertThat(settings.getCredential().getPassword()).isEqualTo("t@ger:/?%".toCharArray());
        assertThat(settings.getSslSettings().isEnabled()).isFalse();
    }

    @Test
    void unknownReadPreferenceFails() {
        SslAwareMongoClient client = client(null, null);
        client.setReadPreference("SOMEWHERE");

        assertThatThrownBy(client::createSettings).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SOMEWHERE");
    }

    @Test
    void noUsernameHasNoCredential() {
        MongoClientSettings settings = client(null, null).createSettings();

        assertThat(settings.getCredential()).isNull();
        assertThat(settings.getClusterSettings().getHosts())
                .containsExactly(new ServerAddress("host1", 27017), new ServerAddress("host2", 27017));
    }

    @Test
    void credentialIsAuthenticatedAgainstTheAdminDatabase() {
        MongoCredential credential = client("scott", "tiger").createSettings().getCredential();

        assertThat(credential.getUserName()).isEqualTo("scott");
        assertThat(credential.getPassword()).isEqualTo("tiger".toCharArray());
        assertThat(credential.getSource()).isEqualTo("admin");
        assertThat(credential.getAuthenticationMechanism()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = { "p@ss", "p:ss", "p/ss", "p?ss", "p%ss", "p%41ss", "p+s s#&=", "@:/?%", "pässwörd" })
    void passwordIsGivenAsIs(String password) {
        MongoClientSettings settings = client("scott", password).createSettings();

        assertThat(settings.getCredential().getPassword()).isEqualTo(password.toCharArray());
        assertThat(settings.getClusterSettings().getHosts())
                .containsExactly(new ServerAddress("host1", 27017), new ServerAddress("host2", 27017));
    }

    @Test
    void usernameIsGivenAsIs() {
        MongoCredential credential = client("us@er:/?%41", "tiger").createSettings().getCredential();

        assertThat(credential.getUserName()).isEqualTo("us@er:/?%41");
        assertThat(credential.getPassword()).isEqualTo("tiger".toCharArray());
    }
}
