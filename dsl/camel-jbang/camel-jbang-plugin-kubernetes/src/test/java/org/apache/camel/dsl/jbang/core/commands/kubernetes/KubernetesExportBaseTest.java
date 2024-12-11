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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.openshift.api.model.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.RuntimeType;
import org.apache.camel.util.IOHelper;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import picocli.CommandLine;

@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*",
                          disabledReason = "Requires too much network resources")
public class KubernetesExportBaseTest extends KubernetesBaseTest {

    protected File workingDir;
    protected String[] defaultArgs;

    @BeforeEach
    public void setup() {
        super.setup();

        try {
            Path base = Paths.get("target");
            workingDir = Files.createTempDirectory(base, "camel-k8s-export").toFile();
            workingDir.deleteOnExit();
        } catch (IOException e) {
            throw new RuntimeCamelException(e);
        }

        defaultArgs = new String[] { "--dir=" + workingDir, "--quiet" };
    }

    protected KubernetesExport createCommand(String[] files, String... args) {
        var argsArr = Optional.ofNullable(args).orElse(new String[0]);
        var argsLst = new ArrayList<>(Arrays.asList(argsArr));
        argsLst.addAll(Arrays.asList(defaultArgs));
        KubernetesExport command = new KubernetesExport(new CamelJBangMain(), files);
        CommandLine.populateCommand(command, argsLst.toArray(new String[0]));
        return command;
    }

    protected Properties getApplicationProperties(File workingDir) throws IOException {
        String content = readResource(workingDir, "src/main/resources/application.properties");
        Properties applicationProperties = new Properties();
        applicationProperties.load(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
        return applicationProperties;
    }

    protected Deployment getDeployment(RuntimeType rt) throws IOException {
        return getResource(rt, Deployment.class)
                .orElseThrow(() -> new RuntimeCamelException("Cannot find deployment for: %s".formatted(rt.runtime())));
    }

    protected Ingress getIngress(RuntimeType rt) throws IOException {
        return getResource(rt, Ingress.class)
                .orElseThrow(() -> new RuntimeCamelException("Cannot find ingress for: %s".formatted(rt.runtime())));
    }

    protected boolean hasIngress(RuntimeType rt) throws IOException {
        return getResource(rt, Ingress.class).isPresent();
    }

    protected boolean hasKnativeService(RuntimeType rt) throws IOException {
        return getResource(rt, io.fabric8.knative.serving.v1.Service.class).isPresent();
    }

    protected Route getRoute(RuntimeType rt) throws IOException {
        return getResource(rt, Route.class)
                .orElseThrow(() -> new RuntimeCamelException("Cannot find route for: %s".formatted(rt.runtime())));
    }

    protected boolean hasRoute(RuntimeType rt) throws IOException {
        return getResource(rt, Route.class).isPresent();
    }

    protected Service getService(RuntimeType rt) throws IOException {
        return getResource(rt, Service.class)
                .orElseThrow(() -> new RuntimeCamelException("Cannot find service for: %s".formatted(rt.runtime())));
    }

    protected boolean hasService(RuntimeType rt) throws IOException {
        return getResource(rt, Service.class).isPresent();
    }

    protected <T extends HasMetadata> Optional<T> getResource(RuntimeType rt, Class<T> type) throws IOException {
        var kind = type.getSimpleName().toLowerCase();
        File file = new File(workingDir, "src/main/jkube/%s.yml".formatted(kind));
        if (file.isFile()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                List<HasMetadata> resources = kubernetesClient.load(fis).items();
                return resources.stream()
                        .filter(it -> type.isAssignableFrom(it.getClass()))
                        .map(type::cast)
                        .findFirst();
            }
        }
        return Optional.empty();
    }

    protected String readResource(File workingDir, String path) throws IOException {
        try (FileInputStream fis = new FileInputStream(workingDir.toPath().resolve(path).toFile())) {
            return IOHelper.loadText(fis);
        }
    }

    protected Model readMavenModel() throws Exception {
        File f = workingDir.toPath().resolve("pom.xml").toFile();
        Assertions.assertTrue(f.isFile(), "Not a pom.xml file: " + f);
        MavenXpp3Reader mavenReader = new MavenXpp3Reader();
        Model model = mavenReader.read(new FileReader(f));
        model.setPomFile(f);
        return model;
    }
}
