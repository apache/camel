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

import java.util.Set;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.support.SourceMetadata;
import org.apache.camel.dsl.jbang.core.common.CatalogLoader;
import org.apache.camel.dsl.jbang.core.common.Source;
import org.apache.camel.dsl.jbang.core.common.SourceHelper;
import org.apache.camel.dsl.jbang.core.common.VersionHelper;
import org.apache.camel.tooling.maven.MavenDownloaderImpl;
import org.apache.camel.tooling.maven.MavenGav;
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
        MavenGav camelQuarkusBom = MavenGav.fromCoordinates("io.quarkus.platform", "quarkus-camel-bom", "3.5.2", "pom", null);
        try (MavenDownloaderImpl d = new MavenDownloaderImpl()) {
            d.build();
            CamelCatalog catalog = CatalogLoader.loadQuarkusCatalog(camelQuarkusBom, d::resolveArtifact);

            // a Java route is read with the Java DSL parser: the uris as the route has them
            Source source = SourceHelper.resolveSource("classpath:PlatformHttpServer.java");
            SourceMetadata metadata = MetadataHelper.readFromSource(catalog, source);

            Assertions.assertTrue(MetadataHelper.exposesHttpServices(catalog, metadata));
            Assertions.assertEquals(1, metadata.endpoints.from.size());
            Assertions.assertTrue(metadata.endpoints.from.contains("platform-http:/hello?httpMethodRestrict=GET"));
            Assertions.assertEquals(0, metadata.endpoints.to.size());

            // a YAML route is loaded in a stubbed CamelContext: the uris as the endpoints normalize them
            source = SourceHelper.resolveSource("classpath:route.yaml");
            metadata = MetadataHelper.readFromSource(catalog, source);

            Assertions.assertFalse(MetadataHelper.exposesHttpServices(catalog, metadata));
            Assertions.assertEquals(1, metadata.endpoints.from.size());
            Assertions.assertTrue(metadata.endpoints.from.contains("timer://tick"));
            Assertions.assertEquals(1, metadata.endpoints.to.size());
            Assertions.assertTrue(metadata.endpoints.to.contains("log://info"));
        }
    }

    @Test
    public void testJavaRouteIsNotCompiled() throws Exception {
        CamelCatalog catalog = new DefaultCamelCatalog();
        // NoSuchProcessor does not exist: the source does not compile, and is read all the same
        SourceMetadata metadata = MetadataHelper.readFromSource(catalog, "Orders.java", """
                import org.apache.camel.builder.RouteBuilder;

                public class Orders extends RouteBuilder {
                    @Override
                    public void configure() {
                        from("knative:channel/orders")
                            .process(new NoSuchProcessor())
                            .wireTap("knative:event/order-received")
                            .toD("knative:endpoint/${header.target}")
                            .to("knative:endpoint/billing");
                    }
                }
                """);

        Assertions.assertTrue(MetadataHelper.exposesHttpServices(catalog, metadata));
        Assertions.assertEquals(Set.of("knative:channel/orders"), metadata.endpoints.from);
        // the toD is only known at runtime
        Assertions.assertEquals(Set.of("knative:event/order-received", "knative:endpoint/billing"),
                metadata.endpoints.to);
    }

    @Test
    public void testXmlRoute() throws Exception {
        CamelCatalog catalog = new DefaultCamelCatalog();
        SourceMetadata metadata = MetadataHelper.readFromSource(catalog, "routes.xml", """
                <routes xmlns="http://camel.apache.org/schema/xml-io">
                    <route>
                        <from uri="platform-http:/orders"/>
                        <to uri="kafka:orders"/>
                    </route>
                </routes>
                """);

        Assertions.assertTrue(MetadataHelper.exposesHttpServices(catalog, metadata));
        Assertions.assertEquals(Set.of("platform-http:/orders"), metadata.endpoints.from);
        Assertions.assertEquals(Set.of("kafka:orders"), metadata.endpoints.to);

        // a Java or XML file that is not a route source has no endpoints
        metadata = MetadataHelper.readFromSource(catalog, "Util.java", "public class Util {}");
        Assertions.assertTrue(metadata.endpoints.from.isEmpty());
        Assertions.assertTrue(metadata.endpoints.to.isEmpty());
    }
}
