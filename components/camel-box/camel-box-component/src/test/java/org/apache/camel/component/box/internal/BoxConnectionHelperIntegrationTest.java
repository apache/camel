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
package org.apache.camel.component.box.internal;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import com.box.sdk.BoxAPIConnection;
import com.box.sdk.BoxUser;
import org.apache.camel.component.box.BoxConfiguration;
import org.apache.camel.support.IntrospectionSupport;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

/**
 * Test class for {@link BoxConnectionHelper}.
 */
public class BoxConnectionHelperIntegrationTest {

    private static final String TEST_OPTIONS_PROPERTIES = "/test-options.properties";

    private BoxConfiguration configuration = new BoxConfiguration();

    @Before
    public void loadConfiguration() throws Exception {
        Properties properties = new Properties();
        try {
            properties.load(getClass().getResourceAsStream(TEST_OPTIONS_PROPERTIES));
        } catch (Exception e) {
            throw new IOException(String.format("%s could not be loaded: %s", TEST_OPTIONS_PROPERTIES, e.getMessage()),
                    e);
        }

        Map<String, Object> options = properties.entrySet().stream().collect(
                Collectors.<Map.Entry, String, Object>toMap(e -> (String) e.getKey(), Map.Entry::getValue));

        IntrospectionSupport.setProperties(configuration, options);
    }

    @Test
    public void testCreateConnection() {
        BoxAPIConnection connection = BoxConnectionHelper.createConnection(configuration);
        BoxUser user = BoxUser.getCurrentUser(connection);
        assertNotNull(user);
    }
}
