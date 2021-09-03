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
package org.apache.camel.component.digitalocean.integration;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;

import org.apache.camel.test.junit5.CamelTestSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DigitalOceanTestSupport extends CamelTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(DigitalOceanTestSupport.class);

    protected final Properties properties;

    protected DigitalOceanTestSupport() {
        properties = loadProperties();
    }

    // This is used by JUnit to automatically determine whether or not to run the integration tests
    @SuppressWarnings("unused")
    private static boolean hasCredentials() {
        Properties properties = loadProperties();

        return !properties.getProperty("oAuthToken", "").isEmpty();
    }

    private static Properties loadProperties() {
        URL url = DigitalOceanTestSupport.class.getResource("/test-options.properties");

        InputStream inStream;
        try {
            inStream = url.openStream();
        } catch (IOException e) {
            LOG.error("I/O error opening the stream: {}", e.getMessage(), e);
            throw new IllegalAccessError("test-options.properties could not be found");
        }

        Properties properties = new Properties();
        try {
            properties.load(inStream);

            return properties;
        } catch (IOException e) {
            LOG.error("I/O error reading the stream: {}", e.getMessage(), e);
            throw new IllegalAccessError("test-options.properties could not be found");
        }
    }

    @Override
    protected Properties useOverridePropertiesWithPropertiesComponent() {
        return properties;
    }

}
