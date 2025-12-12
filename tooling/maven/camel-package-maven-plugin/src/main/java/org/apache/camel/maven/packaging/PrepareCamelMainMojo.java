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
package org.apache.camel.maven.packaging;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.camel.spi.Metadata;
import org.apache.camel.tooling.model.JsonMapper;
import org.apache.camel.tooling.model.MainModel;
import org.apache.camel.tooling.model.MainModel.MainGroupModel;
import org.apache.camel.tooling.util.JavadocHelper;
import org.apache.camel.tooling.util.PackageHelper;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.build.BuildContext;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.AnnotationSource;
import org.jboss.forge.roaster.model.source.FieldSource;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.MethodSource;

/**
 * Prepares camel-main by generating Camel Main configuration metadata for tooling support.
 */
@Mojo(name = "prepare-main-doc", defaultPhase = LifecyclePhase.PROCESS_CLASSES, threadSafe = true,
      requiresDependencyResolution = ResolutionScope.COMPILE)
public class PrepareCamelMainMojo extends AbstractGeneratorMojo {

    /**
     * The output directory for the generated spring boot tooling files
     */
    @Parameter(defaultValue = "${project.basedir}/src/main/doc")
    protected File outFolder;

    @Inject
    public PrepareCamelMainMojo(MavenProjectHelper projectHelper, BuildContext buildContext) {
        super(projectHelper, buildContext);
    }

    /**
     * Parses the Camel Main configuration java source file.
     */
    public static List<MainModel.MainOptionModel> parseConfigurationSource(String fileName) throws IOException {
        return parseConfigurationSource(new File(fileName));
    }

    /**
     * Parses the Camel Main configuration java source file.
     */
    public static List<MainModel.MainOptionModel> parseConfigurationSource(File file) throws IOException {
        final List<MainModel.MainOptionModel> answer = new ArrayList<>();

        JavaClassSource clazz = (JavaClassSource) Roaster.parse(file);
        List<FieldSource<JavaClassSource>> fields = clazz.getFields();
        // filter out final or static fields
        fields = fields.stream().filter(f -> !f.isFinal() && !f.isStatic()).collect(Collectors.toList());
        fields.forEach(f -> {
            AnnotationSource<?> as = f.getAnnotation(Metadata.class);
            String name = f.getName();
            String javaType = f.getType().getQualifiedName();
            String sourceType = clazz.getQualifiedName();
            String defaultValue = f.getStringInitializer();
            boolean secret = false;
            boolean required = false;
            if (as != null) {
                defaultValue = as.getStringValue("defaultValue");
                secret = "true".equals(as.getStringValue("secret"));
                required = "true".equals(as.getStringValue("required"));
            }
            if (defaultValue != null && defaultValue.startsWith("new ")) {
                // skip constructors
                defaultValue = null;
            }

            // the field must have a setter
            String setterName = "set" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
            MethodSource<?> setter = clazz.getMethod(setterName, javaType);
            if (setter != null) {
                String desc = setter.getJavaDoc().getFullText();
                boolean deprecated
                        = clazz.getAnnotation(Deprecated.class) != null || setter.getAnnotation(Deprecated.class) != null;
                MainModel.MainOptionModel model = new MainModel.MainOptionModel();
                model.setName(name);
                model.setJavaType(javaType);
                model.setDescription(JavadocHelper.sanitizeDescription(desc, false));
                model.setSourceType(sourceType);
                model.setDeprecated(deprecated);
                model.setSecret(secret);
                model.setRequired(required);
                List<String> enums = null;
                // add known enums
                if ("org.apache.camel.LoggingLevel".equals(javaType)) {
                    enums = Arrays.asList("ERROR,WARN,INFO,DEBUG,TRACE,OFF".split(","));
                } else if ("org.apache.camel.ManagementStatisticsLevel".equals(javaType)) {
                    enums = Arrays.asList("Extended,Default,RoutesOnly,Off".split(","));
                } else if ("org.apache.camel.spi.RestBindingMode".equals(javaType)) {
                    enums = Arrays.asList("auto,off,json,xml,json_xml".split(","));
                } else if ("org.apache.camel.spi.RestHostNameResolver".equals(javaType)) {
                    enums = Arrays.asList("allLocalIp,localIp,localHostName".split(","));
                } else if ("org.apache.camel.util.concurrent.ThreadPoolRejectedPolicy".equals(javaType)) {
                    enums = Arrays.asList("Abort,CallerRuns,DiscardOldest,Discard".split(","));
                }
                if (enums == null && as != null) {
                    String text = as.getStringValue("enums");
                    if (text != null) {
                        enums = Arrays.asList(text.split(","));
                    }
                }
                model.setEnums(enums);
                String type = MojoHelper.getType(javaType, enums != null && !enums.isEmpty(), false);
                model.setType(type);
                model.setDefaultValue(asDefaultValue(type, defaultValue));
                answer.add(model);
            }
        });

        return answer;
    }

