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

import java.util.stream.Collectors;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.KubernetesExport;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.KubernetesHelper;
import org.apache.camel.dsl.jbang.core.common.RuntimeType;
import org.apache.camel.dsl.jbang.core.common.Source;
import org.apache.camel.dsl.jbang.core.common.SourceHelper;
import org.apache.camel.v1.Integration;
import org.apache.camel.v1.Pipe;
import picocli.CommandLine;

@CommandLine.Command(name = "export",
                     description = "Export integration as an arbitrary Maven/Gradle project with a Kubernetes deployment manifest",
                     sortOptions = false)
public class IntegrationExport extends KubernetesExport {

    private Integration integration;
    private Pipe pipe;

    public IntegrationExport(CamelJBangMain main) {
        super(main);
    }

    public IntegrationExport(CamelJBangMain main, RuntimeType runtime, String[] files, String exportDir, String imageGroup,
                             boolean quiet) {
        super(main, files);
        this.runtime = runtime;
        this.imageGroup = imageGroup;
        this.exportDir = exportDir;
        this.quiet = quiet;
    }

    @Override
    public Integer export() throws Exception {
        if (files.size() != 1) {
            if (!quiet) {
                printer().println("Project export failed - requires single Integration or Pipe source file as an argument");
            }
            return 1;
        }

        Source source = SourceHelper.resolveSource(files.get(0));
        if (source.content().contains("kind: Integration")) {
            integration = KubernetesHelper.yaml(this.getClass().getClassLoader()).loadAs(source.content(), Integration.class);

            if (integration.getMetadata().getAnnotations() != null) {
                this.annotations = integration.getMetadata().getAnnotations().entrySet().stream()
                        .map(entry -> "%s=%s".formatted(entry.getKey(), entry.getValue()))
                        .collect(Collectors.toSet()).toArray(String[]::new);
            }

            if (integration.getMetadata().getLabels() != null) {
                this.labels = integration.getMetadata().getLabels().entrySet().stream()
                        .map(entry -> "%s=%s".formatted(entry.getKey(), entry.getValue()))
                        .collect(Collectors.toSet()).toArray(String[]::new);
            }
        } else if (source.content().contains("kind: Pipe")) {
            pipe = KubernetesHelper.yaml(this.getClass().getClassLoader()).loadAs(source.content(), Pipe.class);

            if (pipe.getMetadata().getAnnotations() != null) {
                this.annotations = pipe.getMetadata().getAnnotations().entrySet().stream()
                        .map(entry -> "%s=%s".formatted(entry.getKey(), entry.getValue()))
                        .collect(Collectors.toSet()).toArray(String[]::new);
            }

            if (pipe.getMetadata().getLabels() != null) {
                this.labels = pipe.getMetadata().getLabels().entrySet().stream()
                        .map(entry -> "%s=%s".formatted(entry.getKey(), entry.getValue()))
                        .collect(Collectors.toSet()).toArray(String[]::new);
            }
        } else {
            if (!quiet) {
                printer().println("Project export failed - not an Integration or Pipe source");
            }
            return 1;
        }

        return super.export();
    }

    @Override
    protected org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.model.Traits getTraitSpec(
            String[] applicationProperties, String[] applicationProfileProperties) {
        if (integration != null && integration.getSpec().getTraits() != null) {
            return KubernetesHelper.yaml(this.getClass().getClassLoader())
                    .loadAs(KubernetesHelper.dumpYaml(integration.getSpec().getTraits()),
                            org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.model.Traits.class);
        }

        if (pipe != null && pipe.getSpec().getIntegration() != null
                && pipe.getSpec().getIntegration().getTraits() != null) {
            // convert pipe spec traits to integration spec traits
            return KubernetesHelper.yaml(this.getClass().getClassLoader())
                    .loadAs(KubernetesHelper.dumpYaml(pipe.getSpec().getIntegration().getTraits()),
                            org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.model.Traits.class);
        }

        return super.getTraitSpec(applicationProperties, applicationProfileProperties);
    }

    @Override
    protected String getProjectName() {
        if (integration != null) {
            return integration.getMetadata().getName();
        }

        if (pipe != null) {
            return pipe.getMetadata().getName();
        }
        return super.getProjectName();
    }
}
