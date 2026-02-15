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

import java.util.Properties;

import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.test.junit6.TestSupport;
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
        return TestSupport.loadExternalPropertiesQuietly(DigitalOceanTestSupport.class, "/test-options.properties");
    }

    @Override
    protected Properties useOverridePropertiesWithPropertiesComponent() {
        return properties;
    }

}
