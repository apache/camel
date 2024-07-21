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

package org.apache.camel.dsl.jbang.core.commands.kubernetes;

import java.util.Arrays;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.support.Capability;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.support.SourceMetadata;
import org.apache.camel.dsl.jbang.core.common.RuntimeType;
import org.apache.camel.dsl.jbang.core.common.Source;
import org.apache.camel.dsl.jbang.core.common.SourceHelper;
import org.apache.camel.dsl.jbang.core.common.VersionHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class MetadataHelperTest {

    @BeforeAll
    static void setup() {
        // Set Camel version with system property value, usually set via Maven surefire plugin
        // In case you run this test via local Java IDE you need to provide the system property or a default value here
        VersionHelper.setCamelVersion(System.getProperty("camel.version", ""));
    }

    @Test
    public void testInspectHttpService() throws Exception {
        CamelCatalog catalog = CatalogHelper.loadCatalog(RuntimeType.quarkus, RuntimeType.quarkus.version());
        Source source = SourceHelper.resolveSource("classpath:PlatformHttpServer.java");
        SourceMetadata metadata = MetadataHelper.readFromSource(catalog, source);

        Assertions.assertTrue(MetadataHelper.exposesHttpServices(catalog, metadata));
        Assertions.assertTrue(metadata.capabilities.contains(Capability.PlatformHttp));
        Assertions.assertEquals(1, metadata.resources.components.size());
        Assertions.assertTrue(metadata.resources.components.contains("platform-http"));
        Assertions.assertEquals(1, metadata.endpoints.from.size());
        Assertions.assertTrue(metadata.endpoints.from.contains("platform-http:///hello?httpMethodRestrict=GET"));
        Assertions.assertEquals(0, metadata.endpoints.to.size());

        source = SourceHelper.resolveSource("classpath:route.yaml");
        metadata = MetadataHelper.readFromSource(catalog, source);

        Assertions.assertFalse(MetadataHelper.exposesHttpServices(catalog, metadata));
        Assertions.assertEquals(3, metadata.resources.components.size());
        Assertions.assertTrue(metadata.resources.components.containsAll(Arrays.asList("timer", "log")));
        Assertions.assertEquals(1, metadata.endpoints.from.size());
        Assertions.assertTrue(metadata.endpoints.from.contains("timer://tick"));
        Assertions.assertEquals(1, metadata.endpoints.to.size());
        Assertions.assertTrue(metadata.endpoints.to.contains("log://info"));
    }
}
