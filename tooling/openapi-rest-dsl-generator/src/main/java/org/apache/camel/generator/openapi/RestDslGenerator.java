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
package org.apache.camel.generator.openapi;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.processing.Filer;

import io.apicurio.datamodels.models.ServerVariable;
import io.apicurio.datamodels.models.openapi.OpenApiDocument;
import io.apicurio.datamodels.models.openapi.v20.OpenApi20Document;
import io.apicurio.datamodels.models.openapi.v30.OpenApi30Document;
import io.apicurio.datamodels.models.openapi.v30.OpenApi30Server;
import org.apache.camel.model.rest.RestsDefinition;

import static org.apache.camel.util.ObjectHelper.notNull;

/**
 * Source code and {@link RestsDefinition} generator that generates Camel REST DSL implementations from OpenAPI
 * specifications.
 */
public abstract class RestDslGenerator<G> {

    String apiContextPath;

    DestinationGenerator destinationGenerator;

    String destinationToSyntax;

    final OpenApiDocument document;

    OperationFilter filter = new OperationFilter();

    String restComponent;

    String restContextPath;

    boolean clientRequestValidation;

    boolean springBootProject;

    boolean springComponent;

    String basePath;

    RestDslGenerator(final OpenApiDocument document) {
        this.document = notNull(document, "document");
    }

    public G asSpringBootProject() {
        this.springBootProject = true;

        @SuppressWarnings("unchecked")
        final G that = (G) this;

        return that;
    }

    public G asSpringComponent() {
        this.springComponent = true;

        @SuppressWarnings("unchecked")
        final G that = (G) this;

        return that;
    }

    public G withApiContextPath(final String contextPath) {
        this.apiContextPath = contextPath;

        @SuppressWarnings("unchecked")
        final G that = (G) this;

        return that;
    }

    public G withClientRequestValidation() {
        this.clientRequestValidation = true;

        @SuppressWarnings("unchecked")
        final G that = (G) this;

        return that;
    }

    public G withBasePath(final String basePath) {
        this.basePath = basePath;

        @SuppressWarnings("unchecked")
        final G that = (G) this;

        return that;
    }

    public G withDestinationGenerator(final DestinationGenerator destinationGenerator) {
        this.destinationGenerator = destinationGenerator;

        @SuppressWarnings("unchecked")
        final G that = (G) this;

        return that;
    }

    /**
     * Syntax to use for to uri.
     *
     * The default is <tt>direct:${operationId}</tt>
     */
    public G withDestinationToSyntax(final String destinationToSyntax) {
        this.destinationToSyntax = destinationToSyntax;

        @SuppressWarnings("unchecked")
        final G that = (G) this;

        return that;
    }

    public G withOperationFilter(final OperationFilter filter) {
        this.filter = filter;

        @SuppressWarnings("unchecked")
        final G that = (G) this;

        return that;
    }

    public G withOperationFilter(final String include) {
        this.filter.setIncludes(include);

        @SuppressWarnings("unchecked")
        final G that = (G) this;

        return that;
    }

    public G withRestComponent(final String restComponent) {
        this.restComponent = restComponent;

        @SuppressWarnings("unchecked")
        final G that = (G) this;

        return that;
    }

    public G withRestContextPath(final String contextPath) {
        this.restContextPath = contextPath;

        @SuppressWarnings("unchecked")
        final G that = (G) this;

        return that;
    }

    DestinationGenerator destinationGenerator() {
        if (destinationGenerator == null) {
            destinationGenerator = destinationToSyntax != null
                    ? new DefaultDestinationGenerator(destinationToSyntax) : new DefaultDestinationGenerator();
        }
        return destinationGenerator;
    }

    public static String determineBasePathFrom(final String parameter, final OpenApiDocument document) {
        return parameter != null
                ? determineBasePathFrom(parameter) : determineBasePathFrom(document);
    }

    public static String determineBasePathFrom(final String parameter) {
        Objects.requireNonNull(parameter, "parameter");

        return prepareBasePath(parameter.trim());
    }

    public static String determineBasePathFrom(final OpenApiDocument document) {
        Objects.requireNonNull(document, "document");

        if (document instanceof OpenApi20Document) {
            return ((OpenApi20Document) document).getBasePath();
        } else if (document instanceof OpenApi30Document) {
            final OpenApi30Document oas30Document = (OpenApi30Document) document;
            final List<OpenApi30Server> servers = oas30Document.getServers();

            if (servers == null || servers.get(0) == null) {
                return "";
            }

            final OpenApi30Server firstServer = servers.get(0);
            final URI serverUrl = URI.create(resolveVariablesIn(firstServer.getUrl(), firstServer));
            return prepareBasePath(serverUrl.getPath());
        }

        throw new IllegalArgumentException("Unsupported document type: " + document.getClass().getName());
    }

    private static String prepareBasePath(String basePath) {
        if (basePath == null || basePath.length() == 0) {
            return "";
        }

        if (basePath.charAt(0) != '/') {
            basePath = "/" + basePath;
        }

        if (basePath.indexOf("//") == 0) {
            // strip off the first "/" if double "/" exists
            basePath = basePath.substring(1);
        }

        if ("/".equals(basePath)) {
            basePath = "";
        }

        return basePath;
    }

    public static String determineHostFrom(final OpenApiDocument document) {
        if (document instanceof OpenApi20Document) {
            return ((OpenApi20Document) document).getHost();
        } else if (document instanceof OpenApi30Document) {
            final OpenApi30Document oas30Document = (OpenApi30Document) document;
            final List<OpenApi30Server> servers = oas30Document.getServers();

            if (servers == null || servers.get(0) == null) {
                return "";
            }

            final OpenApi30Server firstServer = servers.get(0);

            final URI serverUrl = URI.create(resolveVariablesIn(firstServer.getUrl(), firstServer));

            return serverUrl.getHost();
        }

        throw new IllegalArgumentException("Unsupported document type: " + document.getClass().getName());
    }

    public static String resolveVariablesIn(final String url, final OpenApi30Server server) {
        final Map<String, ServerVariable> variables = Objects.requireNonNull(server, "server").getVariables();
        String withoutPlaceholders = url;
        if (variables != null) {
            for (Map.Entry<String, ServerVariable> entry : variables.entrySet()) {
                final String name = "{" + entry.getKey() + "}";
                withoutPlaceholders = withoutPlaceholders.replace(name, entry.getValue().getDefault());
            }
        }

        return withoutPlaceholders;
    }

    public static RestDslSourceCodeGenerator<Appendable> toAppendable(final OpenApiDocument document) {
        return new AppendableGenerator(document);
    }

    public static RestDslDefinitionGenerator toDefinition(final OpenApiDocument document) {
        return new RestDslDefinitionGenerator(document);
    }

    public static RestDslSourceCodeGenerator<Filer> toFiler(final OpenApiDocument document) {
        return new FilerGenerator(document);
    }

    public static RestDslSourceCodeGenerator<Path> toPath(final OpenApiDocument document) {
        return new PathGenerator(document);
    }

    public static RestDslXmlGenerator toXml(final OpenApiDocument document) {
        return new RestDslXmlGenerator(document);
    }

    public static RestDslYamlGenerator toYaml(final OpenApiDocument document) {
        return new RestDslYamlGenerator(document);
    }
}
