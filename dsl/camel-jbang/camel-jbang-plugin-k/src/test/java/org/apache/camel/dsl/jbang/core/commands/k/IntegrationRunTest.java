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

package org.apache.camel.dsl.jbang.core.commands.k;

import java.util.Arrays;
import java.util.regex.Pattern;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.v1.Integration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class IntegrationRunTest extends KubeBaseTest {

    @Test
    public void shouldHandleMissingSourceFile() throws Exception {
        IntegrationRun command = createCommand();
        command.filePaths = new String[] { "mickey-mouse.groovy" };
        Assertions.assertThrows(RuntimeCamelException.class, command::doCall, "Failed to resolve sources");
    }

    @Test
    public void shouldRunIntegration() throws Exception {
        IntegrationRun command = createCommand();
        command.filePaths = new String[] { "classpath:route.yaml" };
        command.doCall();

        Assertions.assertEquals("Integration route created", printer.getOutput());

        Integration created = kubernetesClient.resources(Integration.class).withName("route").get();
        Assertions.assertEquals("camel-k", created.getMetadata().getAnnotations().get(KubeCommand.OPERATOR_ID_LABEL));
    }

    @Test
    public void shouldUpdateIntegration() throws Exception {
        Integration integration = createIntegration("route");
        kubernetesClient.resources(Integration.class).resource(integration).create();

        IntegrationRun command = createCommand();
        command.filePaths = new String[] { "classpath:route.yaml" };
        command.doCall();

        Assertions.assertEquals("Integration route updated", printer.getOutput());

        Integration created = kubernetesClient.resources(Integration.class).withName("route").get();
        Assertions.assertEquals("camel-k", created.getMetadata().getAnnotations().get(KubeCommand.OPERATOR_ID_LABEL));
    }

    @Test
    public void shouldAddTraits() throws Exception {
        IntegrationRun command = createCommand();
        command.filePaths = new String[] { "classpath:route.yaml" };
        command.traits = new String[] { "logging.level=DEBUG", "container.imagePullPolicy=Always" };
        command.output = "yaml";
        command.doCall();

        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Integration
                metadata:
                  annotations:
                    camel.apache.org/operator.id: camel-k
                  name: route
                spec:
                  flows:
                  - additionalProperties:
                      from:
                        uri: timer:tick
                        steps:
                        - set-body:
                            constant: Hello Camel !!!
                        - to: log:info
                  traits:
                    container:
                      imagePullPolicy: ALWAYS
                      name: integration
                      port: 8080
                      servicePort: 80
                      servicePortName: http
                    logging:
                      level: DEBUG""", printer.getOutput());
    }

    @Test
    public void shouldSpecFromOptions() throws Exception {
        IntegrationRun command = createCommand();
        command.filePaths = new String[] { "classpath:route.yaml" };
        command.name = "custom";
        command.operatorId = "custom-operator";
        command.serviceAccount = "service-account-name";
        command.labels = new String[] { "custom-label=enabled" };
        command.annotations = new String[] { "custom-annotation=enabled" };
        command.repositories = new String[] { "http://custom-repository" };
        command.profile = "knative";
        command.output = "yaml";
        command.doCall();

        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Integration
                metadata:
                  annotations:
                    custom-annotation: enabled
                    camel.apache.org/operator.id: custom-operator
                  labels:
                    custom-label: enabled
                  name: custom
                spec:
                  flows:
                  - additionalProperties:
                      from:
                        uri: timer:tick
                        steps:
                        - set-body:
                            constant: Hello Camel !!!
                        - to: log:info
                  profile: knative
                  repositories:
                  - http://custom-repository
                  serviceAccountName: service-account-name
                  traits: {}""", printer.getOutput());
    }

    @Test
    public void shouldAddVolumes() throws Exception {
        IntegrationRun command = createCommand();
        command.filePaths = new String[] { "classpath:route.yaml" };
        command.volumes = new String[] { "/foo", "/bar" };
        command.output = "yaml";
        command.doCall();

        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Integration
                metadata:
                  annotations:
                    camel.apache.org/operator.id: camel-k
                  name: route
                spec:
                  flows:
                  - additionalProperties:
                      from:
                        uri: timer:tick
                        steps:
                        - set-body:
                            constant: Hello Camel !!!
                        - to: log:info
                  traits:
                    mount:
                      hotReload: false
                      volumes:
                      - /foo
                      - /bar""", printer.getOutput());
    }

    @Test
    public void shouldAddDependencies() throws Exception {
        IntegrationRun command = createCommand();
        command.filePaths = new String[] { "classpath:route.yaml" };
        command.dependencies = new String[] { "camel-jackson", "camel-quarkus-jms", "mvn:foo:bar:1.0" };
        command.output = "yaml";
        command.doCall();

        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Integration
                metadata:
                  annotations:
                    camel.apache.org/operator.id: camel-k
                  name: route
                spec:
                  dependencies:
                  - camel:jackson
                  - camel:jms
                  - mvn:foo:bar:1.0
                  flows:
                  - additionalProperties:
                      from:
                        uri: timer:tick
                        steps:
                        - set-body:
                            constant: Hello Camel !!!
                        - to: log:info
                  traits: {}""", printer.getOutput());
    }

    @Test
    public void shouldInferDependenciesYamlRoute() throws Exception {
        IntegrationRun command = createCommand();
        command.filePaths = new String[] { "classpath:route-deps.yaml" };
        command.output = "yaml";
        command.doCall();

        String[] specDependencies = getDependencies(printer.getOutput());
        String[] deps = new String[] {
                "camel:caffeine", "camel:http", "camel:jackson", "camel:jsonpath", "camel:log", "camel:timer" };
        Assertions.assertArrayEquals(deps, specDependencies, "Dependencies don't match: " + Arrays.toString(specDependencies));
    }

    @Test
    public void shouldAddComplexDependenciesYamlRoute() throws Exception {
        IntegrationRun command = createCommand();
        command.filePaths = new String[] { "classpath:route-deps.yaml" };
        command.dependencies = new String[] { "camel-twitter", "mvn:foo:bar:1.0" };
        command.output = "yaml";
        command.doCall();

        String[] specDependencies = getDependencies(printer.getOutput());
        String[] deps = new String[] {
                "camel:caffeine", "camel:http", "camel:jackson", "camel:jsonpath", "camel:log", "camel:timer", "camel:twitter",
                "mvn:foo:bar:1.0" };
        Assertions.assertArrayEquals(deps, specDependencies, "Dependencies don't match: " + Arrays.toString(specDependencies));
    }

    @Test
    public void shouldInferDependenciesJavaRoute() throws Exception {
        IntegrationRun command = createCommand();
        command.filePaths = new String[] { "classpath:Sample.java" };
        command.output = "yaml";
        command.doCall();

        String[] specDependencies = getDependencies(printer.getOutput());
        String[] deps = new String[] {
                "camel:aws2-s3", "camel:caffeine", "camel:dropbox", "camel:jacksonxml", "camel:java-joor-dsl", "camel:kafka",
                "camel:mongodb", "camel:telegram", "camel:zipfile" };
        Assertions.assertArrayEquals(deps, specDependencies, "Dependencies don't match: " + Arrays.toString(specDependencies));
    }

    @Test
    public void shouldAddComplexDependenciesJavaRoute() throws Exception {
        IntegrationRun command = createCommand();
        command.filePaths = new String[] { "classpath:Sample.java" };
        command.dependencies = new String[] { "camel-twitter", "mvn:foo:bar:1.0" };
        command.output = "yaml";
        command.doCall();

        String[] specDependencies = getDependencies(printer.getOutput());
        String[] deps = new String[] {
                "camel:aws2-s3", "camel:caffeine", "camel:dropbox", "camel:jacksonxml", "camel:java-joor-dsl", "camel:kafka",
                "camel:mongodb", "camel:telegram", "camel:twitter", "camel:zipfile", "mvn:foo:bar:1.0" };
        Assertions.assertArrayEquals(deps, specDependencies, "Dependencies don't match: " + Arrays.toString(specDependencies));
    }

    @Test
    public void shouldAddEnvVars() throws Exception {
        IntegrationRun command = createCommand();
        command.filePaths = new String[] { "classpath:route.yaml" };
        command.envVars = new String[] { "CAMEL_FOO=bar" };
        command.output = "yaml";
        command.doCall();

        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Integration
                metadata:
                  annotations:
                    camel.apache.org/operator.id: camel-k
                  name: route
                spec:
                  flows:
                  - additionalProperties:
                      from:
                        uri: timer:tick
                        steps:
                        - set-body:
                            constant: Hello Camel !!!
                        - to: log:info
                  traits:
                    environment:
                      containerMeta: true
                      httpProxy: true
                      vars:
                      - CAMEL_FOO=bar""", printer.getOutput());
    }

    @Test
    public void shouldAddProperties() throws Exception {
        IntegrationRun command = createCommand();
        command.filePaths = new String[] { "classpath:route.yaml" };
        command.properties = new String[] { "camel.foo=bar" };
        command.output = "yaml";
        command.doCall();

        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Integration
                metadata:
                  annotations:
                    camel.apache.org/operator.id: camel-k
                  name: route
                spec:
                  flows:
                  - additionalProperties:
                      from:
                        uri: timer:tick
                        steps:
                        - set-body:
                            constant: Hello Camel !!!
                        - to: log:info
                  traits:
                    camel:
                      properties:
                      - camel.foo=bar""", printer.getOutput());
    }

    @Test
    public void shouldAddBuildProperties() throws Exception {
        IntegrationRun command = createCommand();
        command.filePaths = new String[] { "classpath:route.yaml" };
        command.buildProperties = new String[] { "camel.foo=bar" };
        command.output = "yaml";
        command.doCall();

        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Integration
                metadata:
                  annotations:
                    camel.apache.org/operator.id: camel-k
                  name: route
                spec:
                  flows:
                  - additionalProperties:
                      from:
                        uri: timer:tick
                        steps:
                        - set-body:
                            constant: Hello Camel !!!
                        - to: log:info
                  traits:
                    builder:
                      incrementalImageBuild: true
                      orderStrategy: SEQUENTIAL
                      properties:
                      - camel.foo=bar
                      strategy: ROUTINE""", printer.getOutput());
    }

    @Test
    public void shouldUseKit() throws Exception {
        IntegrationRun command = createCommand();
        command.filePaths = new String[] { "classpath:route.yaml" };
        command.kit = "kit-123456789";
        command.output = "yaml";
        command.doCall();

        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Integration
                metadata:
                  annotations:
                    camel.apache.org/operator.id: camel-k
                  name: route
                spec:
                  flows:
                  - additionalProperties:
                      from:
                        uri: timer:tick
                        steps:
                        - set-body:
                            constant: Hello Camel !!!
                        - to: log:info
                  integrationKit:
                    name: kit-123456789
                  traits: {}""", printer.getOutput());
    }

    @Test
    public void shouldAddSources() throws Exception {
        IntegrationRun command = createCommand();
        command.sources = new String[] { "classpath:route.yaml" };
        command.output = "yaml";
        command.doCall();

        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Integration
                metadata:
                  annotations:
                    camel.apache.org/operator.id: camel-k
                  name: route
                spec:
                  flows:
                  - additionalProperties:
                      from:
                        uri: timer:tick
                        steps:
                        - set-body:
                            constant: Hello Camel !!!
                        - to: log:info
                  traits: {}""", printer.getOutput());
    }

    @Test
    public void shouldAddConnects() throws Exception {
        IntegrationRun command = createCommand();
        command.filePaths = new String[] { "classpath:route.yaml" };
        command.connects = new String[] { "serving.knative.dev/v1:Service:foo" };
        command.output = "yaml";
        command.doCall();

        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Integration
                metadata:
                  annotations:
                    camel.apache.org/operator.id: camel-k
                  name: route
                spec:
                  flows:
                  - additionalProperties:
                      from:
                        uri: timer:tick
                        steps:
                        - set-body:
                            constant: Hello Camel !!!
                        - to: log:info
                  traits:
                    serviceBinding:
                      services:
                      - serving.knative.dev/v1:Service:foo""", printer.getOutput());
    }

    @Test
    public void shouldUsePodTemplate() throws Exception {
        IntegrationRun command = createCommand();
        command.filePaths = new String[] { "classpath:route.yaml" };
        command.podTemplate = "classpath:pod.yaml";
        command.output = "yaml";
        command.doCall();

        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Integration
                metadata:
                  annotations:
                    camel.apache.org/operator.id: camel-k
                  name: route
                spec:
                  flows:
                  - additionalProperties:
                      from:
                        uri: timer:tick
                        steps:
                        - set-body:
                            constant: Hello Camel !!!
                        - to: log:info
                  template:
                    spec:
                      containers:
                      - env:
                        - name: TEST
                          value: TEST
                        name: integration
                        volumeMounts:
                        - mountPath: /var/log
                          name: var-logs
                      volumes:
                      - emptyDir: {}
                        name: var-logs
                  traits: {}""", printer.getOutput());
    }

    @Test
    public void shouldAddConfigs() throws Exception {
        IntegrationRun command = createCommand();
        command.filePaths = new String[] { "classpath:route.yaml" };
        command.configs = new String[] { "secret:foo", "configmap:bar" };
        command.output = "yaml";
        command.doCall();

        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Integration
                metadata:
                  annotations:
                    camel.apache.org/operator.id: camel-k
                  name: route
                spec:
                  flows:
                  - additionalProperties:
                      from:
                        uri: timer:tick
                        steps:
                        - set-body:
                            constant: Hello Camel !!!
                        - to: log:info
                  traits:
                    mount:
                      configs:
                      - secret:foo
                      - configmap:bar
                      hotReload: false""", printer.getOutput());
    }

    @Test
    public void shouldAddResources() throws Exception {
        IntegrationRun command = createCommand();
        command.filePaths = new String[] { "classpath:route.yaml" };
        command.resources = new String[] { "configmap:foo/file.txt" };
        command.output = "yaml";
        command.doCall();

        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Integration
                metadata:
                  annotations:
                    camel.apache.org/operator.id: camel-k
                  name: route
                spec:
                  flows:
                  - additionalProperties:
                      from:
                        uri: timer:tick
                        steps:
                        - set-body:
                            constant: Hello Camel !!!
                        - to: log:info
                  traits:
                    mount:
                      hotReload: false
                      resources:
                      - configmap:foo/file.txt""", printer.getOutput());
    }

    @Test
    public void shouldAddOpenApis() throws Exception {
        IntegrationRun command = createCommand();
        command.filePaths = new String[] { "classpath:route.yaml" };
        command.openApis = new String[] { "configmap:openapi/spec.yaml" };
        command.output = "yaml";
        command.doCall();

        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Integration
                metadata:
                  annotations:
                    camel.apache.org/operator.id: camel-k
                  name: route
                spec:
                  flows:
                  - additionalProperties:
                      from:
                        uri: timer:tick
                        steps:
                        - set-body:
                            constant: Hello Camel !!!
                        - to: log:info
                  traits:
                    openapi:
                      configmaps:
                      - configmap:openapi/spec.yaml""", printer.getOutput());
    }

    @Test
    public void shouldUseImage() throws Exception {
        IntegrationRun command = createCommand();
        command.filePaths = new String[] { "classpath:route.yaml" };
        command.image = "quay.io/camel/demo-app:1.0";
        command.output = "yaml";
        command.doCall();

        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Integration
                metadata:
                  annotations:
                    camel.apache.org/operator.id: camel-k
                  name: demo-app-v1
                spec:
                  traits:
                    container:
                      image: quay.io/camel/demo-app:1.0
                      name: integration
                      port: 8080
                      servicePort: 80
                      servicePortName: http""", printer.getOutput());
    }

    @Test
    public void shouldUseCompression() throws Exception {
        IntegrationRun command = createCommand();
        command.filePaths = new String[] { "classpath:route.yaml" };
        command.compression = true;
        command.output = "yaml";
        command.doCall();

        Assertions.assertEquals(
                """
                        apiVersion: camel.apache.org/v1
                        kind: Integration
                        metadata:
                          annotations:
                            camel.apache.org/operator.id: camel-k
                          name: route
                        spec:
                          sources:
                          - compression: true
                            content: ZFNNb6NADL3zK5zk0kr5WO2RPbFpoqKtiBRoqxwnYMAqzLAzZmn+/XoI2UbauSDP2M/vPZtFsIAXylE7LIANcI0QdSqXT2pKHpRF2JteF4rJaHiI0v0jSIgWjEYwFlpjUUByo9nSuWe5aq6AoCqL2KJmtwZIEUf05JDF2x2U1CAU5K5F0nwgrgWHa3IwGPsBpSCpoiDfWDVAWi7aKw2LlbIF6UradhdLVc1gBo3W1dStBSXzMtL9jYm7wo49ReTJ9JOGO7mTC0t4Exjf5Pv6myA9+JT59Dh//AEXKW7VBbRh6B3eIeNnjh0LUWHVdg0pneOXrH8dxIvThGHOrCRdjTLAlPdpoDhYSOV4auYu3GyGYVirke7a2GpzU7d5EUeTdLcaKUvNq27QObHpd09WvD1fQHXCKFdn4dmowQ9unM44dKEwWPFZV0tw09QF5X46X3bd6Inq+wQxTGmYRynE6Rx+RmmcLgXjPc6eD68ZvEfHY5Rk8S6FwxG2h+QpzuJDItEeouQEv+LkaQkoZkkb/Oys5y8kyRuJhZ/pbYFuBPx++Nh1mFNJuejSVa8qhMr8Qav9enRoW3J+nE7oFYLSUEs8bpH7X5S0CYLSmjYMAHpLITC1aEOm/ENuHGPn/BPAChzy6myKyzX2R34Bx0pzCM/YNAa2qsUGZrPZVMEmhMZUod/k4C8AAAD//w==
                            language: yaml
                            name: route.yaml
                          traits: {}""",
                removeLicenseHeader(printer.getOutput()));
    }

    private final Pattern comments = Pattern.compile("^\\s*#.*$", Pattern.MULTILINE);
    private final Pattern emptyLine = Pattern.compile("^[\\r?\\n]$", Pattern.MULTILINE);

    private String removeLicenseHeader(String yaml) {
        return emptyLine.matcher(comments.matcher(yaml).replaceAll("")).replaceAll("");
    }

    @Test
    public void shouldHandleUseFlowsDisabledOption() throws Exception {
        IntegrationRun command = createCommand();
        command.filePaths = new String[] { "classpath:route.yaml" };
        command.useFlows = false;
        command.output = "yaml";
        command.doCall();

        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Integration
                metadata:
                  annotations:
                    camel.apache.org/operator.id: camel-k
                  name: route
                spec:
                  sources:
                  - compression: false
                    content: |

                      from:
                        uri: timer:tick
                        steps:
                          - set-body:
                              constant: Hello Camel !!!
                          - to: log:info
                    language: yaml
                    name: route.yaml
                  traits: {}""", removeLicenseHeader(printer.getOutput()));
    }

    private IntegrationRun createCommand() {
        IntegrationRun command = new IntegrationRun(new CamelJBangMain().withPrinter(printer));
        command.withClient(kubernetesClient);
        return command;
    }

}
