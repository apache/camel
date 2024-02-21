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

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;

/**
 * A Camel extended {@link ModelConverters} where we are appending vendor extensions to include the java class name of
 * the model classes.
 */
@SuppressWarnings("rawtypes")
public class RestModelConverters {

    private static final ModelConverters MODEL31_CONVERTERS;

    static {
        MODEL31_CONVERTERS = ModelConverters.getInstance(true);
        MODEL31_CONVERTERS.addConverter(new ClassNameExtensionModelResolver(new FqnModelResolver(true)));
    }

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

    private final boolean openapi31;

    public RestModelConverters(boolean openapi31) {
        this.openapi31 = openapi31;
    }

    public List<? extends Schema<?>> readClass(OpenAPI oasDocument, Class<?> clazz) {
        if (clazz.equals(java.io.File.class)) {
            // File is a special type in OAS2 / OAS3 (no model)
            return null;
        } else {
            return readClassOpenApi3(clazz);
        }
    }

    private List<? extends Schema<?>> readClassOpenApi3(Class<?> clazz) {
        String name = clazz.getName();
        if (!name.contains(".")) {
            return null;
        }

        ModelConverters modelConverters = openapi31 ? MODEL31_CONVERTERS : MODEL30_CONVERTERS;
        Map<String, Schema> swaggerModel = modelConverters.readAll(clazz);
        List<Schema<?>> modelSchemas = new java.util.ArrayList<>();
        swaggerModel.forEach((key, schema) -> {
            schema.setName(key);
            modelSchemas.add(schema);
        });
        return modelSchemas;
    }

    private static class FqnModelResolver extends ModelResolver {
        public FqnModelResolver() {
            this(false);
        }

        public FqnModelResolver(boolean openapi31) {
            this(new ObjectMapper());
            openapi31(openapi31);
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
            ModelResolver.composedModelPropertiesAsSibling = true;
        }

        public ClassNameExtensionModelResolver(ModelResolver delegate) {
            super(delegate.objectMapper());
            this.delegate = delegate;
        }

        @Override
        public Schema resolve(AnnotatedType annotatedType, ModelConverterContext context, Iterator<ModelConverter> next) {
            Schema<?> result = delegate.resolve(annotatedType, context, next);

            if (result != null && Objects.equals("object", result.getType())) {
                JavaType type;
                if (annotatedType.getType() instanceof JavaType) {
                    type = (JavaType) annotatedType.getType();
                } else {
                    type = _mapper.constructType(annotatedType.getType());
                }

                if (!type.isContainerType()) {
                    Map<String, String> value = new java.util.HashMap<>();
                    value.put("type", "string");
                    value.put("format", type.getRawClass().getName());
                    result.addExtension("x-className", value);
                    // OpenAPI 3: would it be better to set the classname directly as "format" ?
                    // result.setFormat(type.getRawClass().getName());
                }
            }
            return result;
        }
    }
}
