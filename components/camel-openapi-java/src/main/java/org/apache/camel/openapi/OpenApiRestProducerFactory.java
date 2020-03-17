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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.datamodels.Library;
import io.apicurio.datamodels.openapi.models.OasDocument;
import io.apicurio.datamodels.openapi.models.OasOperation;
import io.apicurio.datamodels.openapi.models.OasParameter;
import io.apicurio.datamodels.openapi.models.OasPathItem;
import io.apicurio.datamodels.openapi.models.OasResponse;
import io.apicurio.datamodels.openapi.v2.models.Oas20Document;
import io.apicurio.datamodels.openapi.v2.models.Oas20Operation;
import io.apicurio.datamodels.openapi.v3.models.Oas30Operation;
import io.apicurio.datamodels.openapi.v3.models.Oas30Response;
import org.apache.camel.CamelContext;
import org.apache.camel.Producer;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestProducerFactory;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.util.CollectionStringBuffer;
import org.apache.camel.util.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.support.ResourceHelper.resolveMandatoryResourceAsInputStream;

public class OpenApiRestProducerFactory implements RestProducerFactory {

    private static final Logger LOG = LoggerFactory.getLogger(OpenApiRestProducerFactory.class);

    @Override
    public Producer createProducer(CamelContext camelContext, String host,
                                   String verb, String basePath, String uriTemplate, String queryParameters,
                                   String consumes, String produces, RestConfiguration configuration, Map<String, Object> parameters) throws Exception {

        String apiDoc = (String) parameters.get("apiDoc");
        // load json model
        if (apiDoc == null) {
            throw new IllegalArgumentException("OpenApi api-doc must be configured using the apiDoc option");
        }

        String path = uriTemplate != null ? uriTemplate : basePath;
        // path must start with a leading slash
        if (!path.startsWith("/")) {
            path = "/" + path;
        }

        OasDocument openApi = loadOpenApiModel(camelContext, apiDoc);
        OasOperation operation = getOpenApiOperation(openApi, verb, path);
        if (operation == null) {
            throw new IllegalArgumentException("OpenApi api-doc does not contain operation for " + verb + ":" + path);
        }

        // validate if we have the query parameters also
        if (queryParameters != null) {
            for (OasParameter param : operation.parameters) {
                if ("query".equals(param.in) && param.required) {
                    // check if we have the required query parameter defined
                    String key = param.getName();
                    String token = key + "=";
                    boolean hasQuery = queryParameters.contains(token);
                    if (!hasQuery) {
                        throw new IllegalArgumentException("OpenApi api-doc does not contain query parameter " + key + " for " + verb + ":" + path);
                    }
                }
            }
        }

        String componentName = (String) parameters.get("componentName");

        Producer producer = createHttpProducer(camelContext, openApi, operation, host, verb, path, queryParameters,
                produces, consumes, componentName, parameters);
        return producer;
    }

    OasDocument loadOpenApiModel(CamelContext camelContext, String apiDoc) throws Exception {
        InputStream is = resolveMandatoryResourceAsInputStream(camelContext, apiDoc);
        final ObjectMapper mapper = new ObjectMapper();
        try {
            final JsonNode node = mapper.readTree(is);
            LOG.debug("Loaded openApi api-doc:\n{}", node.toPrettyString());
            return (OasDocument)Library.readDocument(node);


        } finally {
            IOHelper.close(is);
        }


    }

    private OasOperation getOpenApiOperation(OasDocument openApi, String verb, String path) {
        // path may include base path so skip that
        String basePath = RestOpenApiSupport.getBasePathFromOasDocument(openApi);
        if (basePath != null && path.startsWith(basePath)) {
            path = path.substring(basePath.length());
        }

        OasPathItem modelPath = openApi.paths.getItem(path);
        if (modelPath == null) {
            return null;
        }

        // get,put,post,head,delete,patch,options
        OasOperation op = null;
        if ("get".equals(verb)) {
            op = modelPath.get;
        } else if ("put".equals(verb)) {
            op = modelPath.put;
        } else if ("post".equals(verb)) {
            op = modelPath.post;
        } else if ("head".equals(verb)) {
            op = modelPath.head;
        } else if ("delete".equals(verb)) {
            op = modelPath.delete;
        } else if ("patch".equals(verb)) {
            op = modelPath.patch;
        } else if ("options".equals(verb)) {
            op = modelPath.options;
        }
        return op;
    }

    private Producer createHttpProducer(CamelContext camelContext, OasDocument openApi, OasOperation operation,
                                        String host, String verb, String path, String queryParameters,
                                        String consumes, String produces,
                                        String componentName, Map<String, Object> parameters) throws Exception {

        LOG.debug("Using OpenApi operation: {} with {} {}", operation, verb, path);

        RestProducerFactory factory = (RestProducerFactory) parameters.remove("restProducerFactory");

        if (factory != null) {
            LOG.debug("Using RestProducerFactory: {}", factory);

            if (produces == null) {
                CollectionStringBuffer csb = new CollectionStringBuffer(",");
                List<String> list = new ArrayList<String>();
                if (operation instanceof Oas20Operation) {
                    list = ((Oas20Operation)operation).produces;
                } else if (operation instanceof Oas30Operation) {
                    Oas30Operation oas30Operation = (Oas30Operation)operation;
                    for (OasResponse response : oas30Operation.responses.getResponses()) {
                        Oas30Response oas30Response = (Oas30Response)response;
                        for (String ct : oas30Response.content.keySet()) {
                            list.add(ct);
                        }
                    }

                }
                if (list == null || list.isEmpty()) {
                    if (openApi instanceof Oas20Document) {
                        list = ((Oas20Document)openApi).produces;
                    }
                }
                if (list != null) {
                    for (String s : list) {
                        csb.append(s);
                    }
                }
                produces = csb.isEmpty() ? null : csb.toString();
            }
            if (consumes == null) {
                CollectionStringBuffer csb = new CollectionStringBuffer(",");
                List<String> list = new ArrayList<String>();
                if (operation instanceof Oas20Operation) {
                    list = ((Oas20Operation)operation).consumes;
                } else if (operation instanceof Oas30Operation) {
                    Oas30Operation oas30Operation = (Oas30Operation)operation;
                    if (oas30Operation.requestBody != null
                        && oas30Operation.requestBody.content != null) {
                        for (String ct : oas30Operation.requestBody.content.keySet()) {
                            list.add(ct);
                        }
                    }

                }
                if (list == null || list.isEmpty()) {
                    if (openApi instanceof Oas20Document) {
                        list = ((Oas20Document)openApi).consumes;
                    }
                }
                if (list != null) {
                    for (String s : list) {
                        csb.append(s);
                    }
                }
                consumes = csb.isEmpty() ? null : csb.toString();
            }

            String basePath = null;
            String uriTemplate = null;
            if (host == null) {

                //if no explicit host has been configured then use host and base path from the openApi api-doc
                host = RestOpenApiSupport.getHostFromOasDocument(openApi);
                basePath = RestOpenApiSupport.getBasePathFromOasDocument(openApi);
                uriTemplate = path;

            } else {
                // path includes also uri template
                basePath = path;
                uriTemplate = null;
            }

            RestConfiguration config = CamelContextHelper.getRestConfiguration(camelContext, componentName);
            return factory.createProducer(camelContext, host, verb, basePath, uriTemplate, queryParameters, consumes, produces, config, parameters);

        } else {
            throw new IllegalStateException("Cannot find RestProducerFactory in Registry or as a Component to use");
        }
    }
}
