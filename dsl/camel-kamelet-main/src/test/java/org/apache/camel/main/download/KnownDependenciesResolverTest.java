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
package org.apache.camel.main.download;

import java.util.List;
import java.util.Map;

import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.impl.engine.SimpleCamelContext;
import org.apache.camel.main.util.VersionHelper;
import org.apache.camel.tooling.maven.MavenGav;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class KnownDependenciesResolverTest {

    @Test
    void factoryFinderDependenciesUseRegisteredServices() {
        KnownDependenciesResolver resolver = new KnownDependenciesResolver(new SimpleCamelContext(), null, null);
        resolver.loadKnownFactoryFinderDependencies();

        assertGav(resolver, "META-INF/services/org/apache/camel/bean-processor-factory", "org.apache.camel", "camel-bean");
    }

    @Test
    void camelShorthandMappingsUseCatalogCoordinates() {
        SimpleCamelContext context = new SimpleCamelContext() {
            @Override
            public String getVersion() {
                return "0.0.1";
            }
        };
        KnownDependenciesResolver resolver = new KnownDependenciesResolver(context, null, null);
        resolver.addMappings(Map.of(
                "org.example.Cataloged", "camel:whatsapp",
                "org.example.Uncataloged", "camel:uncataloged"));

        assertEquals(new DefaultCamelCatalog().componentModel("whatsapp").getVersion(),
                resolver.mavenGavForClass("org.example.Cataloged").getVersion());
        assertEquals("0.0.1", resolver.mavenGavForClass("org.example.Uncataloged").getVersion());
    }

    @Test
    void mavenGavForClass_returnsClassScopedDependency() {
        KnownDependenciesResolver resolver = new KnownDependenciesResolver(new SimpleCamelContext(), null, null);
        resolver.loadKnownDependencies();

        MavenGav dependency = resolver.mavenGavForClass(SomeClass.class.getName());

        assertNotNull(dependency);
        assertEquals(dependency.getGroupId(), "com.example");
        assertEquals(dependency.getArtifactId(), "class-scoped");
        assertEquals(dependency.getVersion(), "1.0.0");
    }

    @Test
    void mavenGavForClass_returnsPackageScopedDependency() {
        KnownDependenciesResolver resolver = new KnownDependenciesResolver(new SimpleCamelContext(), null, null);
        resolver.loadKnownDependencies();

        MavenGav dependency = resolver.mavenGavForClass(SomeClass.class.getPackage().getName());

        assertNotNull(dependency);
        assertEquals(dependency.getGroupId(), "org.example");
        assertEquals(dependency.getArtifactId(), "package-scoped");
        assertEquals(dependency.getVersion(), "2.0.0");
    }

    public static class SomeClass {
    }

    @Test
    void theShippedMappingResolvesThirdPartyClassesByPackage() {
        // CAMEL-24809: one line per library, matched by walking up the package; the Artemis package sits under the
        // classic ActiveMQ one and must win for its own classes
        KnownDependenciesResolver resolver = new KnownDependenciesResolver(new SimpleCamelContext(), null, null);
        resolver.loadKnownDependencies();

        assertGav(resolver, "org.postgresql.ds.PGSimpleDataSource", "org.postgresql", "postgresql");
        assertGav(resolver, "org.postgresql.ds.PGConnectionPoolDataSource", "org.postgresql", "postgresql");
        assertGav(resolver, "org.h2.jdbcx.JdbcDataSource", "com.h2database", "h2");
        assertGav(resolver, "com.zaxxer.hikari.HikariConfig", "com.zaxxer", "HikariCP");
        assertGav(resolver, "org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory", "org.apache.artemis",
                "artemis-jakarta-client-all");
        assertGav(resolver, "org.eclipse.yasson.internal.Unmarshaller", "org.eclipse.yasson", "yasson");
        assertGav(resolver, "com.univocity.parsers.csv.CsvParser", "com.sonofab1rd", "univocity-parsers");
        assertGav(resolver, "org.apache.activemq.ActiveMQConnectionFactory", "org.apache.activemq", "activemq-client");
        assertGav(resolver, "org.apache.qpid.jms.JmsConnectionFactory", "org.apache.qpid", "qpid-jms-client");
        assertGav(resolver, "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.core", "jackson-databind");
        assertGav(resolver, "com.fasterxml.jackson.dataformat.xml.XmlMapper", "com.fasterxml.jackson.dataformat",
                "jackson-dataformat-xml");
        assertGav(resolver, "tools.jackson.databind.ObjectMapper", "tools.jackson.core", "jackson-databind");
        assertGav(resolver, "tools.jackson.dataformat.xml.XmlMapper", "tools.jackson.dataformat", "jackson-dataformat-xml");
        assertGav(resolver, "org.apache.commons.csv.CSVFormat", "org.apache.commons", "commons-csv");
        assertGav(resolver, "software.amazon.awssdk.services.sqs.SqsClient", "software.amazon.awssdk", "sqs");
        assertGav(resolver, "org.infinispan.client.hotrod.RemoteCacheManager", "org.infinispan", "infinispan-client-hotrod");
        assertGav(resolver, "org.infinispan.manager.DefaultCacheManager", "org.infinispan", "infinispan-core");
        assertGav(resolver, "freemarker.template.Configuration", "org.freemarker", "freemarker");
        // the jolt-community fork that camel-jolt uses, not the old bazaarvoice library
        assertGav(resolver, "io.joltcommunity.jolt.Chainr", "io.github.jolt-community.jolt", "jolt-community-core");
        // a shared parent package is deliberately not mapped
        assertEquals(null, resolver.mavenGavForClass("org.apache.commons.Anything"));
    }

    @Test
    void theShippedMappingResolvesSpringTransactionClasses() {
        // the beans of a transacted route: the Spring JDBC transaction managers and datasources come with camel-sql
        // (spring-jdbc), the Spring transaction policy and spring-tx come with camel-spring
        KnownDependenciesResolver resolver = new KnownDependenciesResolver(new SimpleCamelContext(), null, null);
        resolver.loadKnownDependencies();

        assertGav(resolver, "org.springframework.jdbc.datasource.DataSourceTransactionManager", "org.apache.camel",
                "camel-sql");
        assertGav(resolver, "org.springframework.jdbc.support.JdbcTransactionManager", "org.apache.camel", "camel-sql");
        assertGav(resolver, "org.springframework.jdbc.datasource.DriverManagerDataSource", "org.apache.camel", "camel-sql");
        assertGav(resolver, "org.springframework.transaction.PlatformTransactionManager", "org.apache.camel",
                "camel-spring");
        assertGav(resolver, "org.springframework.transaction.support.TransactionTemplate", "org.apache.camel",
                "camel-spring");
        assertGav(resolver, "org.apache.camel.spring.spi.SpringTransactionPolicy", "org.apache.camel", "camel-spring");
    }

    @Test
    void anImportResolvesAnyClassOfAComponent() {
        // CAMEL-25239: any class of a component a source imports, not only the component class itself, such as the
        // constants of the headers of a component that a kamelet uses
        KnownDependenciesResolver resolver = new KnownDependenciesResolver(new SimpleCamelContext(), null, null);
        resolver.loadKnownDependencies();

        assertImport(resolver, "org.apache.camel.component.aws2.s3.AWS2S3Component", "camel-aws2-s3");
        assertImport(resolver, "org.apache.camel.component.aws2.s3.AWS2S3Constants", "camel-aws2-s3");
        assertImport(resolver, "org.apache.camel.component.aws2.s3.utils.AWS2S3Utils", "camel-aws2-s3");
        // a sibling package of another component is not mistaken for it
        assertImport(resolver, "org.apache.camel.component.aws2.s3vectors.AWS2S3VectorsConstants", "camel-aws2-s3-vectors");
        // a sub package of another component's package wins for its own classes
        assertImport(resolver, "org.apache.camel.component.file.remote.SftpConstants", "camel-ftp");
        assertImport(resolver, "org.apache.camel.component.file.GenericFile", "camel-file");
        // the libraries still resolve as before
        assertGav(resolver, "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.core", "jackson-databind");
        // a component in a base package does not claim every class in it
        assertNull(resolver.mavenGavForImport("org.apache.camel.Exchange"));
        assertNull(resolver.mavenGavForImport("org.apache.camel.component.Anything"));
        // a class looked up at runtime (often only probed for) still needs the component class itself
        assertNull(resolver.mavenGavForClass("org.apache.camel.component.aws2.s3.AWS2S3Constants"));
        assertGav(resolver, "org.apache.camel.component.aws2.s3.AWS2S3Component", "org.apache.camel", "camel-aws2-s3");
    }

    @Test
    void semanticExpertBeansAndImportsResolveTheirOwnArtifacts() {
        KnownDependenciesResolver resolver = new KnownDependenciesResolver(new SimpleCamelContext(), null, null);
        resolver.loadKnownDependencies();

        assertGav(resolver, "org.apache.camel.component.typesafeai.TypeSafeAiSemanticAdapter", "org.apache.camel",
                "camel-semantic-typesafe-ai");
        assertImport(resolver, "org.apache.camel.component.typesafeai.TypeSafeAiSemanticAdapter", "camel-semantic-typesafe-ai");
        assertGav(resolver, "org.apache.camel.component.wolfdefender.WolfDefenderSemanticAdapter", "org.apache.camel",
                "camel-semantic-wolf-defender");
        assertImport(resolver, "org.apache.camel.component.wolfdefender.WolfDefenderSemanticAdapter",
                "camel-semantic-wolf-defender");
        assertImport(resolver, "org.apache.camel.component.typesafeai.TypeSafeAiEndpoint", "camel-typesafe-ai");
    }

    @Test
    void theShippedMappingResolvesTheKafkaSchemaRegistrySerdes() {
        // the serializers and deserializers that the schemaRegistryVendor and schemaFormat options of camel-kafka set,
        // with the versions of parent/pom.xml (passed by surefire)
        KnownDependenciesResolver resolver = new KnownDependenciesResolver(new SimpleCamelContext(), null, null);
        resolver.loadKnownDependencies();

        String confluent = System.getProperty("confluent-serializer-version");
        String apicurio = System.getProperty("apicurio-registry-sdk-version");
        String glue = System.getProperty("aws-glue-schema-registry-version");
        String azure = System.getProperty("azure-schemaregistry-kafka-avro-version");
        assertNotNull(confluent, "confluent-serializer-version");

        for (String c : new String[] { "KafkaAvroSerializer", "KafkaAvroDeserializer" }) {
            assertGav(resolver, "io.confluent.kafka.serializers." + c, "io.confluent:kafka-avro-serializer:" + confluent);
        }
        for (String c : new String[] { "KafkaJsonSchemaSerializer", "KafkaJsonSchemaDeserializer" }) {
            assertGav(resolver, "io.confluent.kafka.serializers.json." + c,
                    "io.confluent:kafka-json-schema-serializer:" + confluent);
        }
        for (String c : new String[] { "KafkaProtobufSerializer", "KafkaProtobufDeserializer" }) {
            assertGav(resolver, "io.confluent.kafka.serializers.protobuf." + c,
                    "io.confluent:kafka-protobuf-serializer:" + confluent);
        }
        for (String c : new String[] { "AvroKafkaSerializer", "AvroKafkaDeserializer" }) {
            assertGav(resolver, "io.apicurio.registry.serde.avro." + c,
                    "io.apicurio:apicurio-registry-avro-serde-kafka:" + apicurio);
        }
        for (String c : new String[] { "JsonSchemaKafkaSerializer", "JsonSchemaKafkaDeserializer" }) {
            assertGav(resolver, "io.apicurio.registry.serde.jsonschema." + c,
                    "io.apicurio:apicurio-registry-jsonschema-serde-kafka:" + apicurio);
        }
        for (String c : new String[] { "ProtobufKafkaSerializer", "ProtobufKafkaDeserializer" }) {
            assertGav(resolver, "io.apicurio.registry.serde.protobuf." + c,
                    "io.apicurio:apicurio-registry-protobuf-serde-kafka:" + apicurio);
        }
        assertGav(resolver, "com.amazonaws.services.schemaregistry.serializers.GlueSchemaRegistryKafkaSerializer",
                "software.amazon.glue:schema-registry-serde:" + glue);
        assertGav(resolver, "com.amazonaws.services.schemaregistry.deserializers.GlueSchemaRegistryKafkaDeserializer",
                "software.amazon.glue:schema-registry-serde:" + glue);

        // the Azure serde comes with a recent azure-core, as its dependencies ask for an old one
        for (String c : new String[] { "KafkaAvroSerializer", "KafkaAvroDeserializer" }) {
            List<MavenGav> gavs = resolver.mavenGavsForClass("com.microsoft.azure.schemaregistry.kafka.avro." + c);
            assertEquals(2, gavs.size(), c);
            assertEquals("com.microsoft.azure:azure-schemaregistry-kafka-avro:" + azure, gavs.get(0).toString());
            assertEquals("com.azure", gavs.get(1).getGroupId());
            assertEquals("azure-core", gavs.get(1).getArtifactId());
            // TokenRequestContext.isCaeEnabled() that azure-identity calls is in azure-core 1.54.1 and later
            assertTrue(VersionHelper.isGE(gavs.get(1).getVersion(), "1.54.1"), gavs.get(1).getVersion());
            // a single dependency for the callers that take one
            assertEquals(gavs.get(0).toString(),
                    resolver.mavenGavForClass("com.microsoft.azure.schemaregistry.kafka.avro." + c).toString());
        }
        // the other classes of the vendors are not mapped
        assertNull(resolver.mavenGavForClass("io.confluent.kafka.serializers.Other"));
        assertNull(resolver.mavenGavForClass("io.apicurio.registry.serde.Other"));
    }

    @Test
    void aMappingCanListMoreDependencies() {
        KnownDependenciesResolver resolver = new KnownDependenciesResolver(new SimpleCamelContext(), null, null);
        resolver.addMappings(Map.of("org.example.Both", "org.example:first:1.0, org.example:second:2.0"));

        List<MavenGav> gavs = resolver.mavenGavsForClass("org.example.Both");
        assertEquals(List.of("org.example:first:1.0", "org.example:second:2.0"),
                gavs.stream().map(MavenGav::toString).toList());
        assertEquals("org.example:first:1.0", resolver.mavenGavForClass("org.example.Both").toString());
        assertEquals(gavs.toString(), resolver.mavenGavsForImport("org.example.Both").toString());
        assertTrue(resolver.mavenGavsForClass("org.example.Unknown").isEmpty());
    }

    private static void assertGav(KnownDependenciesResolver resolver, String className, String gav) {
        List<MavenGav> gavs = resolver.mavenGavsForClass(className);
        assertEquals(List.of(gav), gavs.stream().map(MavenGav::toString).toList(), className);
    }

    private static void assertImport(KnownDependenciesResolver resolver, String className, String artifactId) {
        MavenGav gav = resolver.mavenGavForImport(className);
        assertNotNull(gav, className);
        assertEquals("org.apache.camel", gav.getGroupId(), className);
        assertEquals(artifactId, gav.getArtifactId(), className);
        assertNotNull(gav.getVersion(), className + " version is null");
    }

    private static void assertGav(KnownDependenciesResolver resolver, String className, String groupId, String artifactId) {
        MavenGav gav = resolver.mavenGavForClass(className);
        assertNotNull(gav, className);
        assertEquals(groupId, gav.getGroupId(), className);
        assertEquals(artifactId, gav.getArtifactId(), className);
        String version = gav.getVersion();
        assertNotNull(version, className + " version is null");
        assertFalse(version.startsWith("${"), className + " version is an unresolved placeholder: " + version);
    }
}
