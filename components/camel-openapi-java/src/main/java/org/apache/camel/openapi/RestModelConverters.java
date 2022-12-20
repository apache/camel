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
package org.apache.camel.openapi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.datamodels.core.models.Extension;
import io.apicurio.datamodels.openapi.models.OasDocument;
import io.apicurio.datamodels.openapi.models.OasSchema;
import io.apicurio.datamodels.openapi.v2.models.Oas20Document;
import io.apicurio.datamodels.openapi.v2.models.Oas20Schema;
import io.apicurio.datamodels.openapi.v2.models.Oas20SchemaDefinition;
import io.apicurio.datamodels.openapi.v3.models.Oas30Discriminator;
import io.apicurio.datamodels.openapi.v3.models.Oas30Document;
import io.apicurio.datamodels.openapi.v3.models.Oas30Schema;
import io.apicurio.datamodels.openapi.v3.models.Oas30Schema.Oas30AnyOfSchema;
import io.apicurio.datamodels.openapi.v3.models.Oas30Schema.Oas30OneOfSchema;
import io.apicurio.datamodels.openapi.v3.models.Oas30SchemaDefinition;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Discriminator;
import io.swagger.v3.oas.models.media.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Camel extended {@link ModelConverters} where we are appending vendor extensions to include the java class name of
 * the model classes.
 */
@SuppressWarnings("rawtypes")
public class RestModelConverters {

    private static final Logger LOG = LoggerFactory.getLogger(RestModelConverters.class);
    private static final ModelConverters MODEL30_CONVERTERS;

    static {
        MODEL30_CONVERTERS = ModelConverters.getInstance();
        MODEL30_CONVERTERS.addConverter(new ClassNameExtensionModelResolver(new FqnModelResolver()));
    }

    private static final ModelConverters MODEL20_CONVERTERS;

    static {
        MODEL20_CONVERTERS = ModelConverters.getInstance();
        MODEL20_CONVERTERS.addConverter(new ClassNameExtensionModelResolver());
    }

    public List<? extends OasSchema> readClass(OasDocument oasDocument, Class<?> clazz) {
        if (clazz.equals(java.io.File.class)) {
            // File is a special type in OAS2 / OAS3 (no model)
            return null;
        } else if (oasDocument instanceof Oas20Document) {
            return readClassOas20((Oas20Document) oasDocument, clazz);
        } else if (oasDocument instanceof Oas30Document) {
            return readClassOas30((Oas30Document) oasDocument, clazz);
        } else {
            return null;
        }
    }

    private List<? extends OasSchema> readClassOas30(Oas30Document oasDocument, Class<?> clazz) {
        String name = clazz.getName();
        if (!name.contains(".")) {
            return null;
        }

        if (oasDocument.components == null) {
            oasDocument.components = oasDocument.createComponents();
        }

        Map<String, Schema> swaggerModel = MODEL30_CONVERTERS.readAll(clazz);
        swaggerModel.forEach((key, schema) -> {
            Oas30SchemaDefinition model = oasDocument.components.createSchemaDefinition(key);
            oasDocument.components.addSchemaDefinition(key, model);
            processSchema(model, schema);
        });

        return oasDocument.components.getSchemaDefinitions();
    }

    private List<? extends OasSchema> readClassOas20(Oas20Document oasDocument, Class<?> clazz) {
        String name = clazz.getName();
        if (!name.contains(".")) {
            return null;
        }

        if (oasDocument.definitions == null) {
            oasDocument.definitions = oasDocument.createDefinitions();
        }

        Map<String, Schema> swaggerModel = MODEL20_CONVERTERS.readAll(clazz);
        swaggerModel.forEach((key, schema) -> {
            Oas20SchemaDefinition model = oasDocument.definitions.createSchemaDefinition(key);
            oasDocument.definitions.addDefinition(key, model);
            processSchema(model, schema);
        });

        return oasDocument.definitions.getDefinitions();
    }

