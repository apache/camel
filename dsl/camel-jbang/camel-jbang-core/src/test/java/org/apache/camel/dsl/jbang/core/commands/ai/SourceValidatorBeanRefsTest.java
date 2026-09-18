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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-24698: a reference to a bean nothing declares is reported before the run, with how to declare it.
 */
public class SourceValidatorBeanRefsTest {

    private static final CamelCatalog CATALOG = new DefaultCamelCatalog();

    private static final String ROUTE = """
            - route:
                from:
                  uri: timer:tick
                  steps:
                    - aggregate:
                        correlationExpression:
                          constant: "true"
                        completionSize: 3
                        aggregationStrategy: myAggregator
                        steps:
                          - log: "${body}"
            """;

    @Test
    void javaClassNextToTheRouteIsNotABeanUntilDeclared(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("MyAggregator.java"), """
                package com.example;
                public class MyAggregator {
                }
                """);
        List<String> msgs
                = SourceValidator.validateYamlBeanRefs(ROUTE, SourceValidator.BeanDeclarations.scan(dir, "r.camel.yaml"),
                        CATALOG);
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0))
                .startsWith("Line 9: aggregationStrategy: bean 'myAggregator' is not declared")
                .contains("MyAggregator.java is in the directory")
                .contains("type: \"#class:com.example.MyAggregator\"");
    }

    @Test
    void declaredInTheFileOrASiblingOrByAnnotationIsFine(@TempDir Path dir) throws IOException {
        String declared = """
                - beans:
                    - name: myAggregator
                      type: "#class:com.example.MyAggregator"
                """ + ROUTE;
        assertThat(SourceValidator.validateYamlBeanRefs(declared, SourceValidator.BeanDeclarations.NONE, CATALOG)).isEmpty();

        Files.writeString(dir.resolve("beans.camel.yaml"), """
                - beans:
                    - name: myAggregator
                      type: "#class:com.example.MyAggregator"
                """);
        assertThat(SourceValidator.validateYamlBeanRefs(ROUTE, SourceValidator.BeanDeclarations.scan(dir, "r.camel.yaml"),
                CATALOG))
                .isEmpty();

        Files.delete(dir.resolve("beans.camel.yaml"));
        Files.writeString(dir.resolve("MyAggregator.java"), """
                package com.example;
                import org.apache.camel.BindToRegistry;
                @BindToRegistry("myAggregator")
                public class MyAggregator {
                }
                """);
        assertThat(SourceValidator.validateYamlBeanRefs(ROUTE, SourceValidator.BeanDeclarations.scan(dir, "r.camel.yaml"),
                CATALOG))
                .isEmpty();
    }

    @Test
    void classReferencesPlaceholdersAndUnknownDirectoryAreLeftAlone(@TempDir Path dir) {
        String yaml = ROUTE.replace("aggregationStrategy: myAggregator",
                "aggregationStrategy: \"#class:com.example.MyAggregator\"");
        assertThat(SourceValidator.validateYamlBeanRefs(yaml, SourceValidator.BeanDeclarations.NONE, CATALOG)).isEmpty();
        yaml = ROUTE.replace("aggregationStrategy: myAggregator", "aggregationStrategy: \"{{strategy}}\"");
        assertThat(SourceValidator.validateYamlBeanRefs(yaml, SourceValidator.BeanDeclarations.NONE, CATALOG)).isEmpty();
        yaml = ROUTE.replace("aggregationStrategy: myAggregator", "aggregationStrategy: com.example.MyAggregator");
        assertThat(SourceValidator.validateYamlBeanRefs(yaml, SourceValidator.BeanDeclarations.NONE, CATALOG)).isEmpty();
        // no directory given: the check is not run at all by validate(...)
        assertThat(SourceValidator.validate("r.camel.yaml", ROUTE, CATALOG, null)).isEmpty();
    }

    @Test
    void theRequiredInterfaceComesFromTheEipModels(@TempDir Path dir) throws IOException {
        // the catalog's EIP model says what each option needs
        assertThat(BeanRefChecks.requiredType(CATALOG, "idempotentRepository"))
                .isEqualTo("org.apache.camel.spi.IdempotentRepository");
        assertThat(BeanRefChecks.requiredType(CATALOG, "aggregationStrategy"))
                .isEqualTo("org.apache.camel.AggregationStrategy");
        assertThat(BeanRefChecks.requiredType(CATALOG, "ref")).isNull();
        Files.writeString(dir.resolve("MyRepo.java"), """
                package com.example;
                public class MyRepo {
                }
                """);
        String yaml = """
                - beans:
                    - name: myRepo
                      type: "#class:com.example.MyRepo"
                - from:
                    uri: timer:tick
                    steps:
                      - idempotentConsumer:
                          simple: "${header.id}"
                          idempotentRepository: myRepo
                          steps:
                            - log: hi
                """;
        List<String> msgs = SourceValidator.validateYamlBeanRefs(yaml,
                SourceValidator.BeanDeclarations.scan(dir, "r.camel.yaml"), CATALOG);
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0)).contains("com.example.MyRepo must implement org.apache.camel.spi.IdempotentRepository")
                .contains("the built-in ones are");
    }

    @Test
    void declaredClassMustImplementWhatTheOptionNeeds(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("MyAggregator.java"), """
                package com.example;
                public class MyAggregator {
                }
                """);
        String declared = """
                - beans:
                    - name: myAggregator
                      type: "#class:com.example.MyAggregator"
                """ + ROUTE;
        List<String> msgs
                = SourceValidator.validateYamlBeanRefs(declared, SourceValidator.BeanDeclarations.scan(dir, "r.camel.yaml"),
                        CATALOG);
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0)).contains("com.example.MyAggregator must implement org.apache.camel.AggregationStrategy");

        String direct = ROUTE.replace("aggregationStrategy: myAggregator",
                "aggregationStrategy: \"#class:com.example.MyAggregator\"");
        assertThat(SourceValidator.validateYamlBeanRefs(direct, SourceValidator.BeanDeclarations.scan(dir, "r.camel.yaml"),
                CATALOG))
                .hasSize(1);

        Files.writeString(dir.resolve("MyAggregator.java"), """
                package com.example;
                import org.apache.camel.AggregationStrategy;
                public class MyAggregator implements AggregationStrategy {
                }
                """);
        assertThat(SourceValidator.validateYamlBeanRefs(declared, SourceValidator.BeanDeclarations.scan(dir, "r.camel.yaml"),
                CATALOG))
                .isEmpty();
    }

    @Test
    void beanFunctionInSimpleIsAReferenceToo(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("Counter.java"),
                "package com.example;\npublic class Counter { public int count() { return 1; } }\n");
        String yaml = """
                - from:
                    uri: timer:tick
                    steps:
                      - setBody:
                          simple: "${bean:counter.count}"
                      - log: "${bean:counter?method=count}"
                """;
        List<String> msgs
                = SourceValidator.validateYamlBeanRefs(yaml, SourceValidator.BeanDeclarations.scan(dir, "r.camel.yaml"),
                        CATALOG);
        assertThat(msgs).hasSize(2);
        assertThat(msgs.get(0))
                .startsWith("Line 5: ${bean:counter}: bean 'counter' is not declared: Counter.java is in the directory")
                .contains("type: \"#class:com.example.Counter\"");
        String declared = """
                - beans:
                    - name: counter
                      type: "#class:com.example.Counter"
                """ + yaml;
        assertThat(SourceValidator.validateYamlBeanRefs(declared, SourceValidator.BeanDeclarations.scan(dir, "r.camel.yaml"),
                CATALOG))
                .isEmpty();
    }

    @Test
    void beanFunctionWithAColonBeforeTheMethodIsExplained() {
        String yaml = """
                - beans:
                    - name: counter
                      type: "#class:com.example.Counter"
                - from:
                    uri: timer:tick
                    steps:
                      - setBody:
                          simple: "${bean:counter:count}"
                """;
        List<String> msgs = SourceValidator.validateYamlBeanRefs(yaml, SourceValidator.BeanDeclarations.NONE, CATALOG);
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0)).contains("${bean:counter.count}").contains("${bean:counter?method=count}")
                .contains("not with a single colon");
        assertThat(SourceValidator.validateYamlBeanRefs(yaml.replace("counter:count", "counter::count"),
                SourceValidator.BeanDeclarations.NONE, CATALOG)).isEmpty();
    }

    @Test
    void unknownBeanWithoutAJavaFileSaysHowToDeclareIt() {
        List<String> msgs = SourceValidator.validateYamlBeanRefs(ROUTE, SourceValidator.BeanDeclarations.NONE, CATALOG);
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0)).contains("not declared in this file or its directory").contains("- beans:");
    }

    @Test
    void stylesheetNotInTheDirectoryIsReported(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("customers-to-html.xsl"), "<xsl:stylesheet/>");
        List<String> msgs = SourceValidator.validate("transform.camel.yaml", """
                - from:
                    uri: timer:tick
                    steps:
                      - to:
                          uri: xslt:stylesheets/customers-to-html.xsl
                      - to:
                          uri: xslt:file:stylesheets/customers-to-html.xsl
                      - to:
                          uri: xslt:customers-to-html.xsl
                      - to:
                          uri: velocity:missing.vm
                      - to:
                          uri: xslt:http://example.com/x.xsl
                """, CATALOG, null, dir);
        assertThat(msgs).hasSize(3);
        assertThat(msgs.get(0)).startsWith("Line 5: xslt: the file stylesheets/customers-to-html.xsl does not exist")
                .contains("write xslt:customers-to-html.xsl");
        assertThat(msgs.get(1)).startsWith("Line 7: xslt: the file stylesheets/customers-to-html.xsl does not exist");
        assertThat(msgs.get(2)).startsWith("Line 11: velocity: the file missing.vm does not exist")
                .contains("velocity:<name>");
    }

    @Test
    void aPojoWithOnePublicMethodIsAnAggregationStrategy(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("MyAggregator.java"), """
                public class MyAggregator {
                    public String append(String existing, String body) {
                        return existing == null ? body : existing + "," + body;
                    }
                }
                """);
        String declared = """
                - beans:
                    - name: myAggregator
                      type: "#class:MyAggregator"
                """ + ROUTE;
        assertThat(SourceValidator.validate("r.camel.yaml", declared, CATALOG, null, dir)).isEmpty();

        Files.writeString(dir.resolve("MyAggregator.java"), """
                public class MyAggregator {
                    public MyAggregator() {
                    }
                    public String append(String existing, String body) {
                        return existing + body;
                    }
                    public int count(String s) {
                        return 1;
                    }
                }
                """);
        List<String> msgs = SourceValidator.validate("r.camel.yaml", declared, CATALOG, null, dir);
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0)).contains("has 2 public methods").contains("aggregationStrategyMethodName");
    }

    @Test
    void aBeanStepOnAClassWithSeveralMethodsNeedsAMethodName(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("LeakSimulator.java"), """
                public class LeakSimulator {
                    public int addObjects() {
                        return 1;
                    }
                    public static int getLeakedObjectCount() {
                        return 2;
                    }
                }
                """);
        String route = """
                - beans:
                    - name: leakSimulator
                      type: "#class:LeakSimulator"
                - from:
                    uri: timer:tick
                    steps:
                      - bean:
                          ref: leakSimulator
                      - bean:
                          ref: leakSimulator
                          method: addObjects
                """;
        List<String> msgs = SourceValidator.validate("r.camel.yaml", route, CATALOG, null, dir);
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0)).startsWith("Line 8: bean leakSimulator has 2 public methods (addObjects, getLeakedObjectCount)")
                .contains("add method: <name>");
    }

    @Test
    void aClassInTheWrongPackageIsReported(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("Other.java"), "public class Other {}\n");
        List<String> msgs = SourceValidator.validate("r.camel.yaml", """
                - beans:
                    - name: agg
                      type: "#class:org.apache.camel.support.StringAggregationStrategy"
                    - name: ok
                      type: "#class:org.apache.camel.processor.aggregate.StringAggregationStrategy"
                - from:
                    uri: timer:tick
                    steps:
                      - aggregate:
                          correlationExpression:
                            constant: "true"
                          completionSize: 3
                          aggregationStrategy: "#class:org.apache.camel.processor.aggregate.StringAggregationStrategy"
                          steps:
                            - log: "${body}"
                """, CATALOG, null, dir);
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0))
                .startsWith("Line 3: type: class org.apache.camel.support.StringAggregationStrategy was not found")
                .contains("did you mean org.apache.camel.processor.aggregate.StringAggregationStrategy?");
    }

    @Test
    void aClassCamelRunDownloadsIsNotReportedAsMissing(@TempDir Path dir) throws IOException {
        // the Postgres datasource and the Artemis connection factory are not on the CLI classpath, but camel run
        // resolves them to their Maven dependency (camel-main-known-dependencies.properties) and downloads it, so a
        // bean of that type runs; the validator must not contradict the runtime
        List<String> msgs = SourceValidator.validate("r.camel.yaml", """
                - beans:
                    - name: postgresDS
                      type: "#class:org.postgresql.ds.PGSimpleDataSource"
                      properties:
                        url: "jdbc:postgresql://localhost:5432/postgres"
                    - name: artemisCF
                      type: "#class:org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory"
                - route:
                    from:
                      uri: "timer:tick?period=1000"
                      steps:
                        - to:
                            uri: "sql:select 1?dataSource=#postgresDS"
                """, CATALOG, null, dir);
        assertThat(msgs).isEmpty();
        assertThat(BeanRefChecks.knownDependency("org.postgresql.ds.PGSimpleDataSource"))
                .startsWith("org.postgresql:postgresql");
        assertThat(BeanRefChecks.knownDependency("com.example.NoSuchThing")).isNull();
    }

    @Test
    void withoutTheMappingFilesOnTheClasspathNothingIsKnownAndNothingFails() {
        // a class loader with no resources at all: the check falls back to "unknown" instead of failing
        ClassLoader empty = new java.net.URLClassLoader(new java.net.URL[0], null);
        assertThat(BeanRefChecks.knownDependency("org.postgresql.ds.PGSimpleDataSource", empty)).isNull();
        assertThat(BeanRefChecks.knownDependency("org.postgresql.ds.PGSimpleDataSource")).isNotNull();
    }

    @Test
    void aClassFromAnUnknownLibrarySaysHowToDeclareTheDependency(@TempDir Path dir) throws IOException {
        // a package no mapping will ever name: com.zaxxer.hikari is mapped as a package by CAMEL-24809
        List<String> msgs = SourceValidator.validate("r.camel.yaml", """
                - beans:
                    - name: pool
                      type: "#class:com.example.pool.HikariDataSourceX"
                """, CATALOG, null, dir);
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0)).contains("was not found").contains("camel.jbang.dependencies=<groupId>:<artifactId>:<version>");
    }

    @Test
    void aSiblingClassImportedFromTheWrongPackageIsNamed(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("MemoryLeakSimulator.java"), """
                public class MemoryLeakSimulator {
                    public int count() {
                        return 1;
                    }
                }
                """);
        List<String> msgs = SourceValidator.validate("LeakBean.java", """
                import org.apache.camel.MemoryLeakSimulator;

                public class LeakBean {
                    public int leak() {
                        return new MemoryLeakSimulator().count();
                    }
                }
                """, CATALOG, null, dir);
        assertThat(msgs).isNotEmpty();
        assertThat(msgs.get(0)).contains("cannot find symbol")
                .contains("MemoryLeakSimulator is the class in MemoryLeakSimulator.java next to this file")
                .contains("has no package").doesNotContain("//DEPS");
    }

    @Test
    void anInnerClassAsABeanTypeIsNamed(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("Sim.java"), "public class Sim { public static class Leak {} }\n");
        List<String> msgs = SourceValidator.validate("r.camel.yaml", """
                - beans:
                    - name: leak
                      type: "#class:Sim$Leak"
                - from:
                    uri: timer:tick
                    steps:
                      - log: "a"
                """, CATALOG, null, dir);
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0)).contains("is an inner class of Sim").contains("Leak.java next to the route");
    }
}
