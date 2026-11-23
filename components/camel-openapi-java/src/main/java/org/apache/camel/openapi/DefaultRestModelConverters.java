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

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
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
import io.swagger.v3.oas.models.SpecVersion;
import io.swagger.v3.oas.models.media.Schema;

/**
 * Default {@link RestModelConverters} implementation backed by Swagger's model converters.
 */
@SuppressWarnings("rawtypes")
public class DefaultRestModelConverters implements RestModelConverters {

    private final ModelConverters model30Converters;
    private final ModelConverters model31Converters;

    public DefaultRestModelConverters() {
        model31Converters = new ModelConverters(true);
        model31Converters.addConverter(new ClassNameExtensionModelResolver(new FqnModelResolver(true)));

        model30Converters = new ModelConverters();
        model30Converters.addConverter(new ClassNameExtensionModelResolver(new FqnModelResolver()));
    }

    @Override
    public void addConverter(ModelConverter converter) {
        model30Converters.addConverter(converter);
        model31Converters.addConverter(converter);
    }

    @Override
    public void addPackageToSkip(String pkg) {
        model30Converters.addPackageToSkip(pkg);
        model31Converters.addPackageToSkip(pkg);
    }

    @Override
    public void addClassToSkip(String cls) {
        model30Converters.addClassToSkip(cls);
        model31Converters.addClassToSkip(cls);
    }

    @Override
    public List<? extends Schema<?>> readClass(OpenAPI oasDocument, Class<?> clazz) {
        if (clazz.equals(File.class)) {
            // File is a special type in OAS2 / OAS3 (no model)
            return null;
        }

        String name = clazz.getName();
        if (!name.contains(".")) {
            return null;
        }

        ModelConverters converters = oasDocument.getSpecVersion() == SpecVersion.V31
                ? model31Converters : model30Converters;
        Map<String, Schema> swaggerModel = converters.readAll(clazz);
        List<Schema<?>> modelSchemas = new ArrayList<>();
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
            if (result != null) {
                String rt = result.getType();
                if (rt == null && result.getTypes() != null) {
                    rt = result.getTypes().size() == 1 ? result.getTypes().iterator().next() : null;
                }
                if (Objects.equals("object", rt)) {
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
            }
            return result;
        }
    }
}
