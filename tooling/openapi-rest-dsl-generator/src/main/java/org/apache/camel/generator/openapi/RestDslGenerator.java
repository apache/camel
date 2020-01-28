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

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.processing.Filer;

import io.apicurio.datamodels.openapi.models.OasDocument;
import io.apicurio.datamodels.openapi.v2.models.Oas20Document;
import io.apicurio.datamodels.openapi.v3.models.Oas30Document;
import io.apicurio.datamodels.openapi.v3.models.Oas30Server;
import org.apache.camel.model.rest.RestsDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



import static org.apache.camel.util.ObjectHelper.notNull;

/**
 * Source code and {@link RestsDefinition} generator that generates Camel REST
 * DSL implementations from OpenAPI specifications.
 */
public abstract class RestDslGenerator<G> {

    private static final Logger LOG = LoggerFactory.getLogger(RestDslGenerator.class);
    final OasDocument openapi;

    
    DestinationGenerator destinationGenerator = new DirectToOperationId();
    OperationFilter filter = new OperationFilter();
    String restComponent;
    String restContextPath;
    String apiContextPath;
    boolean springComponent;
    boolean springBootProject;

    RestDslGenerator(final OasDocument openapi) {
        this.openapi = notNull(openapi, "openapi");
    }

    public G withDestinationGenerator(final DestinationGenerator directRouteGenerator) {
        notNull(directRouteGenerator, "directRouteGenerator");
        this.destinationGenerator = directRouteGenerator;

        @SuppressWarnings("unchecked")
        final G that = (G) this;

        return that;
    }

    DestinationGenerator destinationGenerator() {
        return destinationGenerator;
    }

    public G withOperationFilter(OperationFilter filter) {
        this.filter = filter;

        @SuppressWarnings("unchecked")
        final G that = (G) this;

        return that;
    }

    public G withOperationFilter(String include) {
        this.filter.setIncludes(include);

        @SuppressWarnings("unchecked")
        final G that = (G) this;

        return that;
    }

    public G withRestComponent(String restComponent) {
        this.restComponent = restComponent;

        @SuppressWarnings("unchecked")
        final G that = (G) this;

        return that;
    }

    public G withRestContextPath(String contextPath) {
        this.restContextPath = contextPath;

        @SuppressWarnings("unchecked")
        final G that = (G) this;

        return that;
    }
    
    public G withApiContextPath(String contextPath) {
        this.apiContextPath = contextPath;

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

    public G asSpringBootProject() {
        this.springBootProject = true;

        @SuppressWarnings("unchecked")
        final G that = (G) this;

        return that;
    }

    public static RestDslSourceCodeGenerator<Appendable> toAppendable(final OasDocument openapi) {
        return new AppendableGenerator(openapi);
    }

    public static RestDslDefinitionGenerator toDefinition(final OasDocument openapi) {
        return new RestDslDefinitionGenerator(openapi);
    }

    public static RestDslXmlGenerator toXml(final OasDocument openapi) {
        return new RestDslXmlGenerator(openapi);
    }

    public static RestDslSourceCodeGenerator<Filer> toFiler(final OasDocument openapi) {
        return new FilerGenerator(openapi);
    }

    public static RestDslSourceCodeGenerator<Path> toPath(final OasDocument openapi) {
        return new PathGenerator(openapi);
    }
    
    public static String getHostFromOasDocument(final OasDocument openapi) {
        String host = null;
        if (openapi instanceof Oas20Document) {
            host = ((Oas20Document)openapi).host;
        } else if (openapi instanceof Oas30Document) {
            if (((Oas30Document)openapi).getServers() != null 
                && ((Oas30Document)openapi).getServers().get(0) != null) {
                try {
                    URL serverUrl = new URL(parseVariables(((Oas30Document)openapi).getServers().get(0).url, (Oas30Server)((Oas30Document)openapi).getServers().get(0)));
                    host = serverUrl.getHost();
                
                } catch (MalformedURLException e) {
                    LOG.info("error when parsing OpenApi 3.0 doc server url", e);
                }
            }
        }
        return host;
        
    }
    
    public static String getBasePathFromOasDocument(final OasDocument openapi) {
        String basePath = null;
        if (openapi instanceof Oas20Document) {
            basePath = ((Oas20Document)openapi).basePath;
        } else if (openapi instanceof Oas30Document) {
            if (((Oas30Document)openapi).getServers() != null 
                && ((Oas30Document)openapi).getServers().get(0) != null) {
                try {
                    Oas30Server server = (Oas30Server)((Oas30Document)openapi).getServers().get(0);
                    if (server.variables != null && server.variables.get("basePath") != null) {
                        basePath = server.variables.get("basePath").default_;
                    }
                    if (basePath == null) {
                        // parse server url as fallback
                        URL serverUrl = new URL(parseVariables(((Oas30Document)openapi).getServers().get(0).url, server));
                        basePath = serverUrl.getPath();
                        if (basePath.indexOf("//") == 0) {
                            // strip off the first "/" if double "/" exists
                            basePath = basePath.substring(1);
                        }
                        if ("/".equals(basePath)) {
                            basePath = "";
                        }
                    } 
                                    
                } catch (MalformedURLException e) {
                    //not a valid whole url, just the basePath
                    basePath = ((Oas30Document)openapi).getServers().get(0).url;
                }
            }
            
        }
        return basePath;
        
    }
 
    public static String parseVariables(String url, Oas30Server server) {
        Pattern p = Pattern.compile("\\{(.*?)\\}");
        Matcher m = p.matcher(url);
        while (m.find()) {
           
            String var = m.group(1);
            if (server != null && server.variables != null && server.variables.get(var) != null) {
                String varValue = server.variables.get(var).default_;
                url = url.replace("{" + var + "}", varValue);
            }
        }
        return url;
    }
}
