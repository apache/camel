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
     * The output directory for generated spring boot tooling file
     */
    @Parameter(defaultValue = "${project.basedir}/src/main/doc")
    protected File outFolder;

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
            if (as != null) {
                defaultValue = as.getStringValue("defaultValue");
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
                String type = fromMainToType(javaType);
                MainModel.MainOptionModel model = new MainModel.MainOptionModel();
                model.setName(name);
                model.setType(type);
                model.setJavaType(javaType);
                model.setDescription(JavadocHelper.sanitizeDescription(desc, false));
                model.setSourceType(sourceType);
                model.setDefaultValue(asDefaultValue(type, defaultValue));
                model.setDeprecated(deprecated);
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
                answer.add(model);
            }
        });

        return answer;
    }

    private static String fromMainToType(String type) {
        if ("boolean".equals(type) || "java.lang.Boolean".equals(type)) {
            return "boolean";
        } else if ("int".equals(type) || "java.lang.Integer".equals(type)) {
            return "integer";
        } else if ("long".equals(type) || "java.lang.Long".equals(type)) {
            return "integer";
        } else if ("float".equals(type) || "java.lang.Float".equals(type)) {
            return "number";
        } else if ("double".equals(type) || "java.lang.Double".equals(type)) {
            return "number";
        } else if ("string".equals(type) || "java.lang.String".equals(type)) {
            return "string";
        } else {
            return "object";
        }
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
    public void execute(MavenProject project, MavenProjectHelper projectHelper, BuildContext buildContext)
            throws MojoFailureException, MojoExecutionException {
        outFolder = new File(project.getBasedir(), "src/generated/resources");
        super.execute(project, projectHelper, buildContext);
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
                    // TODO: add more vault providers here
                } else if (file.getName().contains("Health")) {
                    prefix = "camel.health.";
                } else if (file.getName().contains("Lra")) {
                    prefix = "camel.lra.";
                } else if (file.getName().contains("HttpServer")) {
                    prefix = "camel.server.";
                } else if (file.getName().contains("ThreadPoolProfileConfigurationProperties")) {
                    // skip this file
                    continue;
                } else if (file.getName().contains("ThreadPoolConfigurationProperties")) {
                    prefix = "camel.threadpool.";
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
                    "camel.server",
                    "Camel Embedded HTTP Server (only for standalone; not Spring Boot or Quarkus) configurations",
                    "org.apache.camel.main.HttpServerConfigurationProperties"));
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
            // TODO: add more vault providers here
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
