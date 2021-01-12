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
package org.apache.camel.maven.component.vertx.kafka;

import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.apache.camel.maven.component.vertx.kafka.config.ConfigField;
import org.apache.camel.maven.component.vertx.kafka.config.ConfigFieldsBuilder;
import org.apache.camel.maven.component.vertx.kafka.config.ConfigJavaClass;
import org.apache.camel.tooling.util.FileUtil;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "generate-kafka-config", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class GenerateKafkaConfigMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project.build.directory}/generated-sources/configuration")
    private File generatedSrcDir;

    /**
     * The generated configuration class name
     */
    @Parameter(defaultValue = "KafkaConfiguration", required = true)
    private String className;

    /**
     * The generated configuration package name
     */
    @Parameter(defaultValue = "org.apache.camel.component.vertx.kafka.configuration", required = true)
    private String packageName;

    /**
     * The parent class name in case the generated configuration class needs to extend a parent class
     */
    @Parameter
    private String parentClassName;

    /**
     * Fields names that need to be required
     */
    @Parameter
    private Set<String> requiredFields = Collections.emptySet();

    /**
     * Fields names that need to be deprecated
     */
    @Parameter
    private Set<String> deprecatedFields = Collections.emptySet();

    /**
     * Fields names that need to be skipped (not to be generated)
     */
    @Parameter
    private Set<String> skippedFields = Collections.emptySet();

    /**
     * Fields that need to be annotated with @UriPath in Camel
     */
    @Parameter
    private Set<String> uriPathFields = Collections.emptySet();

    /**
     * Map of fields raw name -> overridden value for this field
     */
    @Parameter
    private Map<String, Object> overriddenDefaultValues = Collections.emptyMap();

    /**
     * Map of fields raw name -> overridden generated variable name for this field. e.g: topic -> awesomeTopic
     */
    @Parameter
    private Map<String, String> overriddenVariableNames = Collections.emptyMap();

    /**
     * Map of fields that need to be additionally added and can be used by both, camel producer and camel consumer
     */
    @Parameter
    private Map<String, ConfigField> additionalCommonConfigs = Collections.emptyMap();

    /**
     * Map of fields that need to be additionally added and can be only used by camel consumer
     */
    @Parameter
    private Map<String, ConfigField> additionalConsumerConfigs = Collections.emptyMap();

    /**
     * Map of fields that need to be additionally added and can be only used by camel producer
     */
    @Parameter
    private Map<String, ConfigField> additionalProducerConfigs = Collections.emptyMap();

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        final Map<String, ConfigField> consumerConfigs = new ConfigFieldsBuilder()
                .fromConfigKeys(ConsumerConfig.configDef().configKeys())
                .addConfig(additionalConsumerConfigs)
                .addConfig(additionalCommonConfigs)
                .setUriPathFields(uriPathFields)
                .setRequiredFields(requiredFields)
                .setSkippedFields(skippedFields)
                .setDeprecatedFields(deprecatedFields)
                .setOverriddenVariableNames(overriddenVariableNames)
                .setOverriddenDefaultValues(overriddenDefaultValues)
                .build();

        final Map<String, ConfigField> producerConfigs = new ConfigFieldsBuilder()
                .fromConfigKeys(ProducerConfig.configDef().configKeys())
                .addConfig(additionalProducerConfigs)
                .addConfig(additionalCommonConfigs)
                .setUriPathFields(uriPathFields)
                .setRequiredFields(requiredFields)
                .setSkippedFields(skippedFields)
                .setDeprecatedFields(deprecatedFields)
                .setOverriddenVariableNames(overriddenVariableNames)
                .setOverriddenDefaultValues(overriddenDefaultValues)
                .build();

        try {
            final ConfigJavaClass configJavaClass = ConfigJavaClass.builder()
                    .withClassName(className)
                    .withPackageName(packageName)
                    .withParentClassName(parentClassName)
                    .withConsumerConfigs(consumerConfigs)
                    .withProducerConfigs(producerConfigs)
                    .build();

            final File parentPath = new File(generatedSrcDir, packageName.replace(".", "/"));
            final File configClassFile = new File(parentPath, className + ".java");
            if (!configClassFile.exists()) {
                configClassFile.getParentFile().mkdirs();
                configClassFile.createNewFile();
            }
            FileUtil.updateFile(configClassFile.toPath(), configJavaClass.printClassAsString());
        } catch (Exception e) {
            throw new MojoFailureException(e.getMessage(), e);
        }
    }
}
