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
package org.apache.camel.component.beanstalk;

import com.surftools.BeanstalkClient.Client;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ConnectionSettingsTest {

    @Test
    void parseUriTest() {
        final ConnectionSettingsFactory factory = BeanstalkComponent.getConnectionSettingsFactory();
        assertEquals(new ConnectionSettings("host.domain.tld", 11300, "someTube"), factory.parseUri("host.domain.tld:11300/someTube"), "Full URI");
        assertEquals(new ConnectionSettings("host.domain.tld", Client.DEFAULT_PORT, "someTube"), factory.parseUri("host.domain.tld/someTube"), "No port");
        assertEquals(new ConnectionSettings(Client.DEFAULT_HOST, Client.DEFAULT_PORT, "someTube"), factory.parseUri("someTube"), "Only tube");
    }

    @Test
    void parseTubesTest() {
        final ConnectionSettingsFactory factory = BeanstalkComponent.getConnectionSettingsFactory();
        assertArrayEquals(new String[]{"tube1", "tube2"}, factory.parseUri("host:90/tube1+tube2").tubes, "Full URI");
        assertArrayEquals(new String[]{"tube1", "tube2"}, factory.parseUri("host/tube1+tube2").tubes, "No port");
        assertArrayEquals(new String[]{"tube1", "tube2"}, factory.parseUri("tube1+tube2").tubes, "Only tubes");
        assertArrayEquals(new String[0], factory.parseUri("").tubes, "Empty URI");
    }

    @Test
    void notValidHost() {
        assertThrows(IllegalArgumentException.class, () -> {
            final ConnectionSettingsFactory factory = BeanstalkComponent.getConnectionSettingsFactory();
            factory.parseUri("not_valid?host/tube?");
        }, "Calling on not valid URI must raise exception");
    }

    @BeforeEach
    public void setUp() {
        BeanstalkComponent.setConnectionSettingsFactory(new ConnectionSettingsFactory());
    }
}
