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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import com.datasonnet.Mapper;
import com.datasonnet.document.MediaType;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.spi.annotations.Language;
import org.apache.camel.support.LRUCacheFactory;
import org.apache.camel.support.SingleInputTypedLanguageSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Language("datasonnet")
public class DatasonnetLanguage extends SingleInputTypedLanguageSupport {
    private static final Logger LOG = LoggerFactory.getLogger(DatasonnetLanguage.class);
    private final Map<String, String> classpathImports;

    // Cache used to stores the Mappers
    // See: {@link GroovyLanguage}
    private final Map<String, Mapper> mapperCache = LRUCacheFactory.newLRUSoftCache(16, 1000, true);

    public DatasonnetLanguage() {
        this.classpathImports = discoverDataSonnetLibraries();
    }

    public DatasonnetLanguage(Map<String, String> classpathImports) {
        this.classpathImports = classpathImports;
    }

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
    public Expression createExpression(Expression source, String expression, Object[] properties) {
        expression = loadResource(expression);

        DatasonnetExpression answer = new DatasonnetExpression(expression);
        answer.setSource(source);
        answer.setResultType(property(Class.class, properties, 0, null));
        String mediaType = property(String.class, properties, 2, null);
        if (mediaType != null) {
            answer.setBodyMediaType(MediaType.valueOf(mediaType));
        }
        mediaType = property(String.class, properties, 3, null);
        if (mediaType != null) {
            answer.setOutputMediaType(MediaType.valueOf(mediaType));
        }
        if (getCamelContext() != null) {
            answer.init(getCamelContext());
        }
        return answer;
    }

    Optional<Mapper> lookup(String script) {
        return Optional.ofNullable(mapperCache.get(script));
    }

    Mapper computeIfMiss(String script, Supplier<Mapper> mapperSupplier) {
        return mapperCache.computeIfAbsent(script, k -> mapperSupplier.get());
    }

    public Map<String, String> getClasspathImports() {
        return classpathImports;
    }

    private Map<String, String> discoverDataSonnetLibraries() {
        Map<String, String> imports = new HashMap<>();
        try (ScanResult scanResult = new ClassGraph().acceptPaths("/").scan()) {
            try {
                scanResult
                        .getResourcesWithExtension("libsonnet")
                        .forEachByteArrayThrowingIOException((resource, bytes) -> {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("Loading DataSonnet library: {}", resource.getPath());
                            }
                            imports.put(resource.getPath(), new String(bytes, StandardCharsets.UTF_8));
                        });
            } catch (IOException e) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("Failed to load DataSonnet libraries", e);
                }
            }
        }
        return imports;
    }
}
