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
package org.apache.camel.language.datasonnet;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import com.datasonnet.Mapper;
import com.datasonnet.document.MediaType;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.spi.PropertyConfigurer;
import org.apache.camel.spi.annotations.Language;
import org.apache.camel.support.LRUCacheFactory;
import org.apache.camel.support.TypedLanguageSupport;
import org.apache.camel.support.component.PropertyConfigurerSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Language("datasonnet")
public class DatasonnetLanguage extends TypedLanguageSupport implements PropertyConfigurer {
    private static final Logger LOG = LoggerFactory.getLogger(DatasonnetLanguage.class);

    private static final Map<String, String> CLASSPATH_IMPORTS = new HashMap<>();

    static {
        LOG.debug("One time classpath search...");
        try (ScanResult scanResult = new ClassGraph().acceptPaths("/").scan()) {
            try {
                scanResult.getResourcesWithExtension("libsonnet")
                        .forEachByteArrayThrowingIOException((resource, bytes) -> {
                            LOG.debug("Loading DataSonnet library: {}", resource.getPath());
                            CLASSPATH_IMPORTS.put(resource.getPath(), new String(bytes, StandardCharsets.UTF_8));
                        });
            } catch (IOException e) {
                // ignore
            }
        }
        LOG.debug("One time classpath search done");
    }

    // Cache used to stores the Mappers
    // See: {@link GroovyLanguage}
    private final Map<String, Mapper> mapperCache = LRUCacheFactory.newLRUSoftCache(16, 1000, true);

    private MediaType bodyMediaType;
    private MediaType outputMediaType;
    private Collection<String> libraryPaths;

    @Override
    public Predicate createPredicate(String expression) {
        return createPredicate(expression, null);
    }

    @Override
    public Expression createExpression(String expression) {
        return createExpression(expression, null);
    }

    @Override
    public Predicate createPredicate(String expression, Object[] properties) {
        return (Predicate) createExpression(expression, properties);
    }

    @Override
    public Expression createExpression(String expression, Object[] properties) {
        expression = loadResource(expression);

        DatasonnetExpression answer = new DatasonnetExpression(expression);
        answer.setResultType(property(Class.class, properties, 0, getResultType()));

        String stringBodyMediaType = property(String.class, properties, 1, null);
        answer.setBodyMediaType(stringBodyMediaType != null ? MediaType.valueOf(stringBodyMediaType) : bodyMediaType);
        String stringOutputMediaType = property(String.class, properties, 2, null);
        answer.setOutputMediaType(stringOutputMediaType != null ? MediaType.valueOf(stringOutputMediaType) : outputMediaType);

        return answer;
    }

    Optional<Mapper> lookup(String script) {
        return Optional.ofNullable(mapperCache.get(script));
    }

    Mapper computeIfMiss(String script, Supplier<Mapper> mapperSupplier) {
        return mapperCache.computeIfAbsent(script, k -> mapperSupplier.get());
    }

    public Map<String, String> getClasspathImports() {
        return CLASSPATH_IMPORTS;
    }

    @Override
    public boolean configure(CamelContext camelContext, Object target, String name, Object value, boolean ignoreCase) {
        if (target != this) {
            throw new IllegalStateException("Can only configure our own instance !");
        }

        switch (ignoreCase ? name.toLowerCase() : name) {
            case "bodyMediaType":
            case "bodymediatype":
                setBodyMediaType(PropertyConfigurerSupport.property(camelContext, String.class, value));
                return true;
            case "outputMediaType":
            case "outputmediatype":
                setOutputMediaType(PropertyConfigurerSupport.property(camelContext, String.class, value));
                return true;
            case "resultType":
            case "resulttype":
                setResultType(PropertyConfigurerSupport.property(camelContext, Class.class, value));
                return true;
            default:
                return false;
        }
    }

    // Getter/Setter methods
    // -------------------------------------------------------------------------

    public MediaType getBodyMediaType() {
        return bodyMediaType;
    }

    public void setBodyMediaType(MediaType bodyMediaType) {
        this.bodyMediaType = bodyMediaType;
    }

    public void setBodyMediaType(String bodyMediaType) {
        this.bodyMediaType = MediaType.valueOf(bodyMediaType);
    }

    public MediaType getOutputMediaType() {
        return outputMediaType;
    }

    public void setOutputMediaType(MediaType outputMediaType) {
        this.outputMediaType = outputMediaType;
    }

    public void setOutputMediaType(String outputMediaType) {
        this.outputMediaType = MediaType.valueOf(outputMediaType);
    }

    public Collection<String> getLibraryPaths() {
        return libraryPaths;
    }

    public void setLibraryPaths(Collection<String> libraryPaths) {
        this.libraryPaths = libraryPaths;
    }
}
