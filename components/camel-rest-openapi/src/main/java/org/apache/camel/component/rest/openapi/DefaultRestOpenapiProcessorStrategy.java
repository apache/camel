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

package org.apache.camel.component.rest.openapi;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProducer;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.NonManagedService;
import org.apache.camel.Route;
import org.apache.camel.component.platform.http.PlatformHttpComponent;
import org.apache.camel.component.platform.http.spi.PlatformHttpConsumerAware;
import org.apache.camel.spi.PackageScanResourceResolver;
import org.apache.camel.spi.ProducerCache;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.cache.DefaultProducerCache;
import org.apache.camel.support.processor.RestBindingAdvice;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link RestOpenapiProcessorStrategy} that links the Rest DSL to routes called via direct:operationId.
 */
public class DefaultRestOpenapiProcessorStrategy extends ServiceSupport
        implements RestOpenapiProcessorStrategy, CamelContextAware, NonManagedService {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultRestOpenapiProcessorStrategy.class);

    private static final String BODY_VERBS = "DELETE,PUT,POST,PATCH";

    private static final String GEN_OPID = "GENOPID_";

    private CamelContext camelContext;
    private ProducerCache producerCache;
    private String component = "direct";
    private String missingOperation;
    private String mockIncludePattern;
    private final List<String> uris = new ArrayList<>();

    @Override
    public void validateOpenApi(OpenAPI openAPI, String basePath, PlatformHttpConsumerAware platformHttpConsumer)
            throws Exception {
        List<String> ids = new ArrayList<>();
        for (var e : openAPI.getPaths().entrySet()) {
            for (var o : e.getValue().readOperationsMap().entrySet()) {
                Operation op = o.getValue();
                String id =
                        op.getOperationId() != null ? op.getOperationId() : generateOperationId(e.getKey(), o.getKey());
                ids.add(component + "://" + id);
            }
        }
        // should have routes with all
        List<String> existing = new ArrayList<>();
        for (Route route : camelContext.getRoutes()) {
            String base = route.getEndpoint().getEndpointBaseUri();
            existing.add(base);
        }

        // all ids must have a route
        ids.removeAll(existing);
        if (!ids.isEmpty()) {
            String missing =
                    ids.stream().sorted().map(id -> id.replace("://", ":")).collect(Collectors.joining("\n\t"));
            String msg = String.format(
                    "OpenAPI specification has %d unmapped operations to corresponding routes: %n\t%s",
                    ids.size(), missing);

            if ("fail".equalsIgnoreCase(missingOperation)) {
                throw new IllegalArgumentException(msg);
            } else if ("ignore".equalsIgnoreCase(missingOperation)) {
                LOG.warn(msg + "\nThis validation error is ignored.");
            } else if ("mock".equalsIgnoreCase(missingOperation)) {
                LOG.debug(msg + "\nThis validation error is ignored (Will return a mocked/empty response).");
            }
        }

        // enlist open-api rest services
        PlatformHttpComponent phc = camelContext.getComponent("platform-http", PlatformHttpComponent.class);
        if (phc != null) {
            String path = basePath;
            if (path == null || path.isEmpty() || path.equals("/")) {
                path = "";
            }
            for (var p : openAPI.getPaths().entrySet()) {
                String uri = path + p.getKey();
                String verbs = p.getValue().readOperationsMap().keySet().stream()
                        .map(Enum::name)
                        .sorted()
                        .collect(Collectors.joining(","));
                String consumes = null;
                String produces = null;
                for (var o : p.getValue().readOperations()) {
                    if (o.getRequestBody() != null) {
                        Content c = o.getRequestBody().getContent();
                        if (c != null) {
                            consumes = c.keySet().stream().sorted().collect(Collectors.joining(","));
                        }
                    }
                    if (o.getResponses() != null) {
                        for (var a : o.getResponses().values()) {
                            Content c = a.getContent();
                            if (c != null) {
                                produces = c.keySet().stream().sorted().collect(Collectors.joining(","));
                            }
                        }
                    }
                }
                phc.addHttpEndpoint(uri, verbs, consumes, produces, platformHttpConsumer.getPlatformHttpConsumer());
                uris.add(uri);
            }
        }
    }

    /**
     * If the operation has no operationId specified, generate one based on the path and the operation method.
     *
     * @param  path       The path for this operation, such as /users.
     * @param  httpMethod The operation to perform
     * @return            A generated operation id based on the path and the operation. Slashes and braces in the path
     *                    are replaced with placeholder characters.
     */
    private String generateOperationId(String path, HttpMethod httpMethod) {
        final String sanitizedPath = path.replace('/', '.').replaceAll("[{}]", "_");
        final String opId = GEN_OPID + httpMethod.name() + sanitizedPath;
        LOG.debug("Generated operationId {} for path {} and method {}", opId, path, httpMethod.name());
        return opId;
    }

    @Override
    public boolean processApiSpecification(String specificationUri, Exchange exchange, AsyncCallback callback) {
        try {
            Resource res = PluginHelper.getResourceLoader(camelContext).resolveResource(specificationUri);
            if (res != null && res.exists()) {
                if (specificationUri.endsWith("json")) {
                    exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/json");
                } else if (specificationUri.endsWith("yaml") || specificationUri.endsWith("yml")) {
                    exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "text/yaml");
                }
                InputStream is = res.getInputStream();
                String data = IOHelper.loadText(is);
                exchange.getMessage().setBody(data);
                IOHelper.close(is);
            }
        } catch (Exception e) {
            exchange.setException(e);
        }
        callback.done(true);
        return true;
    }

    @Override
    public boolean process(
            OpenAPI openAPI,
            Operation operation,
            String verb,
            String path,
            RestBindingAdvice binding,
            Exchange exchange,
            AsyncCallback callback) {

        exchange.setProperty(Exchange.REST_OPENAPI, openAPI);

        if ("mock".equalsIgnoreCase(missingOperation) || "ignore".equalsIgnoreCase(missingOperation)) {
            // check if there is a route
            Endpoint e = camelContext.hasEndpoint(component + ":" + operation.getOperationId());
            if (e == null) {
                try {
                    var requestError = binding.doClientRequestValidation(exchange);
                    if (requestError != null) {
                        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, requestError.statusCode());
                        exchange.getMessage().setBody(requestError.body());
                        exchange.setRouteStop(true);
                    } else if ("mock".equalsIgnoreCase(missingOperation)) {
                        // no route then try to load mock data as the answer
                        loadMockData(operation, verb, path, exchange);
                    }
                    if (requestError == null) {
                        var responseError = binding.doClientResponseValidation(exchange);
                        if (responseError != null) {
                            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, responseError.statusCode());
                            exchange.getMessage().setBody(responseError.body());
                        }
                    }
                } catch (Exception ex) {
                    exchange.setException(ex);
                }
                callback.done(true);
                return true;
            }
        }

        // there is a route so process
        Map<String, Object> state;
        try {
            state = binding.before(exchange);
        } catch (Exception e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }

        final Endpoint e = camelContext.getEndpoint(component + ":" + operation.getOperationId());
        final AsyncProducer p = producerCache.acquireProducer(e);
        return p.process(exchange, doneSync -> {
            try {
                producerCache.releaseProducer(e, p);
                binding.after(exchange, state);
            } catch (Exception ex) {
                exchange.setException(ex);
            } finally {
                callback.done(doneSync);
            }
        });
    }

    private void loadMockData(Operation operation, String verb, String path, Exchange exchange) {
        final PackageScanResourceResolver resolver = PluginHelper.getPackageScanResourceResolver(camelContext);
        final String[] includes = mockIncludePattern != null ? mockIncludePattern.split(",") : null;

        boolean json = false;
        boolean xml = false;
        Resource found = null;
        if (includes != null) {
            Collection<Resource> accepted = new ArrayList<>();
            for (String include : includes) {
                try {
                    accepted.addAll(resolver.findResources(include));
                } catch (Exception e) {
                    // ignore as folder may not exist
                }
            }

            String ct = ExchangeHelper.getContentType(exchange);
            if (ct != null) {
                json = ct.contains("json");
                xml = ct.contains("xml");
            }

            for (Resource resource : accepted) {
                String target = FileUtil.stripFirstLeadingSeparator(path);
                String loc = FileUtil.stripExt(FileUtil.compactPath(resource.getLocation(), '/'));
                String onlyExt = FileUtil.onlyExt(resource.getLocation());
                boolean match = loc.endsWith(target);
                boolean matchExt = !json && !xml || json && onlyExt.equals("json") || xml && onlyExt.equals("xml");
                if (match && matchExt) {
                    found = resource;
                    json = onlyExt.equals("json");
                    xml = onlyExt.equals("xml");
                    break;
                }
            }
        }
        if (found != null) {
            try {
                // use the mock data as response
                if (json) {
                    exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/json");
                } else if (xml) {
                    exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "text/xml");
                }
                exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);
                exchange.getMessage().setBody(IOHelper.loadText(found.getInputStream()));
            } catch (Exception e) {
                // ignore
            }
        } else {
            // no mock data, so return data as-is for PUT,POST,DELETE,PATCH
            if (BODY_VERBS.contains(verb)) {
                // return input data as-is
                exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);
            } else {
                // no mock data (such as for GET)
                // then try to see if there is an example in the openapi spec response we can use,
                // otherwise use an empty body
                Object body = "";

                String contentType = ExchangeHelper.getContentType(exchange);
                String accept = exchange.getMessage().getHeader("Accept", String.class);
                if (operation.getResponses() != null) {
                    ApiResponse a = operation.getResponses().get("200");
                    Content c = a.getContent();
                    if (c != null && !c.isEmpty()) {
                        // prefer media-type that is the same as the incoming content-type
                        // if none found, then find first matching content-type from the HTTP Accept header
                        MediaType mt = contentType != null ? c.get(contentType) : null;
                        if (mt == null && accept != null) {
                            // find best match accept
                            for (String acc : accept.split(",")) {
                                acc = StringHelper.before(acc, ";", acc);
                                acc = acc.trim();
                                mt = c.get(acc);
                                if (mt != null) {
                                    // update content-type
                                    contentType = acc;
                                    break;
                                }
                            }
                            // fallback to grab json or xml if we accept anything
                            if (mt == null && "*/*".equals(accept)) {
                                mt = c.get("application/json");
                                if (mt != null) {
                                    contentType = "application/json";
                                }
                            }
                            // fallback to grab json or xml if we accept anything
                            if (mt == null && "*/*".equals(accept)) {
                                mt = c.get("application/xml");
                                if (mt != null) {
                                    contentType = "application/xml";
                                }
                            }
                        }
                        if (mt != null) {
                            if (mt.getExample() != null) {
                                body = mt.getExample();
                            } else if (mt.getExamples() != null) {
                                // grab first example
                                Example ex =
                                        mt.getExamples().values().iterator().next();
                                body = ex.getValue();
                            }
                        }
                    }
                }
                boolean empty = body == null || body.toString().isBlank();
                if (empty) {
                    exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 204);
                    exchange.getMessage().setBody("");
                } else {
                    exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);
                    exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, contentType);
                    exchange.getMessage().setBody(body);
                }
            }
        }
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public String getComponent() {
        return component;
    }

    /**
     * Name of component to use for processing the Rest DSL requests.
     */
    public void setComponent(String component) {
        this.component = component;
    }

    @Override
    public String getMissingOperation() {
        return missingOperation;
    }

    @Override
    public void setMissingOperation(String missingOperation) {
        this.missingOperation = missingOperation;
    }

    @Override
    public String getMockIncludePattern() {
        return mockIncludePattern;
    }

    @Override
    public void setMockIncludePattern(String mockIncludePattern) {
        this.mockIncludePattern = mockIncludePattern;
    }

    @Override
    protected void doInit() throws Exception {
        producerCache = new DefaultProducerCache(this, getCamelContext(), 1000);
        ServiceHelper.initService(producerCache);

        // automatic adjust missing operation to fail, and ignore if you use developer mode
        if (missingOperation == null) {
            boolean dev = "dev"
                    .equalsIgnoreCase(camelContext.getCamelContextExtension().getProfile());
            missingOperation = dev ? "mock" : "fail";
        }
    }

    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startService(producerCache);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(producerCache);

        if (camelContext != null) {
            PlatformHttpComponent phc = (PlatformHttpComponent) camelContext.hasComponent("platform-http");
            if (phc != null) {
                uris.forEach(phc::removeHttpEndpoint);
                uris.clear();
            }
        }
    }
}
