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
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.datamodels.core.models.Extension;
import io.apicurio.datamodels.openapi.models.OasDocument;
import io.apicurio.datamodels.openapi.models.OasSchema;
import io.apicurio.datamodels.openapi.v2.models.Oas20Document;
import io.apicurio.datamodels.openapi.v2.models.Oas20Schema;
import io.apicurio.datamodels.openapi.v2.models.Oas20SchemaDefinition;
import io.apicurio.datamodels.openapi.v3.models.Oas30Document;
import io.apicurio.datamodels.openapi.v3.models.Oas30SchemaDefinition;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Camel extended {@link ModelConverters} where we appending vendor extensions to include the java class name of the
 * model classes.
 */
@SuppressWarnings("rawtypes")
public class RestModelConverters {

    private static final Logger LOG = LoggerFactory.getLogger(RestModelConverters.class);
    private static final ModelConverters MODEL_CONVERTERS;

    static {
        MODEL_CONVERTERS = ModelConverters.getInstance();
        MODEL_CONVERTERS.addConverter(new FqnModelResolver());
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

        Map<String, Schema> swaggerModel = MODEL_CONVERTERS.readAll(clazz);
        swaggerModel.forEach((key, schema) -> {
            Oas30SchemaDefinition model = oasDocument.components.createSchemaDefinition(key);
            oasDocument.components.addSchemaDefinition(key, model);
            processSchema(model, schema);

            addClassNameExtension(model, key);
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

        Map<String, Schema> swaggerModel = ModelConverters.getInstance().readAll(clazz);
        swaggerModel.forEach((key, schema) -> {
            Oas20SchemaDefinition model = oasDocument.definitions.createSchemaDefinition(key);
            oasDocument.definitions.addDefinition(key, model);
            processSchema(model, schema);

            addClassNameExtension(model, key);
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
            });
        }
    }

    private void addClassNameExtension(OasSchema schema, String name) {
        Extension extension = schema.createExtension();
        extension.name = "x-className";
        Map<String, String> value = new HashMap<>();
        value.put("type", "string");
        value.put("format", name);
        extension.value = value;
        schema.addExtension("x-className", extension);
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

}
