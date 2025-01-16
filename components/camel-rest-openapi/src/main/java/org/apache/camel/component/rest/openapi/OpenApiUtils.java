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
package org.apache.camel.component.rest.openapi;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.SpecVersion;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.apache.camel.CamelContext;
import org.apache.camel.StartupStep;
import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.camel.spi.StartupStepRecorder;
import org.apache.camel.support.PluginHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenApiUtils {

    private static final Logger LOG = LoggerFactory.getLogger(OpenApiUtils.class);

    private static final Map<String, Class<?>> OPENAPI_TYPES = Map.of(
            "integer", Integer.class,
            "number", Double.class,
            "string", String.class,
            "boolean", Boolean.class);

    private static final Map<String, Class<?>> OPENAPI_FORMATS = Map.of(
            "int32", Integer.class,
            "int64", Long.class,
            "float", Float.class,
            "double", Double.class,
            "byte", byte[].class,
            "binary", byte[].class,
            "date", LocalDate.class,
            "date-time", LocalDateTime.class);

    private static final Pattern CLASS_NAME_PATTERN = Pattern.compile(".*\\/(.*)");

    private final AtomicBoolean packageScanInit = new AtomicBoolean();
    private final Set<Class<?>> scannedClasses = new HashSet<>();
    private CamelContext camelContext;
    private String bindingPackage;
    private Components components;

    public OpenApiUtils(CamelContext camelContext, String bindingPackage, Components components) {
        this.camelContext = camelContext;
        this.bindingPackage = bindingPackage;
        this.components = components;
    }

    public boolean isRequiredBody(Operation operation) {
        return operation.getRequestBody() != null && Boolean.TRUE == operation.getRequestBody().getRequired();
    }

    public String getConsumes(Operation operation) {
        // the operation may have specific information what it can consume
        if (operation.getRequestBody() != null) {
            Content content = operation.getRequestBody().getContent();
            if (content != null) {
                return content.keySet().stream().sorted().collect(Collectors.joining(","));
            }
        }
        return null;
    }

    public String getProduces(Operation operation) {
        // the operation may have specific information what it can produce
        if (operation.getResponses() != null) {
            HashSet<String> mediaTypes = new HashSet<>();
            for (var apiResponse : operation.getResponses().values()) {
                Content content = apiResponse.getContent();
                if (content != null) {
                    mediaTypes.addAll(content.keySet());
                }
            }

            if (!mediaTypes.isEmpty()) {
                return mediaTypes.stream().sorted().collect(Collectors.joining(","));
            }
        }
        return null;
    }

    public Set<String> getRequiredQueryParameters(Operation operation) {
        return getRequiredParameters(operation, "query");
    }

    public Set<String> getRequiredHeaders(Operation operation) {
        return getRequiredParameters(operation, "header");
    }

    public Map<String, String> getQueryParametersDefaultValue(Operation operation) {
        Map<String, String> defaultValues = null;
        if (operation.getParameters() != null) {
            defaultValues = operation.getParameters().stream()
                    .filter(p -> "query".equals(p.getIn()))
                    .filter(p -> p.getSchema() != null)
                    .filter(p -> p.getSchema().getDefault() != null)
                    .collect(Collectors.toMap(Parameter::getName, p -> p.getSchema().getDefault().toString()));
        }
        return defaultValues;
    }

    public String manageRequestBody(Operation operation) {
        if (operation.getRequestBody() != null) {
            Content content = operation.getRequestBody().getContent();
            return findClass(content);
        }
        return null;
    }

    public String manageResponseBody(Operation operation) {
        if (operation.getResponses() != null) {
            for (var a : operation.getResponses().values()) {
                Content content = a.getContent();
                String className = findClass(content);
                if (className != null) {
                    return className;
                }
            }
        }
        return null;
    }

    public void clear() {
        this.scannedClasses.clear();
    }

    private String findClass(Content content) {
        if (content != null) {
            for (var mediaTypeEntry : content.entrySet()) {
                Class<?> clazz = loadBindingClass(mediaTypeEntry);
                if (clazz != null) {
                    return resolveClassName(mediaTypeEntry.getValue().getSchema(), clazz);
                }
            }
        }
        return null;
    }

    private Class<?> loadBindingClass(Map.Entry<String, MediaType> mediaType) {
        scan();
        String mediaTypeName = mediaType.getKey();
        Schema<?> schema = mediaType.getValue().getSchema();

        if (mediaTypeName.contains("xml") && schema.getXml() != null) {
            return loadBindingClassForXml(schema);
        } else if (mediaTypeName.contains("json")) {
            return loadBindingClassForJson(schema);
        }

        // class not found
        return null;
    }

    private Class<?> loadBindingClassForXml(Schema<?> schema) {
        String ref = schema.getXml().getName();
        // must refer to a class name, so upper case
        ref = Character.toUpperCase(ref.charAt(0)) + ref.substring(1);
        // find class via simple name
        for (Class<?> clazz : scannedClasses) {
            if (clazz.getSimpleName().equals(ref)) {
                return clazz;
            }
        }
        return null;
    }

    private Class<?> loadBindingClassForJson(Schema<?> schema) {
        if (isArrayType(schema)) {
            schema = schema.getItems();
        }
        String schemaName = findSchemaName(schema);
        if (schemaName == null) {
            Class<?> primitiveType = resolvePrimitiveType(schema);
            if (primitiveType != null) {
                return primitiveType;
            }
        }

        schemaName = Optional.ofNullable(schemaName)
                .orElse(Optional.ofNullable(schema.get$ref()).orElse(getSchemaType(schema)));
        Matcher classNameMatcher = CLASS_NAME_PATTERN.matcher(schemaName);
        String classToFind = classNameMatcher.find()
                ? classNameMatcher.group(1)
                : schemaName;

        String schemaTitle = schema.getTitle();
        return scannedClasses.stream()
                .filter(aClass -> aClass.getSimpleName().equals(classToFind) || aClass.getSimpleName().equals(schemaTitle)) //use either the name or title of schema to find the class
                .findFirst()
                .orElse(null);
    }

    public boolean isArrayType(Schema<?> schema) {
        if (schema.getSpecVersion() == SpecVersion.V30) {
            return schema instanceof ArraySchema;
        }
        return "array".equals(schema.getTypes().stream().findFirst().orElse(null));
    }

    private String getSchemaType(Schema<?> schema) {
        if (schema.getSpecVersion() == SpecVersion.V30) {
            return schema.getType();
        }
        return schema.getTypes() == null ? null : schema.getTypes().stream().findFirst().orElse(null);
    }

    private String resolveClassName(Schema<?> schema, Class<?> clazz) {
        if (isArrayType(schema)) {
            return clazz.getName().concat("[]");
        }
        return clazz.getName();
    }

    private Class<?> resolvePrimitiveType(Schema<?> schema) {
        String type = getSchemaType(schema);
        if (type == null) {
            return null;
        }
        String format = schema.getFormat();
        Class<?> classType = OPENAPI_TYPES.get(type);
        if (format != null) {
            classType = OPENAPI_FORMATS.get(format);
            if (classType == null) {
                return String.class;
            }
        }
        return classType;
    }

    private void scan() {
        if (packageScanInit.compareAndSet(false, true)) {
            if (bindingPackage != null) {
                StartupStepRecorder recorder = camelContext.getCamelContextExtension().getStartupStepRecorder();
                StartupStep step = recorder.beginStep(RestOpenApiProcessor.class, "openapi-binding",
                        "OpenAPI binding classes package scan");
                String[] pcks = bindingPackage.split(",");
                PackageScanClassResolver resolver = PluginHelper.getPackageScanClassResolver(camelContext);
                // just add all classes as the POJOs can be generated with all kind of tools and with and without annotations
                scannedClasses.addAll(resolver.findImplementations(Object.class, pcks));
                if (!scannedClasses.isEmpty()) {
                    LOG.info("Binding package scan found {} classes in packages: {}", scannedClasses.size(), bindingPackage);
                }
                recorder.endStep(step);
            }
        }
    }

    private Set<String> getRequiredParameters(Operation operation, String type) {
        Set<String> parameters = null;
        if (operation.getParameters() != null) {
            parameters = operation.getParameters().stream()
                    .filter(parameter -> type.equals(parameter.getIn()))
                    .filter(parameter -> Boolean.TRUE == parameter.getRequired())
                    .map(Parameter::getName)
                    .collect(Collectors.toSet());
        }
        return parameters;
    }

    private String findSchemaName(Schema<?> schema) {
        if (components != null) {
            for (Map.Entry<String, Schema> schemaEntry : components.getSchemas().entrySet()) {
                if (schemaEntry.getValue() == schema) {
                    return schemaEntry.getKey();
                }
            }
        }
        return null;
    }
}