    private void processSchema(OasSchema model, Schema schema) {
        String type = schema.getType();
        model.type = type;
        model.format = schema.getFormat();

        String ref = schema.get$ref();
        if (ref != null) {
            if (model instanceof Oas20Schema) {
                // Change the prefix from 3.x to 2.x
                model.$ref = RestOpenApiReader.OAS20_SCHEMA_DEFINITION_PREFIX +
                             ref.substring(RestOpenApiReader.OAS30_SCHEMA_DEFINITION_PREFIX.length());
            } else {
                model.$ref = ref;
            }
        }
        Boolean nullable = schema.getNullable();
        if (nullable != null && model instanceof Oas30Schema) {
            ((Oas30Schema) model).nullable = nullable;
        }

        // xxxOf support
        if (model instanceof Oas30SchemaDefinition && schema instanceof ComposedSchema) {
            ComposedSchema composedSchema = (ComposedSchema) schema;
            Oas30SchemaDefinition modelDefinition = (Oas30SchemaDefinition) model;

            // oneOf
            boolean xOf = false;
            if (null != composedSchema.getOneOf()) {
                xOf = true;
                for (Schema oneOfSchema : composedSchema.getOneOf()) {
                    if (null != oneOfSchema.get$ref()) {
                        Oas30OneOfSchema oneOfModel = modelDefinition.createOneOfSchema();
                        oneOfModel.setReference(oneOfSchema.get$ref());
                        modelDefinition.addOneOfSchema(oneOfModel);
                        type = null; // No longer typed
                        model.type = null;
                    }
                }
            }

            // allOf
            if (null != composedSchema.getAllOf()) {
                xOf = true;
                for (Schema allOfSchema : composedSchema.getAllOf()) {
                    if (null != allOfSchema.get$ref()) {
                        OasSchema allOfModel = modelDefinition.createAllOfSchema();
                        allOfModel.setReference(allOfSchema.get$ref());
                        modelDefinition.addAllOfSchema(allOfModel);
                        type = null; // No longer typed
                        model.type = null;
                    }
                }
            }

            // anyOf
            if (null != composedSchema.getAnyOf()) {
                xOf = true;
                for (Schema anyOfSchema : composedSchema.getAnyOf()) {
                    if (null != anyOfSchema.get$ref()) {
                        Oas30AnyOfSchema anyOfModel = modelDefinition.createAnyOfSchema();
                        anyOfModel.setReference(anyOfSchema.get$ref());
                        modelDefinition.addAnyOfSchema(anyOfModel);
                        type = null; // No longer typed
                        model.type = null;
                    }
                }
            }

            // Discriminator
            if (xOf && null != composedSchema.getDiscriminator()) {
                Discriminator discriminator = schema.getDiscriminator();
                Oas30Discriminator modelDiscriminator = modelDefinition.createDiscriminator();
                modelDiscriminator.propertyName = discriminator.getPropertyName();

                if (null != discriminator.getMapping()) {
                    discriminator.getMapping().entrySet().stream()
                            .forEach(e -> modelDiscriminator.addMapping(e.getKey(), e.getValue()));
                }
                modelDefinition.discriminator = modelDiscriminator;
            }
        }

        if (type != null) {
            switch (type) {
                case "object":
                    if (schema.getProperties() != null) {
                        //noinspection unchecked
                        schema.getProperties().forEach((p, v) -> {
                            OasSchema property = (OasSchema) model.createPropertySchema((String) p);
                            model.addProperty((String) p, property);
                            processSchema(property, (Schema) v);
                        });
                    }
                    break;
                case "array":
                    Schema items = ((ArraySchema) schema).getItems();
                    OasSchema modelItems = model.createItemsSchema();
                    model.items = modelItems;
                    processSchema(modelItems, items);
                    break;
                case "string":
                    if (schema.getEnum() != null) {
                        //noinspection unchecked
                        model.enum_ = new ArrayList<String>(schema.getEnum());
                    }
                    break;
                case "boolean":
                case "number":
                case "integer":
                    break;
                default:
                    LOG.warn("Encountered unexpected type {} in processing schema.", type);
                    break;
            }
        }

        if (schema.getRequired() != null) {
            //noinspection unchecked
            model.required = new ArrayList<String>(schema.getRequired());
        }

        String description = schema.getDescription();
        if (description != null) {
            model.description = description;
        }
        Object example = schema.getExample();
        if (example != null) {
            model.example = example;
        }

        if (schema.getAdditionalProperties() instanceof Schema) {
            OasSchema additionalProperties = model.createAdditionalPropertiesSchema();
            model.additionalProperties = additionalProperties;
            processSchema(additionalProperties, (Schema) schema.getAdditionalProperties());
        }

        if (schema.getExtensions() != null) {
            //noinspection unchecked
            schema.getExtensions().forEach((key, value) -> {
                Extension extension = model.createExtension();
                extension.name = (String) key;
                extension.value = value;

                model.addExtension((String) key, extension);
            });
        }
    }

    private static class FqnModelResolver extends ModelResolver {
        public FqnModelResolver() {
            this(new ObjectMapper());
        }

        public FqnModelResolver(ObjectMapper mapper) {
            super(mapper);
            this._typeNameResolver.setUseFqn(true);
        }
    }

    private static class ClassNameExtensionModelResolver extends ModelResolver {
        private final ModelResolver delegate;

        public ClassNameExtensionModelResolver() {
            this(new ModelResolver(new ObjectMapper()));
        }

        public ClassNameExtensionModelResolver(ModelResolver delegate) {
            super(delegate.objectMapper());
            this.delegate = delegate;
        }

        @Override
        public Schema resolve(AnnotatedType annotatedType, ModelConverterContext context, Iterator<ModelConverter> next) {
            Schema result = delegate.resolve(annotatedType, context, next);

            if (result != null && Objects.equals("object", result.getType())) {
                JavaType type;
                if (annotatedType.getType() instanceof JavaType) {
                    type = (JavaType) annotatedType.getType();
                } else {
                    type = _mapper.constructType(annotatedType.getType());
                }

                if (!type.isContainerType()) {
                    Map<String, String> value = new HashMap<>();
                    value.put("type", "string");
                    value.put("format", type.getRawClass().getName());

                    result.addExtension("x-className", value);
                }
            }
            return result;
        }
    }

}