    private static Object asDefaultValue(String type, String defaultValue) {
        if (defaultValue != null) {
            if ("boolean".equals(type)) {
                return Boolean.parseBoolean(defaultValue);
            } else if ("integer".equals(type)) {
                return Integer.parseInt(defaultValue);
            }
        }
        if (defaultValue == null && "boolean".equals(type)) {
            return "false";
        }
        return defaultValue;
    }

    @Override
    public void execute(MavenProject project) throws MojoFailureException, MojoExecutionException {
        outFolder = new File(project.getBasedir(), "src/generated/resources");
        super.execute(project);
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        // scan for configuration files
        File[] files = new File(project.getBasedir(), "src/main/java/org/apache/camel/main")
                .listFiles(f -> f.isFile() && f.getName().endsWith("Properties.java"));
        if (files == null || files.length == 0) {
            return;
        }

        final List<MainModel.MainOptionModel> data = new ArrayList<>();

        for (File file : files) {
            getLog().info("Parsing Camel Main configuration file: " + file);
            try {
                List<MainModel.MainOptionModel> model = parseConfigurationSource(file);
                // compute prefix for name
                String prefix;
                if (file.getName().contains("Resilience")) {
                    prefix = "camel.resilience4j.";
                } else if (file.getName().contains("FaultTolerance")) {
                    prefix = "camel.faulttolerance.";
                } else if (file.getName().contains("Rest")) {
                    prefix = "camel.rest.";
                } else if (file.getName().contains("AwsVault")) {
                    prefix = "camel.vault.aws.";
                } else if (file.getName().contains("GcpVault")) {
                    prefix = "camel.vault.gcp.";
                } else if (file.getName().contains("AzureVault")) {
                    prefix = "camel.vault.azure.";
                } else if (file.getName().contains("KubernetesVault")) {
                    prefix = "camel.vault.kubernetes.";
                } else if (file.getName().contains("KubernetesConfigMapVault")) {
                    prefix = "camel.vault.kubernetescm.";
                } else if (file.getName().contains("HashicorpVault")) {
                    prefix = "camel.vault.hashicorp.";
                } else if (file.getName().contains("IBMSecretsManagerVault")) {
                    prefix = "camel.vault.ibm.";
                } else if (file.getName().contains("SpringCloudConfig")) {
                    prefix = "camel.vault.springConfig.";
                } else if (file.getName().contains("CyberArkVault")) {
                    prefix = "camel.vault.cyberark.";
                } else if (file.getName().contains("Health")) {
                    prefix = "camel.health.";
                } else if (file.getName().contains("StartupCondition")) {
                    prefix = "camel.startupcondition.";
                } else if (file.getName().contains("Lra")) {
                    prefix = "camel.lra.";
                } else if (file.getName().contains("Otel2")) {
                    prefix = "camel.opentelemetry2.";
                } else if (file.getName().contains("Otel") && !file.getName().contains("Otel2")) {
                    prefix = "camel.opentelemetry.";
                } else if (file.getName().contains("TelemetryDev")) {
                    prefix = "camel.telemetryDev.";
                } else if (file.getName().contains("MdcConfigurationProperties")) {
                    prefix = "camel.mdc.";
                } else if (file.getName().contains("Metrics")) {
                    prefix = "camel.metrics.";
                } else if (file.getName().contains("HttpServer")) {
                    prefix = "camel.server.";
                } else if (file.getName().contains("HttpManagementServer")) {
                    prefix = "camel.management.";
                } else if (file.getName().contains("ThreadPoolProfileConfigurationProperties")) {
                    // skip this file
                    continue;
                } else if (file.getName().contains("ThreadPoolConfigurationProperties")) {
                    prefix = "camel.threadpool.";
                } else if (file.getName().contains("SSLConfigurationProperties")) {
                    prefix = "camel.ssl.";
                } else if (file.getName().contains("DebuggerConfigurationProperties")) {
                    prefix = "camel.debug.";
                } else if (file.getName().contains("TracerConfigurationProperties")) {
                    prefix = "camel.trace.";
                } else if (file.getName().contains("RouteControllerConfigurationProperties")) {
                    prefix = "camel.routecontroller.";
                } else {
                    prefix = "camel.main.";
                }
                final String namePrefix = prefix;
                model.forEach(m -> m.setName(namePrefix + m.getName()));
                data.addAll(model);
            } catch (Exception e) {
                throw new MojoFailureException("Error parsing file " + file + " due " + e.getMessage(), e);
            }
        }

        // include additional rest configuration from camel-api
        File camelApiDir = PackageHelper.findCamelDirectory(project.getBasedir(), "core/camel-api");
        File restConfig = new File(camelApiDir, "src/main/java/org/apache/camel/spi/RestConfiguration.java");
        try {
            List<MainModel.MainOptionModel> model = parseConfigurationSource(restConfig);
            model.forEach(m -> m.setName("camel.rest." + m.getName()));
            data.addAll(model);
        } catch (Exception e) {
            throw new MojoFailureException("Error parsing file " + restConfig + " due " + e.getMessage(), e);
        }
        // include additional vault configuration from camel-api
        // TODO: add more vault providers here
        File awsVaultConfig = new File(camelApiDir, "src/main/java/org/apache/camel/vault/AwsVaultConfiguration.java");
        try {
            List<MainModel.MainOptionModel> model = parseConfigurationSource(awsVaultConfig);
            model.forEach(m -> m.setName("camel.vault.aws." + m.getName()));
            data.addAll(model);
        } catch (Exception e) {
            throw new MojoFailureException("Error parsing file " + awsVaultConfig + " due " + e.getMessage(), e);
        }

        File gcpVaultConfig = new File(camelApiDir, "src/main/java/org/apache/camel/vault/GcpVaultConfiguration.java");
        try {
            List<MainModel.MainOptionModel> model = parseConfigurationSource(gcpVaultConfig);
            model.forEach(m -> m.setName("camel.vault.gcp." + m.getName()));
            data.addAll(model);
        } catch (Exception e) {
            throw new MojoFailureException("Error parsing file " + gcpVaultConfig + " due " + e.getMessage(), e);
        }

        File azureVaultConfig = new File(camelApiDir, "src/main/java/org/apache/camel/vault/AzureVaultConfiguration.java");
        try {
            List<MainModel.MainOptionModel> model = parseConfigurationSource(azureVaultConfig);
            model.forEach(m -> m.setName("camel.vault.azure." + m.getName()));
            data.addAll(model);
        } catch (Exception e) {
            throw new MojoFailureException("Error parsing file " + azureVaultConfig + " due " + e.getMessage(), e);
        }

        File kubernetesVaultConfig
                = new File(camelApiDir, "src/main/java/org/apache/camel/vault/KubernetesVaultConfiguration.java");
        try {
            List<MainModel.MainOptionModel> model = parseConfigurationSource(kubernetesVaultConfig);
            model.forEach(m -> m.setName("camel.vault.kubernetes." + m.getName()));
            data.addAll(model);
        } catch (Exception e) {
            throw new MojoFailureException("Error parsing file " + kubernetesVaultConfig + " due " + e.getMessage(), e);
        }

        File springCloudConfigConfig
                = new File(camelApiDir, "src/main/java/org/apache/camel/vault/SpringCloudConfigConfiguration.java");
        try {
            List<MainModel.MainOptionModel> model = parseConfigurationSource(springCloudConfigConfig);
            model.forEach(m -> m.setName("camel.vault.springConfig." + m.getName()));
            data.addAll(model);
        } catch (Exception e) {
            throw new MojoFailureException("Error parsing file " + springCloudConfigConfig + " due " + e.getMessage(), e);
        }

        File kubernetesConfigmapsVaultConfig
                = new File(camelApiDir, "src/main/java/org/apache/camel/vault/KubernetesConfigMapVaultConfiguration.java");
        try {
            List<MainModel.MainOptionModel> model = parseConfigurationSource(kubernetesConfigmapsVaultConfig);
            model.forEach(m -> m.setName("camel.vault.kubernetescm." + m.getName()));
            data.addAll(model);
        } catch (Exception e) {
            throw new MojoFailureException(
                    "Error parsing file " + kubernetesConfigmapsVaultConfig + " due " + e.getMessage(), e);
        }

        File hashicorpVaultConfig
                = new File(camelApiDir, "src/main/java/org/apache/camel/vault/HashicorpVaultConfiguration.java");
        try {
            List<MainModel.MainOptionModel> model = parseConfigurationSource(hashicorpVaultConfig);
            model.forEach(m -> m.setName("camel.vault.hashicorp." + m.getName()));
            data.addAll(model);
        } catch (Exception e) {
            throw new MojoFailureException("Error parsing file " + hashicorpVaultConfig + " due " + e.getMessage(), e);
        }

        File ibmVaultConfig
                = new File(camelApiDir, "src/main/java/org/apache/camel/vault/IBMSecretsManagerVaultConfiguration.java");
        try {
            List<MainModel.MainOptionModel> model = parseConfigurationSource(ibmVaultConfig);
            model.forEach(m -> m.setName("camel.vault.ibm." + m.getName()));
            data.addAll(model);
        } catch (Exception e) {
            throw new MojoFailureException("Error parsing file " + ibmVaultConfig + " due " + e.getMessage(), e);
        }

        File cyberarkVaultConfig
                = new File(camelApiDir, "src/main/java/org/apache/camel/vault/CyberArkVaultConfiguration.java");
        try {
            List<MainModel.MainOptionModel> model = parseConfigurationSource(cyberarkVaultConfig);
            model.forEach(m -> m.setName("camel.vault.cyberark." + m.getName()));
            data.addAll(model);
        } catch (Exception e) {
            throw new MojoFailureException("Error parsing file " + cyberarkVaultConfig + " due " + e.getMessage(), e);
        }

        // lets sort so they are always ordered (but camel.main in top)
        data.sort((o1, o2) -> {
            if (o1.getName().startsWith("camel.main.") && !o2.getName().startsWith("camel.main.")) {
                return -1;
            } else if (!o1.getName().startsWith("camel.main.") && o2.getName().startsWith("camel.main.")) {
                return 1;
            } else {
                return o1.getName().compareToIgnoreCase(o2.getName());
            }
        });

        if (!data.isEmpty()) {
            MainModel model = new MainModel();
            model.getOptions().addAll(data);
            model.getGroups().add(new MainGroupModel(
                    "camel.main", "Camel Main configurations", "org.apache.camel.main.DefaultConfigurationProperties"));
            model.getGroups().add(new MainGroupModel(
                    "camel.startupcondition", "Camel Startup Condition configurations",
                    "org.apache.camel.main.StartupConditionConfigurationProperties"));
            model.getGroups()
                    .add(new MainGroupModel(
                            "camel.routecontroller", "Camel Route Controller configurations",
                            "org.apache.camel.main.RouteControllerConfigurationProperties"));
            model.getGroups().add(new MainGroupModel(
                    "camel.server",
                    "Camel Embedded HTTP Server (only for standalone; not Spring Boot or Quarkus) configurations",
                    "org.apache.camel.main.HttpServerConfigurationProperties"));
            model.getGroups().add(new MainGroupModel(
                    "camel.management",
                    "Camel Embedded HTTP management Server (only for standalone; not Spring Boot or Quarkus) configurations",
                    "org.apache.camel.main.HttpManagementServerConfigurationProperties"));
            model.getGroups()
                    .add(new MainGroupModel(
                            "camel.debug", "Camel Debugger configurations",
                            "org.apache.camel.main.DebuggerConfigurationProperties"));
            model.getGroups()
                    .add(new MainGroupModel(
                            "camel.trace", "Camel Tracer configurations",
                            "org.apache.camel.main.TracerConfigurationProperties"));
            model.getGroups()
                    .add(new MainGroupModel(
                            "camel.ssl", "Camel SSL configurations",
                            "org.apache.camel.main.SSLConfigurationProperties"));
            model.getGroups()
                    .add(new MainGroupModel(
                            "camel.threadpool", "Camel Thread Pool configurations",
                            "org.apache.camel.main.ThreadPoolConfigurationProperties"));
            model.getGroups().add(new MainGroupModel(
                    "camel.health", "Camel Health Check configurations",
                    "org.apache.camel.main.HealthConfigurationProperties"));
            model.getGroups().add(
                    new MainGroupModel(
                            "camel.rest", "Camel Rest-DSL configurations", "org.apache.camel.spi.RestConfiguration"));
            model.getGroups().add(
                    new MainGroupModel(
                            "camel.vault.aws", "Camel AWS Vault configurations",
                            "org.apache.camel.vault.AwsVaultConfiguration"));
            model.getGroups().add(
                    new MainGroupModel(
                            "camel.vault.gcp", "Camel GCP Vault configurations",
                            "org.apache.camel.vault.GcpVaultConfiguration"));
            model.getGroups().add(
                    new MainGroupModel(
                            "camel.vault.azure", "Camel Azure Key Vault configurations",
                            "org.apache.camel.vault.AzureVaultConfiguration"));
            model.getGroups().add(
                    new MainGroupModel(
                            "camel.vault.kubernetes", "Camel Kubernetes Vault configurations",
                            "org.apache.camel.vault.KubernetesVaultConfiguration"));
            model.getGroups().add(
                    new MainGroupModel(
                            "camel.vault.kubernetescm", "Camel Kubernetes Configmaps Vault configurations",
                            "org.apache.camel.vault.KubernetesConfigMapVaultConfiguration"));
            model.getGroups().add(
                    new MainGroupModel(
                            "camel.vault.hashicorp", "Camel Hashicorp Vault configurations",
                            "org.apache.camel.vault.HashicorpVaultConfiguration"));
            model.getGroups().add(
                    new MainGroupModel(
                            "camel.vault.ibm", "Camel IBM Secrets Manager Vault configurations",
                            "org.apache.camel.vault.IBMSecretsManagerVaultConfiguration"));
            model.getGroups().add(
                    new MainGroupModel(
                            "camel.vault.cyberark", "Camel CyberArk Conjur Vault configurations",
                            "org.apache.camel.vault.CyberArkVaultConfiguration"));
            model.getGroups().add(new MainGroupModel(
                    "camel.opentelemetry", "Camel OpenTelemetry configurations",
                    "org.apache.camel.main.OtelConfigurationProperties"));
            model.getGroups().add(new MainGroupModel(
                    "camel.opentelemetry2", "Camel OpenTelemetry 2 configurations",
                    "org.apache.camel.main.Otel2ConfigurationProperties"));
            model.getGroups().add(new MainGroupModel(
                    "camel.telemetryDev", "Camel Telemetry Dev configurations",
                    "org.apache.camel.main.TelemetryDevConfigurationProperties"));
            model.getGroups().add(new MainGroupModel(
                    "camel.mdc", "Camel MDC configurations",
                    "org.apache.camel.main.MdcConfigurationProperties"));
            model.getGroups().add(new MainGroupModel(
                    "camel.metrics", "Camel Micrometer Metrics configurations",
                    "org.apache.camel.main.MetricsConfigurationProperties"));
            model.getGroups()
                    .add(new MainGroupModel(
                            "camel.faulttolerance", "Fault Tolerance EIP Circuit Breaker configurations",
                            "org.apache.camel.main.FaultToleranceConfigurationProperties"));
            model.getGroups()
                    .add(new MainGroupModel(
                            "camel.resilience4j", "Resilience4j EIP Circuit Breaker configurations",
                            "org.apache.camel.main.Resilience4jConfigurationProperties"));
            model.getGroups().add(new MainGroupModel(
                    "camel.lra", "Camel Saga EIP (Long Running Actions) configurations",
                    "org.apache.camel.main.LraConfigurationProperties"));

            String json = JsonMapper.createJsonSchema(model);

            updateResource(outFolder.toPath(), "META-INF/camel-main-configuration-metadata.json", json);
        }
    }

}
