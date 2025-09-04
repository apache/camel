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

import java.util.regex.Pattern;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.v1.Integration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("Deprecated and resource intensive")
class IntegrationRunTest extends CamelKBaseTest {

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
        Assertions.assertEquals("camel-k", created.getMetadata().getAnnotations().get(CamelKCommand.OPERATOR_ID_LABEL));
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
        Assertions.assertEquals("camel-k", created.getMetadata().getAnnotations().get(CamelKCommand.OPERATOR_ID_LABEL));
    }

    @Test
    public void shouldAddTraits() throws Exception {
        IntegrationRun command = createCommand();
        command.filePaths = new String[] { "classpath:route.yaml" };
        command.traits = new String[] { "logging.level=DEBUG", "container.image-pull-policy=Always" };
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
                  - from:
                      uri: timer:tick
                      steps:
                      - setBody:
                          constant: Hello Camel !!!
                      - to: log:info
                  traits:
                    container:
                      imagePullPolicy: Always
                    logging:
                      level: DEBUG""", printer.getOutput());
    }

    @Test
    public void shouldAddTraitAddons() throws Exception {
        IntegrationRun command = createCommand();
        command.filePaths = new String[] { "classpath:route.yaml" };
        command.traits = new String[] {
                "container.port=8080", "telemetry.enabled=true",
                "telemetry.endpoint=http://opentelemetrycollector.ns.svc.cluster.local:8080" };
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
                  - from:
                      uri: timer:tick
                      steps:
                      - setBody:
                          constant: Hello Camel !!!
                      - to: log:info
                  traits:
                    addons:
                      telemetry:
                        endpoint: http://opentelemetrycollector.ns.svc.cluster.local:8080
                        enabled: true
                    container:
                      port: 8080""", printer.getOutput());
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
        command.traitProfile = "knative";
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
                  - from:
                      uri: timer:tick
                      steps:
                      - setBody:
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
                  - from:
                      uri: timer:tick
                      steps:
                      - setBody:
                          constant: Hello Camel !!!
                      - to: log:info
                  traits:
                    mount:
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
                  - from:
                      uri: timer:tick
                      steps:
                      - setBody:
                          constant: Hello Camel !!!
                      - to: log:info
                  traits: {}""", printer.getOutput());
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
                  - from:
                      uri: timer:tick
                      steps:
                      - setBody:
                          constant: Hello Camel !!!
                      - to: log:info
                  traits:
                    environment:
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
                  - from:
                      uri: timer:tick
                      steps:
                      - setBody:
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
                  - from:
                      uri: timer:tick
                      steps:
                      - setBody:
                          constant: Hello Camel !!!
                      - to: log:info
                  traits:
                    builder:
                      properties:
                      - camel.foo=bar""", printer.getOutput());
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
                  - from:
                      uri: timer:tick
                      steps:
                      - setBody:
                          constant: Hello Camel !!!
                      - to: log:info
                  integrationKit:
                    name: kit-123456789
                  traits: {}""", printer.getOutput());
    }

    @Test
    public void shouldUseIntegrationProfile() throws Exception {
        IntegrationRun command = createCommand();
        command.filePaths = new String[] { "classpath:route.yaml" };
        command.integrationProfile = "my-profile";
        command.output = "yaml";
        command.doCall();

        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Integration
                metadata:
                  annotations:
                    camel.apache.org/operator.id: camel-k
                    camel.apache.org/integration-profile.id: my-profile
                  name: route
                spec:
                  flows:
                  - from:
                      uri: timer:tick
                      steps:
                      - setBody:
                          constant: Hello Camel !!!
                      - to: log:info
                  traits: {}""", printer.getOutput());
    }

    @Test
    public void shouldUseNamespacedIntegrationProfile() throws Exception {
        IntegrationRun command = createCommand();
        command.filePaths = new String[] { "classpath:route.yaml" };
        command.integrationProfile = "my-namespace/my-profile";
        command.output = "yaml";
        command.doCall();

        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Integration
                metadata:
                  annotations:
                    camel.apache.org/operator.id: camel-k
                    camel.apache.org/integration-profile.namespace: my-namespace
                    camel.apache.org/integration-profile.id: my-profile
                  name: route
                spec:
                  flows:
                  - from:
                      uri: timer:tick
                      steps:
                      - setBody:
                          constant: Hello Camel !!!
                      - to: log:info
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
                  - from:
                      uri: timer:tick
                      steps:
                      - setBody:
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
                  - from:
                      uri: timer:tick
                      steps:
                      - setBody:
                          constant: Hello Camel !!!
                      - to: log:info
                  traits:
                    service-binding:
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
                  - from:
                      uri: timer:tick
                      steps:
                      - setBody:
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
                  - from:
                      uri: timer:tick
                      steps:
                      - setBody:
                          constant: Hello Camel !!!
                      - to: log:info
                  traits:
                    mount:
                      configs:
                      - secret:foo
                      - configmap:bar""", printer.getOutput());
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
                  - from:
                      uri: timer:tick
                      steps:
                      - setBody:
                          constant: Hello Camel !!!
                      - to: log:info
                  traits:
                    mount:
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
                  - from:
                      uri: timer:tick
                      steps:
                      - setBody:
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
                      image: quay.io/camel/demo-app:1.0""", printer.getOutput());
    }

    @Test
    public void shouldUseTraitWithListItem() throws Exception {
        IntegrationRun command = createCommand();
        command.filePaths = new String[] { "classpath:route.yaml" };
        command.traits
                = new String[] {
                        "toleration.taints=camel.apache.org/master:NoExecute:300", "camel.properties=camel.foo=bar",
                        "affinity.node-affinity-labels=kubernetes.io/hostname" };
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
                  - from:
                      uri: timer:tick
                      steps:
                      - setBody:
                          constant: Hello Camel !!!
                      - to: log:info
                  traits:
                    affinity:
                      nodeAffinityLabels:
                      - kubernetes.io/hostname
                    camel:
                      properties:
                      - camel.foo=bar
                    toleration:
                      taints:
                      - camel.apache.org/master:NoExecute:300""", printer.getOutput());
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
                            content: ZFPBbtswDL3nK16TSwukybCjd3LTBDVWOECcrshRsWlbqC15Ej03fz/KSdYA08WgRD6+90jPJjO86pyMpwJswTUh7lQun8yWPChH2NjeFIq1NbiPs80DJCQHawjWobWOBCS3hp0+9ixXzRkQqnJELRn2CyAjGtHT7T5ZrVHqhlBofy6S5oPmWnC41h6DdR8oBUkVhQ6NVQNt5KI903BUKVdoU0nb7uR0VTPsYMj5WncLQdkHGdnmysSfYceeIvJg+4uGG7kXF+b4JTChyffFN0G6DynTy+P04QdOUtyqE4xl9J5ukOkzp46FqLBqu0Yrk9OXrH8dxIvDBcMeWUm6GmXAlrdpUDyZSeV4auYuWi6HYVioke7Cump5Vbd8FUfTbP04UpaaN9OQ92LT71478fZ4guqEUa6OwrNRQxjcOJ1x6EJhcOKzqebwl6kLyu10vuy60hPVtwlimDKYxhmSbIqnOEuyuWC8J/uX7dse7/FuF6f7ZJ1hu8Nqmz4n+2SbSrRBnB7wM0mf5yAxS9rQZ+cCfyGpg5FUhJleF+hKIOxHiH1HuS51LrpM1auKUNk/5ExYj45cq30Ypxd6haA0utU8bpH/X5S0mUxKZ9toAvROR2DdkotY5x9y45k6H56AR3jiJ1uczmE48gd4VoYjvFDTWKxUSw3u7u4uBWwjNLaKwiJP/gIAAP//
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
                          - setBody:
                              constant: Hello Camel !!!
                          - to: log:info
                    language: yaml
                    name: route.yaml
                  traits: {}""", removeLicenseHeader(printer.getOutput()));
    }

    @Test
    public void shouldFailWithMissingOperatorId() throws Exception {
        IntegrationRun command = createCommand();
        command.filePaths = new String[] { "classpath:route.yaml" };
        command.useFlows = false;
        command.output = "yaml";

        command.operatorId = "";

        Assertions.assertEquals(-1, command.doCall());

        Assertions.assertEquals("Operator id must be set", printer.getOutput());
    }

    @Test
    public void shouldHandleUnsupportedOutputFormat() throws Exception {
        IntegrationRun command = createCommand();
        command.filePaths = new String[] { "classpath:route.yaml" };
        command.useFlows = false;
        command.output = "wrong";

        Assertions.assertEquals(-1, command.doCall());

        Assertions.assertEquals("Unsupported output format 'wrong' (supported: yaml, json)", printer.getOutput());
    }

    private IntegrationRun createCommand() {
        IntegrationRun command = new IntegrationRun(new CamelJBangMain().withPrinter(printer));
        command.withClient(kubernetesClient);
        return command;
    }

}
