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
package org.apache.camel.dsl.jbang.core.commands;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.camel.dsl.jbang.core.common.CommandLineTestHelper;
import org.apache.camel.dsl.jbang.run.common.RuntimeType;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class ExportDependencyTest extends ExportBaseTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(ExportDependencyTest.class);

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldExportLazyBean(RuntimeType rt) throws Exception {
        LOG.info("shouldExportLazyBean {}", rt);
        Export command = createCommand(rt, new String[] { "classpath:route.yaml", "file:src/test/resources/LazyFoo.java" },
                "--gav=examples:route:1.0.0", "--dir=" + workingDir, "--quiet", "--lazy-bean=true");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Model model = readMavenModel();
        Assertions.assertEquals("examples", model.getGroupId());
        Assertions.assertEquals("route", model.getArtifactId());
        Assertions.assertEquals("1.0.0", model.getVersion());
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldExportCustomKamelet(RuntimeType rt) throws Exception {
        LOG.info("shouldExportCustomKamelet {}", rt);
        Export command = createCommand(rt,
                new String[] { "src/test/resources/route.yaml", "src/test/resources/user-source.kamelet.yaml" },
                "--gav=examples:route:1.0.0", "--dir=" + workingDir, "--quiet");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Model model = readMavenModel();
        Assertions.assertEquals("examples", model.getGroupId());
        Assertions.assertEquals("route", model.getArtifactId());
        Assertions.assertEquals("1.0.0", model.getVersion());

        if (rt == RuntimeType.main) {
            Assertions.assertTrue(containsDependency(model.getDependencies(), "org.apache.camel", "camel-kamelet", null));
            Assertions.assertTrue(containsDependency(model.getDependencies(), "org.apache.camel", "camel-http", null));
            Assertions.assertTrue(containsDependency(model.getDependencies(), "org.apache.camel", "camel-sql", null));
            Assertions
                    .assertFalse(
                            containsDependency(model.getDependencies(), "org.apache.camel.kamelets", "camel-kamelets", null));
        } else if (rt == RuntimeType.springBoot) {
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel.springboot", "camel-kamelet-starter", null));
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel.springboot", "camel-http-starter", null));
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel.springboot", "camel-sql-starter", null));
            Assertions
                    .assertFalse(
                            containsDependency(model.getDependencies(), "org.apache.camel.kamelets", "camel-kamelets", null));
        } else if (rt == RuntimeType.quarkus) {
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel.quarkus", "camel-quarkus-kamelet", null));
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel.quarkus", "camel-quarkus-http", null));
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel.quarkus", "camel-quarkus-sql", null));
            Assertions
                    .assertFalse(
                            containsDependency(model.getDependencies(), "org.apache.camel.kamelets", "camel-kamelets", null));
        }

        File f = workingDir.toPath().resolve("src/main/resources/kamelets/user-source.kamelet.yaml").toFile();
        Assertions.assertTrue(f.isFile());
        Assertions.assertTrue(f.exists());
        f = workingDir.toPath().resolve("src/main/resources/camel/route.yaml").toFile();
        Assertions.assertTrue(f.isFile());
        Assertions.assertTrue(f.exists());
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldExportOfficialKamelet(RuntimeType rt) throws Exception {
        LOG.info("shouldExportOfficialKamelet {}", rt);
        Export command = createCommand(rt, new String[] { "src/test/resources/counter.yaml" },
                "--gav=examples:route:1.0.0", "--dir=" + workingDir, "--quiet");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Model model = readMavenModel();
        Assertions.assertEquals("examples", model.getGroupId());
        Assertions.assertEquals("route", model.getArtifactId());
        Assertions.assertEquals("1.0.0", model.getVersion());

        if (rt == RuntimeType.main) {
            assertThat(model.getDependencies())
                    .as("Expected to find dependency: org.apache.camel:camel-kamelet")
                    .anySatisfy(dep -> {
                        assertThat(dep.getGroupId()).isEqualTo("org.apache.camel");
                        assertThat(dep.getArtifactId()).isEqualTo("camel-kamelet");
                    });

            assertThat(model.getDependencies())
                    .as("Expected to find dependency: org.apache.camel.kamelets:camel-kamelets")
                    .anySatisfy(dep -> {
                        assertThat(dep.getGroupId()).isEqualTo("org.apache.camel.kamelets");
                        assertThat(dep.getArtifactId()).isEqualTo("camel-kamelets");
                    });
        } else if (rt == RuntimeType.springBoot) {
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel.springboot", "camel-kamelet-starter", null));
            Assertions
                    .assertTrue(
                            containsDependency(model.getDependencies(), "org.apache.camel.kamelets", "camel-kamelets", null));
        } else if (rt == RuntimeType.quarkus) {
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel.quarkus", "camel-quarkus-kamelet", null));
            Assertions
                    .assertTrue(
                            containsDependency(model.getDependencies(), "org.apache.camel.kamelets", "camel-kamelets", null));
        }

        File f = workingDir.toPath().resolve("src/main/resources/camel/counter.yaml").toFile();
        Assertions.assertTrue(f.isFile());
        Assertions.assertTrue(f.exists());
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldExportSecret(RuntimeType rt) throws Exception {
        LOG.info("shouldExportSecret {}", rt);
        Export command = createCommand(rt,
                new String[] { "src/test/resources/k8s-secret.yaml" },
                "--gav=examples:route:1.0.0", "--dir=" + workingDir, "--quiet");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Model model = readMavenModel();
        Assertions.assertEquals("examples", model.getGroupId());
        Assertions.assertEquals("route", model.getArtifactId());
        Assertions.assertEquals("1.0.0", model.getVersion());

        if (rt == RuntimeType.main) {
            Assertions.assertTrue(containsDependency(model.getDependencies(), "org.apache.camel", "camel-kubernetes", null));
        } else if (rt == RuntimeType.springBoot) {
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel.springboot", "camel-kubernetes-starter",
                            null));
        } else if (rt == RuntimeType.quarkus) {
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel.quarkus", "camel-quarkus-kubernetes", null));
        }
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldExportActiveMQ(RuntimeType rt) throws Exception {
        LOG.info("shouldExportActiveMQ {}", rt);
        Export command = createCommand(rt, new String[] { "src/test/resources/ActiveMQRoute.java" },
                "--gav=examples:route:1.0.0", "--dir=" + workingDir, "--quiet");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Model model = readMavenModel();
        Assertions.assertEquals("examples", model.getGroupId());
        Assertions.assertEquals("route", model.getArtifactId());
        Assertions.assertEquals("1.0.0", model.getVersion());

        if (rt == RuntimeType.main) {
            Assertions.assertTrue(containsDependency(model.getDependencies(), "org.apache.camel", "camel-activemq", null));
            Assertions.assertTrue(containsDependency(model.getDependencies(), "org.messaginghub", "pooled-jms", null));
        } else if (rt == RuntimeType.springBoot) {
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel.springboot", "camel-activemq-starter", null));
            Assertions.assertTrue(containsDependency(model.getDependencies(), "org.messaginghub", "pooled-jms", null));
        } else if (rt == RuntimeType.quarkus) {
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel.quarkus", "camel-quarkus-activemq", null));
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "io.quarkiverse.messaginghub", "quarkus-pooled-jms", null));
        }
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldExportKafka(RuntimeType rt) throws Exception {
        LOG.info("shouldExportKafka {}", rt);
        Export command = createCommand(rt, new String[] { "src/test/resources/MyKafkaRepo.java" },
                "--gav=examples:route:1.0.0", "--dir=" + workingDir, "--quiet");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Model model = readMavenModel();
        Assertions.assertEquals("examples", model.getGroupId());
        Assertions.assertEquals("route", model.getArtifactId());
        Assertions.assertEquals("1.0.0", model.getVersion());

        if (rt == RuntimeType.main) {
            Assertions.assertTrue(containsDependency(model.getDependencies(), "org.apache.camel", "camel-kafka", null));
        } else if (rt == RuntimeType.springBoot) {
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel.springboot", "camel-kafka-starter",
                            null));
        } else if (rt == RuntimeType.quarkus) {
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel.quarkus", "camel-quarkus-kafka", null));
        }
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldExportMasterDelegate(RuntimeType rt) throws Exception {
        LOG.info("shouldExportMasterDelegate {}", rt);
        Export command = createCommand(rt, new String[] { "src/test/resources/master-route.yaml" },
                "--gav=examples:route:1.0.0", "--dir=" + workingDir, "--quiet");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Model model = readMavenModel();

        // the delegate component of the master endpoint must be exported as well (CAMEL-25186)
        if (rt == RuntimeType.main) {
            Assertions.assertTrue(containsDependency(model.getDependencies(), "org.apache.camel", "camel-master", null));
            Assertions.assertTrue(containsDependency(model.getDependencies(), "org.apache.camel", "camel-timer", null));
        } else if (rt == RuntimeType.springBoot) {
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel.springboot", "camel-master-starter",
                            null));
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel.springboot", "camel-timer-starter",
                            null));
        } else if (rt == RuntimeType.quarkus) {
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel.quarkus", "camel-quarkus-master", null));
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel.quarkus", "camel-quarkus-timer", null));
        }
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldExportZooKeeperMasterDelegate(RuntimeType rt) throws Exception {
        LOG.info("shouldExportZooKeeperMasterDelegate {}", rt);
        Export command = createCommand(rt, new String[] { "src/test/resources/zookeeper-master-route.yaml" },
                "--gav=examples:route:1.0.0", "--dir=" + workingDir, "--quiet");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Model model = readMavenModel();

        // the delegate component of the zookeeper-master endpoint must be exported as well (CAMEL-26116)
        if (rt == RuntimeType.main) {
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel", "camel-zookeeper-master", null));
            Assertions.assertTrue(containsDependency(model.getDependencies(), "org.apache.camel", "camel-timer", null));
        } else if (rt == RuntimeType.springBoot) {
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel.springboot",
                            "camel-zookeeper-master-starter", null));
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel.springboot", "camel-timer-starter",
                            null));
        } else if (rt == RuntimeType.quarkus) {
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel.quarkus",
                            "camel-quarkus-zookeeper-master", null));
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel.quarkus", "camel-quarkus-timer", null));
        }
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldExportWebhookDelegate(RuntimeType rt) throws Exception {
        LOG.info("shouldExportWebhookDelegate {}", rt);
        Export command = createCommand(rt, new String[] { "src/test/resources/webhook-route.yaml" },
                "--gav=examples:route:1.0.0", "--dir=" + workingDir, "--quiet");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Model model = readMavenModel();

        // the delegate component of the webhook endpoint must be exported as well (CAMEL-26325)
        if (rt == RuntimeType.main) {
            Assertions.assertTrue(containsDependency(model.getDependencies(), "org.apache.camel", "camel-webhook", null));
            Assertions.assertTrue(containsDependency(model.getDependencies(), "org.apache.camel", "camel-telegram", null));
        } else if (rt == RuntimeType.springBoot) {
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel.springboot", "camel-webhook-starter",
                            null));
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel.springboot", "camel-telegram-starter",
                            null));
        } else if (rt == RuntimeType.quarkus) {
            // camel-quarkus has no webhook extension
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel.quarkus", "camel-quarkus-telegram",
                            null));
        }
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldExportCircuitBreaker(RuntimeType rt) throws Exception {
        LOG.info("shouldExportCircuitBraeker {}", rt);
        Export command = createCommand(rt, new String[] { "src/test/resources/MyCB.java" },
                "--gav=examples:route:1.0.0", "--dir=" + workingDir, "--quiet");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Model model = readMavenModel();
        Assertions.assertEquals("examples", model.getGroupId());
        Assertions.assertEquals("route", model.getArtifactId());
        Assertions.assertEquals("1.0.0", model.getVersion());

        if (rt == RuntimeType.main) {
            Assertions.assertTrue(containsDependency(model.getDependencies(), "org.apache.camel", "camel-resilience4j", null));
        } else if (rt == RuntimeType.springBoot) {
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel.springboot", "camel-resilience4j-starter",
                            null));
        } else if (rt == RuntimeType.quarkus) {
            // quarkus does not have resilience4j but uses mp-fault-tolenrance
            Assertions.assertFalse(
                    containsDependency(model.getDependencies(), "org.apache.camel.quarkus", "camel-quarkus-resilience4j",
                            null));
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel.quarkus",
                            "camel-quarkus-microprofile-fault-tolerance",
                            null));
        }
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldExportJsonDataFormat(RuntimeType rt) throws Exception {
        LOG.info("shouldExportJsonDataFormat {}", rt);
        List<String> cmdArgs = new ArrayList<>(List.of("--gav=examples:route:1.0.0", "--dir=" + workingDir, "--quiet"));
        if (rt == RuntimeType.springBoot) {
            // a release whose Spring Boot catalog has camel-jackson3-starter (4.19 onwards)
            cmdArgs.add("--camel-version=4.23.0");
        }
        Export command = createCommand(rt, new String[] { "src/test/resources/MyJson.java" }, cmdArgs.toArray(String[]::new));
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Model model = readMavenModel();

        // the jackson data format of the catalog is the Jackson 3 line
        if (rt == RuntimeType.main) {
            Assertions.assertTrue(containsDependency(model.getDependencies(), "org.apache.camel", "camel-jackson3", null));
            Assertions.assertFalse(containsDependency(model.getDependencies(), "org.apache.camel", "camel-jackson", null));
        } else if (rt == RuntimeType.springBoot) {
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel.springboot", "camel-jackson3-starter", null));
        } else if (rt == RuntimeType.quarkus) {
            // Camel Quarkus has no camel-quarkus-jackson3: its jackson extension stands in for camel-jackson3
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel.quarkus", "camel-quarkus-jackson", null));
            Assertions.assertFalse(containsDependency(model.getDependencies(), "org.apache.camel", "camel-jackson3", null));
        }
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldExportSecretBean(RuntimeType rt) throws Exception {
        LOG.info("shouldExportSecretBean {}", rt);
        Export command = createCommand(rt,
                new String[] { "src/test/resources/k8s-secret-bean.yaml" },
                "--gav=examples:route:1.0.0", "--dir=" + workingDir, "--quiet");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Model model = readMavenModel();
        Assertions.assertEquals("examples", model.getGroupId());
        Assertions.assertEquals("route", model.getArtifactId());
        Assertions.assertEquals("1.0.0", model.getVersion());

        if (rt == RuntimeType.main) {
            Assertions.assertTrue(containsDependency(model.getDependencies(), "org.apache.camel", "camel-kubernetes", null));
        } else if (rt == RuntimeType.springBoot) {
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel.springboot", "camel-kubernetes-starter",
                            null));
        } else if (rt == RuntimeType.quarkus) {
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel.quarkus", "camel-quarkus-kubernetes", null));
        }
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldExportGroovy(RuntimeType rt) throws Exception {
        LOG.info("shouldExportGroovy {}", rt);
        Export command = createCommand(rt,
                new String[] { "src/test/resources/groovy-demo.camel.yaml", "src/test/resources/demo.groovy" },
                "--gav=examples:route:1.0.0", "--dir=" + workingDir, "--quiet");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Model model = readMavenModel();
        Assertions.assertEquals("examples", model.getGroupId());
        Assertions.assertEquals("route", model.getArtifactId());
        Assertions.assertEquals("1.0.0", model.getVersion());

        if (rt == RuntimeType.main) {
            Assertions.assertTrue(containsDependency(model.getDependencies(), "org.apache.camel", "camel-groovy", null));
        } else if (rt == RuntimeType.springBoot) {
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel.springboot", "camel-groovy-starter", null));
        } else if (rt == RuntimeType.quarkus) {
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel.quarkus", "camel-quarkus-groovy", null));
        }

        File f = workingDir.toPath().resolve("src/main/resources/camel-groovy/demo.groovy").toFile();
        Assertions.assertTrue(f.isFile());
        Assertions.assertTrue(f.exists());
    }

    /** CAMEL-24853: a Groovy script the route references as a resource: stays where the reference resolves. */
    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldExportAResourceReferencedGroovyScriptToTheResourcesRoot(RuntimeType rt) throws Exception {
        Export command = createCommand(rt,
                new String[] {
                        "src/test/resources/groovy-resource-demo.camel.yaml", "src/test/resources/shipment-mapping.groovy",
                        "src/test/resources/demo.groovy" },
                "--gav=examples:route:1.0.0", "--dir=" + workingDir, "--quiet");
        Assertions.assertEquals(0, command.doCall());

        File referenced = workingDir.toPath().resolve("src/main/resources/shipment-mapping.groovy").toFile();
        Assertions.assertTrue(referenced.isFile(), "the referenced script is at the resources root");
        Assertions.assertFalse(workingDir.toPath().resolve("src/main/resources/camel-groovy/shipment-mapping.groovy").toFile()
                .exists(), "and not in camel-groovy, where it would be compiled as a script");
        File script = workingDir.toPath().resolve("src/main/resources/camel-groovy/demo.groovy").toFile();
        Assertions.assertTrue(script.isFile(),
                "a script the routes do not reference as a resource goes to camel-groovy as before");
    }

    /** CAMEL-24853: the same for a route given as a classpath: resource, read from the classpath for the scan. */
    @Test
    public void shouldExportAResourceReferencedGroovyScriptOfAClasspathRoute() throws Exception {
        Export command = createCommand(RuntimeType.main,
                new String[] {
                        "classpath:groovy-resource-demo.camel.yaml", "src/test/resources/shipment-mapping.groovy",
                        "src/test/resources/demo.groovy" },
                "--gav=examples:route:1.0.0", "--dir=" + workingDir, "--quiet");
        Assertions.assertEquals(0, command.doCall());

        Assertions.assertTrue(workingDir.toPath().resolve("src/main/resources/shipment-mapping.groovy").toFile().isFile(),
                "the script the classpath: route references is at the resources root");
        Assertions.assertTrue(workingDir.toPath().resolve("src/main/resources/camel-groovy/demo.groovy").toFile().isFile());
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldExportToDWithCustomKamelet(RuntimeType rt) throws Exception {
        LOG.info("shouldExportToDWithCustomKamelet {}", rt);
        Export command = createCommand(rt,
                new String[] { "src/test/resources/toDroute.yaml", "src/test/resources/cheese-sink.kamelet.yaml" },
                "--gav=examples:route:1.0.0", "--dir=" + workingDir, "--quiet");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Model model = readMavenModel();
        Assertions.assertEquals("examples", model.getGroupId());
        Assertions.assertEquals("route", model.getArtifactId());
        Assertions.assertEquals("1.0.0", model.getVersion());

        if (rt == RuntimeType.main) {
            Assertions.assertTrue(containsDependency(model.getDependencies(), "org.apache.camel", "camel-log", null));
            Assertions.assertTrue(containsDependency(model.getDependencies(), "org.apache.camel", "camel-kamelet", null));
            Assertions
                    .assertFalse(
                            containsDependency(model.getDependencies(), "org.apache.camel.kamelets", "camel-kamelets", null));
        } else if (rt == RuntimeType.springBoot) {
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel.springboot", "camel-log-starter", null));
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel.springboot", "camel-kamelet-starter", null));
            Assertions
                    .assertFalse(
                            containsDependency(model.getDependencies(), "org.apache.camel.kamelets", "camel-kamelets", null));
        } else if (rt == RuntimeType.quarkus) {
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel.quarkus", "camel-quarkus-log", null));
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel.quarkus", "camel-quarkus-kamelet", null));
            Assertions
                    .assertFalse(
                            containsDependency(model.getDependencies(), "org.apache.camel.kamelets", "camel-kamelets", null));
        }

        File f = workingDir.toPath().resolve("src/main/resources/kamelets/cheese-sink.kamelet.yaml").toFile();
        Assertions.assertTrue(f.isFile());
        Assertions.assertTrue(f.exists());
        f = workingDir.toPath().resolve("src/main/resources/camel/toDroute.yaml").toFile();
        Assertions.assertTrue(f.isFile());
        Assertions.assertTrue(f.exists());
    }

    @Test
    void shouldKeepTheRunControllerOutWhenPlatformHttpIsGivenInAnyNotation() throws Exception {
        LOG.info("shouldKeepTheRunControllerOutWhenPlatformHttpIsGivenInAnyNotation");
        // camel-spring-boot 4.19+ requires JDK 21 (Spring Boot 4), as runtimeProvider has it
        Assumptions.assumeTrue(Runtime.version().feature() >= 21, "camel-spring-boot requires JDK 21");

        // a profile, so the exported project has an application.properties to read
        Path baseDir = baseDirWithApplicationProperties("camel.main.name=platform-http-demo\n");

        // --dep=camel-platform-http becomes camel:platform-http, and it is the HTTP server of the exported
        // project, so the run controller is not needed to keep it alive
        Export command = new CamelJBangMain().command(new Export());
        command.exportBaseDir = baseDir;
        CommandLineTestHelper.parse(command,
                "--gav=examples:route:1.0.0", "--dir=" + workingDir, "--quiet",
                "--runtime=spring-boot", "--camel-version=" + RELEASED_CAMEL_VERSION,
                CamelCommandBaseTestSupport.quarkusExtRegistry(),
                "--dep=camel-platform-http",
                "src/test/resources/route.yaml");
        int exit = command.doCall();

        assertThat(exit).isEqualTo(0);
        Properties props = loadExportedProperties("application.properties");
        assertThat(exportedProperty(props, "camel.main.run-controller"))
                .as("the export has an HTTP server of its own")
                .isNull();
        assertEachArtifactOnce(readMavenModel());
    }

    @Test
    void shouldReplacePlatformHttpInAnyNotation() throws Exception {
        LOG.info("shouldReplacePlatformHttpInAnyNotation");
        // --dep=camel-platform-http becomes camel:platform-http, which the camel-main runtime replaces with
        // camel-platform-http-main as well: the runtime exporter sees the dependencies before the duplicates are
        // removed, so it compares artifacts and not dependency strings
        Export command = new CamelJBangMain().command(new Export());
        CommandLineTestHelper.parse(command,
                "--gav=examples:route:1.0.0", "--dir=" + workingDir, "--quiet",
                "--runtime=camel-main",
                "--dep=camel-platform-http",
                "src/test/resources/route.yaml");
        int exit = command.doCall();

        assertThat(exit).isEqualTo(0);
        Model model = readMavenModel();

        assertThat(model.getDependencies()).extracting(Dependency::getArtifactId)
                .contains("camel-platform-http-main")
                .doesNotContain("camel-platform-http", "camel-platform-http-vertx");
        assertEachArtifactOnce(model);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "camel.main.name=platform-http-demo\n",
            "camel.main.name=platform-http-demo\ncamel.management.health-check-enabled=true\n" })
    void shouldAddCamelHealthWhenTheHealthCheckIsOn(String applicationProperties) throws Exception {
        LOG.info("shouldAddCamelHealthWhenTheHealthCheckIsOn");
        // platform-http turns on the embedded HTTP server and its health check in the exported application.properties,
        // and the management server does not start without camel-health, whether the export or the
        // application.properties turned the health check on
        Path baseDir = baseDirWithApplicationProperties(applicationProperties);

        Export command = new CamelJBangMain().command(new Export());
        command.exportBaseDir = baseDir;
        CommandLineTestHelper.parse(command,
                "--gav=examples:route:1.0.0", "--dir=" + workingDir, "--quiet",
                "--runtime=camel-main",
                "--dep=camel-platform-http",
                "src/test/resources/route.yaml");
        int exit = command.doCall();

        assertThat(exit).isEqualTo(0);
        Properties props = loadExportedProperties("application.properties");
        assertThat(exportedProperty(props, "camel.management.health-check-enabled")).isEqualTo("true");
        Model model = readMavenModel();
        assertThat(model.getDependencies()).extracting(Dependency::getArtifactId).contains("camel-health");
        assertEachArtifactOnce(model);
    }
}
