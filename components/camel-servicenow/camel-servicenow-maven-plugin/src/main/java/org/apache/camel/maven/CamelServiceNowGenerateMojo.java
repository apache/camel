/**
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
package org.apache.camel.maven;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Generated;
import javax.lang.model.element.Modifier;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import org.apache.camel.component.extension.MetaDataExtension;
import org.apache.camel.component.servicenow.ServiceNowComponent;
import org.apache.camel.component.servicenow.annotations.ServiceNowSysParm;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Goal to generate DTOs for ServiceNow objects
 */
@Mojo(name = "generate", requiresProject = false, defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class CamelServiceNowGenerateMojo extends AbstractMojo {
    /**
     * Location of generated DTO files, defaults to target/generated-sources/camel-salesforce.
     */
    @Parameter(
        property = "camel.servicenow.output.directory",
        defaultValue = "${project.build.directory}/generated-sources/camel-servicenow")
    protected File outputDirectory;

    /**
     * Java package name for generated DTOs.
     */
    @Parameter(
        property = "camel.servicenow.output.package",
        defaultValue = "org.apache.camel.servicenow.dto")
    protected String packageName;

    /**
     * ServiceNow instance name.
     */
    @Parameter(
        property = "camel.servicenow.instance.name", required = true)
    protected String instanceName;

    /**
     * ServiceName user name.
     */
    @Parameter(
        property = "camel.servicenow.user.name", required = true)
    protected String userName;

    /**
     * ServiceNow user password.
     */
    @Parameter(
        property = "camel.servicenow.user.password", required = true)
    protected String userPassword;

    /**
     * ServiceNow OAuth2 client id.
     */
    @Parameter(
        property = "camel.servicenow.oauth2.client.id")
    protected String oauthClientId;

    /**
     * ServiceNow OAuth2 client secret.
     */
    @Parameter(
        property = "camel.servicenow.oauth2.client.secret")
    protected String oauthClientSecret;

    /**
     * SSL Context parameters.
     */
    @Parameter(
        property = "camel.servicenow.ssl.parameters")
    protected SSLContextParameters sslParameters;

    /**
     * ServiceNow objects for which DTOs must be generated.
     */
    @Parameter(required = true)
    protected List<String> objects = Collections.emptyList();

    /**
     * ServiceNow fields to include in generated DTOs.
     */
    @Parameter
    protected Map<String, String> fields = Collections.emptyMap();

    /**
     * ServiceNow fields to exclude from generated DTOs, fields explicit included
     * have the precedence.
     */
    @Parameter
    protected Map<String, String> fieldsExcludePattern = Collections.emptyMap();

    // ************************************
    //
    // ************************************

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final DefaultCamelContext context = new DefaultCamelContext();
        final ServiceNowComponent component = new ServiceNowComponent(context);

        for (String objectName : objects) {
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("instanceName", instanceName);
            parameters.put("userName", userName);
            parameters.put("password", userPassword);
            parameters.put("oauthClientId", oauthClientId);
            parameters.put("oauthClientSecret", oauthClientSecret);
            parameters.put("objectType", "table");
            parameters.put("objectName", objectName);

            for (Map.Entry<String, String> entry : fields.entrySet()) {
                parameters.put("object." + entry.getKey() + ".fields", entry.getValue());
            }
            for (Map.Entry<String, String> entry : fieldsExcludePattern.entrySet()) {
                parameters.put("object." + entry.getKey() + ".fields.exclude.pattern", entry.getValue());
            }

            JsonNode schema = component.getExtension(MetaDataExtension.class)
                .flatMap(e -> e.meta(parameters))
                .flatMap(m -> Optional.ofNullable(m.getPayload(JsonNode.class)))
                .orElseThrow(() -> new MojoExecutionException("Unable to get grab MetaData for object: " + objectName)
            );

            validateSchema(schema);

            generateBean(objectName, schema);
        }
    }

    // ************************************
    // Beans
    // ************************************

    private void generateBean(String name, JsonNode schema) throws MojoExecutionException {
        try {
            TypeSpec.Builder typeBuilder = TypeSpec.classBuilder(toCamelCase(name, false))
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addAnnotation(Generated.class)
                .addAnnotation(AnnotationSpec.builder(ServiceNowSysParm.class)
                    .addMember("name", "$S", "sysparm_exclude_reference_link")
                    .addMember("value", "$S", "true")
                    .build())
                .addAnnotation(AnnotationSpec.builder(JsonIgnoreProperties.class)
                    .addMember("ignoreUnknown", "$L", "true")
                    .build())
                .addAnnotation(AnnotationSpec.builder(JsonInclude.class)
                    .addMember("value", "$L", "JsonInclude.Include.NON_NULL")
                    .build());

            schema.get("properties").fields().forEachRemaining(
                entry -> generateBeanProperty(typeBuilder, schema, entry.getKey(), entry.getValue())
            );

            JavaFile.builder(packageName, typeBuilder.build())
                .indent("    ")
                .build()
                .writeTo(outputDirectory);
        } catch (IOException e) {
            throw new MojoExecutionException("Unable to generate Class", e);
        }
    }

    private void generateBeanProperty(TypeSpec.Builder typeBuilder, JsonNode schema, String name, JsonNode definition) {
        final ArrayNode required = (ArrayNode)schema.get("required");
        final ObjectNode definitions = (ObjectNode)schema.get("definitions");
        final String fieldName = toCamelCase(name, true);
        final String methodName = toCamelCase(name, false);

        Class<?> javaType = String.class;
        Optional<String> format = Optional.empty();

        JsonNode type = definition.get("type");
        if (type != null) {
            if ("boolean".equalsIgnoreCase(type.textValue())) {
                javaType = boolean.class;
            }
            if ("integer".equalsIgnoreCase(type.textValue())) {
                javaType = Integer.class;
            }
            if ("number".equalsIgnoreCase(type.textValue())) {
                javaType = Double.class;
            }
        }

        JsonNode ref = definition.get("$ref");
        if (ref != null) {
            if ("#/definitions/guid".equalsIgnoreCase(ref.textValue())) {
                javaType = String.class;
            }
            if ("#/definitions/date".equalsIgnoreCase(ref.textValue())) {
                javaType = Date.class;
                format = getNodeTextValue(definitions, "date", "format");
            }
            if ("#/definitions/time".equalsIgnoreCase(ref.textValue())) {
                javaType = Date.class;
                format = getNodeTextValue(definitions, "time", "format");
            }
            if ("#/definitions/date-time".equalsIgnoreCase(ref.textValue())) {
                javaType = Date.class;
                format = getNodeTextValue(definitions, "date-time", "format");
            }
        }

        FieldSpec.Builder field = FieldSpec.builder(javaType, toCamelCase(name, true))
            .addModifiers(Modifier.PRIVATE)
            .addAnnotation(AnnotationSpec.builder(JsonProperty.class)
                .addMember("value", "$S", name)
                .addMember("required", "$L", required.has(name))
                .build());

        format.ifPresent(f ->
            field.addAnnotation(AnnotationSpec.builder(JsonFormat.class)
                .addMember("shape", "$L","JsonFormat.Shape.STRING")
                .addMember("pattern", "$S", f)
                .build())
        );

        MethodSpec.Builder getter = MethodSpec.methodBuilder("get" + methodName)
            .addModifiers(Modifier.PUBLIC)
            .returns(javaType)
            .addStatement("return this.$L", fieldName);

        MethodSpec.Builder setter = MethodSpec.methodBuilder("set" + methodName)
            .addModifiers(Modifier.PUBLIC)
            .returns(void.class)
            .addParameter(javaType, fieldName)
            .addStatement("this.$L = $L", fieldName, fieldName);

        typeBuilder.addField(field.build());
        typeBuilder.addMethod(getter.build());
        typeBuilder.addMethod(setter.build());
    }

    // ************************************
    // Helpers
    // ************************************

    private String toCamelCase(String text, boolean lowerCaseFirst) {
        String result = Stream.of(text.split("[^a-zA-Z0-9]"))
            .map(v -> v.substring(0, 1).toUpperCase() + v.substring(1).toLowerCase())
            .collect(Collectors.joining());

        if (lowerCaseFirst) {
            result = result.substring(0, 1).toLowerCase() + result.substring(1);
        }

        return result;
    }

    private Optional<String> getNodeTextValue(JsonNode root, String... path) {
        return getNode(root, path).map(JsonNode::asText);
    }

    private Optional<JsonNode> getNode(JsonNode root, String... path) {
        JsonNode node = root;
        for (String name : path) {
            node = node.get(name);
            if (node == null) {
                break;
            }
        }

        return Optional.ofNullable(node);
    }

    private void validateSchema(JsonNode schema) throws MojoExecutionException {
        getNode(schema, "required")
            .orElseThrow(() -> new MojoExecutionException("Invalid JsonSchema: 'required' element not found"));
        getNode(schema, "definitions")
            .orElseThrow(() -> new MojoExecutionException("Invalid JsonSchema: 'definitions' element not found"));
        getNode(schema, "definitions", "date", "format")
            .orElseThrow(() -> new MojoExecutionException("Invalid JsonSchema: '#/definitions/date' element not found"));
        getNode(schema, "definitions", "time", "format")
            .orElseThrow(() -> new MojoExecutionException("Invalid JsonSchema: '#/definitions/time' element not found"));
        getNode(schema, "definitions", "date-time", "format")
            .orElseThrow(() -> new MojoExecutionException("Invalid JsonSchema: '#/definitions/date-time' element not found"));
    }
}
