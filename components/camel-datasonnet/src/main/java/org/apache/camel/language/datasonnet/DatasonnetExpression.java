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

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.datasonnet.Mapper;
import com.datasonnet.MapperBuilder;
import com.datasonnet.document.DefaultDocument;
import com.datasonnet.document.Document;
import com.datasonnet.document.MediaType;
import com.datasonnet.document.MediaTypes;
import com.datasonnet.header.Header;
import com.datasonnet.spi.Library;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.RuntimeExpressionException;
import org.apache.camel.spi.ExpressionResultTypeAware;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.ExpressionAdapter;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatasonnetExpression extends ExpressionAdapter implements ExpressionResultTypeAware {
    private static final Logger LOG = LoggerFactory.getLogger(DatasonnetExpression.class);

    private final String expression;
    private Expression source;
    private MediaType bodyMediaType;
    private MediaType outputMediaType;
    private Class<?> resultType;
    private Collection<String> libraryPaths;
    private transient DatasonnetLanguage language;

    public DatasonnetExpression(String expression) {
        this.expression = expression;
    }

    @Deprecated
    public static DatasonnetExpression builder(String expression) {
        return new DatasonnetExpression(expression);
    }

    @Deprecated
    public static DatasonnetExpression builder(String expression, Class<?> resultType) {
        DatasonnetExpression answer = new DatasonnetExpression(expression);
        answer.setResultType(resultType);
        return answer;
    }

    @Override
    public boolean matches(Exchange exchange) {
        this.outputMediaType = MediaTypes.APPLICATION_JAVA;
        return evaluate(exchange, Boolean.class);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T evaluate(Exchange exchange, Class<T> type) {
        try {
            // pass exchange to CML lib using thread as context
            CML.getInstance().getExchange().set(exchange);

            Document<?> result = doEvaluate(exchange);
            if (!type.equals(Object.class)) {
                return ExchangeHelper.convertToType(exchange, type, result.getContent());
            } else if (resultType == null || resultType.equals(Document.class)) {
                return (T) result;
            } else {
                return (T) result.getContent();
            }
        } catch (Exception e) {
            throw new RuntimeExpressionException("Unable to evaluate DataSonnet expression: " + expression, e);
        } finally {
            CML.getInstance().getExchange().remove();
        }
    }

    private Document<?> doEvaluate(Exchange exchange) {
        MediaType bodyMT = bodyMediaType;
        if (bodyMT == null && !expression.startsWith(Header.DATASONNET_HEADER)) {
            //Try to auto-detect input mime type if it was not explicitly set
            String typeHeader = exchange.getProperty(DatasonnetConstants.BODY_MEDIATYPE,
                    exchange.getIn().getHeader(Exchange.CONTENT_TYPE), String.class);
            if (typeHeader != null) {
                bodyMT = MediaType.valueOf(typeHeader);
            }
        }

        Object payload = source != null ? source.evaluate(exchange, Object.class) : exchange.getMessage().getBody();
        Document<?> doc = null;
        if (payload != null) {
            doc = exchange.getContext().getTypeConverter().tryConvertTo(Document.class, exchange, payload);
        }
        if (doc == null) {
            String text = exchange.getContext().getTypeConverter().tryConvertTo(String.class, exchange, payload);
            if (exchange.getMessage().getBody() == null || "".equals(text)) {
                //Empty body, force type to be application/java
                doc = new DefaultDocument<>("", MediaTypes.APPLICATION_JAVA);
            } else if (MediaTypes.APPLICATION_JAVA.equalsTypeAndSubtype(bodyMT) || bodyMT == null) {
                doc = new DefaultDocument<>(payload);
            } else {
                // force using string value
                doc = new DefaultDocument<>(text, bodyMT);
            }
        }

        // the mapper is pre initialized
        Mapper mapper = language.lookup(expression)
                .orElseThrow(() -> new IllegalStateException("Datasonnet expression not initialized"));

        MediaType outMT = outputMediaType;
        if (outMT == null) {
            //Try to auto-detect output mime type if it was not explicitly set
            String typeHeader = exchange.getProperty(DatasonnetConstants.OUTPUT_MEDIATYPE,
                    exchange.getIn().getHeader(DatasonnetConstants.OUTPUT_MEDIATYPE), String.class);
            if (typeHeader != null) {
                outMT = MediaType.valueOf(typeHeader);
            } else {
                outMT = MediaTypes.ANY;
            }
        }

        Map<String, Document<?>> inputs = Collections.singletonMap("body", doc);
        if (resultType == null || resultType.equals(Document.class)) {
            return mapper.transform(doc, inputs, outMT, Object.class);
        } else {
            return mapper.transform(doc, inputs, outMT, resultType);
        }
    }

    private Map<String, String> resolveImports(DatasonnetLanguage language) {
        if (libraryPaths == null) {
            return language.getClasspathImports();
        }

        Map<String, String> answer = new HashMap<>();
        LOG.debug("Explicit library path is: {}", libraryPaths);
        for (String nextPath : libraryPaths) {
            final File nextLibDir = new File(nextPath);
            if (nextLibDir.isDirectory()) {
                try {
                    Files.walkFileTree(nextLibDir.toPath(), new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            File f = file.toFile();
                            if (!f.isDirectory() && f.getName().toLowerCase().endsWith(".libsonnet")) {
                                String content = IOUtils.toString(file.toUri(), Charset.defaultCharset());
                                Path relative = nextLibDir.toPath().relativize(file);
                                LOG.debug("Loading DataSonnet library: {}", relative);
                                answer.put(relative.toString(), content);
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
                } catch (IOException e) {
                    LOG.warn("Unable to load DataSonnet library from: {}", nextPath, e);
                }
            }
        }

        return answer;
    }

    @Override
    public void init(CamelContext context) {
        super.init(context);

        language = (DatasonnetLanguage) context.resolveLanguage("datasonnet");
        // initialize mapper eager
        language.computeIfMiss(expression, () -> {
            MapperBuilder builder = new MapperBuilder(expression)
                    .withInputNames("body")
                    .withImports(resolveImports(language))
                    .withLibrary(CML.getInstance())
                    .withDefaultOutput(MediaTypes.APPLICATION_JAVA);

            Set<Library> additionalLibraries = context.getRegistry().findByType(com.datasonnet.spi.Library.class);
            for (Library lib : additionalLibraries) {
                builder = builder.withLibrary(lib);
            }
            return builder.build();
        });
    }

    // Getter/Setter methods
    // -------------------------------------------------------------------------

    public Expression getSource() {
        return source;
    }

    public void setSource(Expression source) {
        this.source = source;
    }

    public MediaType getBodyMediaType() {
        return bodyMediaType;
    }

    /**
     * The message's body MediaType
     */
    public void setBodyMediaType(MediaType inputMimeType) {
        this.bodyMediaType = inputMimeType;
    }

    public MediaType getOutputMediaType() {
        return outputMediaType;
    }

    /**
     * The MediaType to output
     */
    public void setOutputMediaType(MediaType outputMimeType) {
        this.outputMediaType = outputMimeType;
    }

    public Collection<String> getLibraryPaths() {
        return libraryPaths;
    }

    /**
     * The paths to search for .libsonnet files
     */
    public void setLibraryPaths(Collection<String> libraryPaths) {
        this.libraryPaths = libraryPaths;
    }

    @Override
    public String getExpressionText() {
        return this.expression;
    }

    @Override
    public Class<?> getResultType() {
        return this.resultType;
    }

    /**
     * Sets the class of the result type (type from output).
     * <p/>
     * The default result type is com.datasonnet.document.Document
     */
    public void setResultType(Class<?> targetType) {
        this.resultType = targetType;
    }

    // Fluent builder methods
    // -------------------------------------------------------------------------

    @Deprecated
    public DatasonnetExpression bodyMediaType(MediaType bodyMediaType) {
        setBodyMediaType(bodyMediaType);
        return this;
    }

    @Deprecated
    public DatasonnetExpression outputMediaType(MediaType outputMediaType) {
        setOutputMediaType(outputMediaType);
        return this;
    }

    @Override
    public String toString() {
        return "datasonnet: " + expression;
    }

}
