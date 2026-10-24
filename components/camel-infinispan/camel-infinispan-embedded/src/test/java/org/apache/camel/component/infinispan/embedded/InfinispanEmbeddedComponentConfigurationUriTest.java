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
package org.apache.camel.component.infinispan.embedded;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The configurationUri of the component is read with the resource loader of Camel.
 */
public class InfinispanEmbeddedComponentConfigurationUriTest {

    private static final String CONFIG
            = "org/apache/camel/component/infinispan/spring/SpringInfinispanEmbeddedIdempotentRepositoryConfiguration.xml";

    @TempDir
    Path tempDir;

    private Path configFile;

    @BeforeEach
    void copyConfig() throws Exception {
        configFile = tempDir.resolve("infinispan.xml");
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(CONFIG)) {
            Files.copy(is, configFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Test
    public void testClasspathName() throws Exception {
        assertConfigurationLoaded(CONFIG);
    }

    @Test
    public void testClasspathScheme() throws Exception {
        assertConfigurationLoaded("classpath:" + CONFIG);
    }

    @Test
    public void testFileScheme() throws Exception {
        assertConfigurationLoaded("file:" + configFile.toAbsolutePath());
    }

    @Test
    public void testAbsolutePath() throws Exception {
        assertConfigurationLoaded(configFile.toAbsolutePath().toString());
    }

    @Test
    public void testPlaceholder() throws Exception {
        assertConfigurationLoaded("{{infinispan.config}}");
    }

    private void assertConfigurationLoaded(String configurationUri) throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.getPropertiesComponent().addInitialProperty("infinispan.config", "file:" + configFile.toAbsolutePath());

            InfinispanEmbeddedComponent component = new InfinispanEmbeddedComponent();
            component.getConfiguration().setConfigurationUri(configurationUri);
            context.addComponent(InfinispanEmbeddedComponent.SCHEME, component);
            context.start();

            // the cache idempotent is only defined in the configuration file
            assertNotNull(component.getManager().getCacheContainer().getCacheConfiguration("idempotent"),
                    "The configuration " + configurationUri + " is not loaded");
        }
    }
}
