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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RouteAware;
import org.apache.camel.StartupStep;
import org.apache.camel.component.platform.http.spi.PlatformHttpConsumerAware;
import org.apache.camel.http.base.HttpHelper;
import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.StartupStepRecorder;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.RestConsumerContextPathMatcher;
import org.apache.camel.support.processor.DelegateAsyncProcessor;
import org.apache.camel.support.processor.RestBindingAdvice;
import org.apache.camel.support.processor.RestBindingAdviceFactory;
import org.apache.camel.support.processor.RestBindingConfiguration;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestOpenApiProcessor extends DelegateAsyncProcessor implements CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(RestOpenApiProcessor.class);

    // just use the most common verbs
    private static final List<String> METHODS = Arrays.asList("GET", "HEAD", "POST", "PUT", "DELETE", "PATCH");

    private CamelContext camelContext;
    private final RestOpenApiEndpoint endpoint;
    private final OpenAPI openAPI;
    private final String basePath;
    private final String apiContextPath;
    private final List<RestConsumerContextPathMatcher.ConsumerPath<Operation>> paths = new ArrayList<>();
    private final RestOpenapiProcessorStrategy restOpenapiProcessorStrategy;
    private final AtomicBoolean packageScanInit = new AtomicBoolean();
    private final Set<Class<?>> scannedClasses = new HashSet<>();
    private PlatformHttpConsumerAware platformHttpConsumer;
    private Consumer consumer;

    public RestOpenApiProcessor(RestOpenApiEndpoint endpoint, OpenAPI openAPI, String basePath, String apiContextPath,
                                Processor processor, RestOpenapiProcessorStrategy restOpenapiProcessorStrategy) {
        super(processor);
        this.endpoint = endpoint;
        this.basePath = basePath;
        // ensure starts with leading slash
        this.apiContextPath = apiContextPath != null && !apiContextPath.startsWith("/") ? "/" + apiContextPath : apiContextPath;
        this.openAPI = openAPI;
        this.restOpenapiProcessorStrategy = restOpenapiProcessorStrategy;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public PlatformHttpConsumerAware getPlatformHttpConsumer() {
        return platformHttpConsumer;
    }

    public void setPlatformHttpConsumer(PlatformHttpConsumerAware platformHttpConsumer) {
        this.platformHttpConsumer = platformHttpConsumer;
    }

    public Consumer getConsumer() {
        return consumer;
    }

    public void setConsumer(Consumer consumer) {
        this.consumer = consumer;
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        // use HTTP_URI as this works for all runtimes
        String uri = exchange.getMessage().getHeader(Exchange.HTTP_URI, String.class);
        if (uri != null) {
            uri = URISupport.stripQuery(uri);
        }
        if (uri != null && uri.startsWith(basePath)) {
            uri = uri.substring(basePath.length());
        }
        String verb = exchange.getMessage().getHeader(Exchange.HTTP_METHOD, String.class);

        RestConsumerContextPathMatcher.ConsumerPath<Operation> m
                = RestConsumerContextPathMatcher.matchBestPath(verb, uri, paths);
        if (m instanceof RestOpenApiConsumerPath rcp) {
            Operation o = rcp.getConsumer();

            String consumerPath = rcp.getConsumerPath();

            //if uri is not starting with slash then remove the slash in the consumerPath from the openApi spec
            if (consumerPath.startsWith("/") && uri != null && !uri.startsWith("/")) {
                consumerPath = consumerPath.substring(1);
            }

            // map path-parameters from operation to camel headers
            HttpHelper.evalPlaceholders(exchange.getMessage().getHeaders(), uri, consumerPath);

            // process the incoming request
            return restOpenapiProcessorStrategy.process(openAPI, o, verb, uri, rcp.getBinding(), exchange, callback);
        }

        // is it the api-context path
        if (uri != null && uri.equals(apiContextPath)) {
            return restOpenapiProcessorStrategy.processApiSpecification(endpoint.getSpecificationUri(), exchange, callback);
        }

        // okay we cannot process this requires so return either 404 or 405.
        // to know if its 405 then we need to check if any other HTTP method would have a consumer for the "same" request
        final String contextPath = uri;
        List<String> allow = METHODS.stream()
                .filter(v -> RestConsumerContextPathMatcher.matchBestPath(v, contextPath, paths) != null).toList();
        if (allow.isEmpty()) {
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 404);
        } else {
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 405);
            // include list of allowed VERBs
            exchange.getMessage().setHeader("Allow", String.join(", ", allow));
        }
        exchange.setRouteStop(true);
        callback.done(true);
        return true;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        CamelContextAware.trySetCamelContext(restOpenapiProcessorStrategy, getCamelContext());

        // register all openapi paths
        for (var e : openAPI.getPaths().entrySet()) {
            String path = e.getKey(); // path
            for (var o : e.getValue().readOperationsMap().entrySet()) {
                String v = o.getKey().name(); // verb
                // create per operation binding
                RestBindingConfiguration bc = createRestBindingConfiguration(o.getValue());

                String url = basePath + path;
                if (platformHttpConsumer != null) {
                    url = platformHttpConsumer.getPlatformHttpConsumer().getEndpoint().getServiceUrl() + url;
                }

                String desc = o.getValue().getSummary();
                if (desc != null && desc.isBlank()) {
                    desc = null;
                }
                String routeId = null;
                if (consumer instanceof RouteAware ra) {
                    routeId = ra.getRoute().getRouteId();
                }
                camelContext.getRestRegistry().addRestService(consumer, true, url, path, basePath, null, v, bc.getConsumes(),
                        bc.getProduces(), bc.getType(), bc.getOutType(), routeId, desc);

                RestBindingAdvice binding = RestBindingAdviceFactory.build(camelContext, bc);
                RestBindingAdviceFactory.build(camelContext, bc);

                ServiceHelper.buildService(binding);
                paths.add(new RestOpenApiConsumerPath(v, path, o.getValue(), binding));
            }
        }
        scannedClasses.clear(); // no longer needed

        restOpenapiProcessorStrategy.setMissingOperation(endpoint.getMissingOperation());
        restOpenapiProcessorStrategy.setMockIncludePattern(endpoint.getMockIncludePattern());
        ServiceHelper.initService(restOpenapiProcessorStrategy);

        // validate openapi contract
        restOpenapiProcessorStrategy.validateOpenApi(openAPI, platformHttpConsumer);
    }

    private RestBindingConfiguration createRestBindingConfiguration(Operation o) throws Exception {
        RestConfiguration config = camelContext.getRestConfiguration();
        RestConfiguration.RestBindingMode mode = config.getBindingMode();

        RestBindingConfiguration bc = new RestBindingConfiguration();
        bc.setBindingMode(mode.name());
        bc.setEnableCORS(config.isEnableCORS());
        bc.setCorsHeaders(config.getCorsHeaders());
        bc.setClientRequestValidation(config.isClientRequestValidation() || endpoint.isClientRequestValidation());
        bc.setEnableNoContentResponse(config.isEnableNoContentResponse());
        bc.setSkipBindingOnErrorCode(config.isSkipBindingOnErrorCode());

        String consumes = endpoint.getConsumes();
        String produces = endpoint.getProduces();
        // the operation may have specific information what it can consume
        if (o.getRequestBody() != null) {
            Content c = o.getRequestBody().getContent();
            if (c != null) {
                consumes = c.keySet().stream().sorted().collect(Collectors.joining(","));
            }
        }
        // the operation may have specific information what it can produce
        if (o.getResponses() != null) {
            HashSet<String> mediaTypes = new HashSet<>();
            for (var a : o.getResponses().values()) {
                Content c = a.getContent();
                if (c != null) {
                    mediaTypes.addAll(c.keySet());
                }
            }

            if (!mediaTypes.isEmpty()) {
                produces = mediaTypes.stream().sorted().collect(Collectors.joining(","));
            }
        }
        bc.setConsumes(consumes);
        bc.setProduces(produces);

        boolean requiredBody = false;
        if (o.getRequestBody() != null) {
            requiredBody = Boolean.TRUE == o.getRequestBody().getRequired();
        }
        bc.setRequiredBody(requiredBody);

        Set<String> requiredQueryParameters = null;
        if (o.getParameters() != null) {
            requiredQueryParameters = o.getParameters().stream()
                    .filter(p -> "query".equals(p.getIn()))
                    .filter(p -> Boolean.TRUE == p.getRequired())
                    .map(Parameter::getName)
                    .collect(Collectors.toSet());
        }
        if (requiredQueryParameters != null) {
            bc.setRequiredQueryParameters(requiredQueryParameters);
        }

        Set<String> requiredHeaders = null;
        if (o.getParameters() != null) {
            requiredHeaders = o.getParameters().stream()
                    .filter(p -> "header".equals(p.getIn()))
                    .filter(p -> Boolean.TRUE == p.getRequired())
                    .map(Parameter::getName)
                    .collect(Collectors.toSet());
        }
        if (requiredHeaders != null) {
            bc.setRequiredHeaders(requiredHeaders);
        }
        Map<String, String> defaultQueryValues = null;
        if (o.getParameters() != null) {
            defaultQueryValues = o.getParameters().stream()
                    .filter(p -> "query".equals(p.getIn()))
                    .filter(p -> p.getSchema() != null)
                    .filter(p -> p.getSchema().getDefault() != null)
                    .collect(Collectors.toMap(Parameter::getName, p -> p.getSchema().getDefault().toString()));
        }
        if (defaultQueryValues != null) {
            bc.setQueryDefaultValues(defaultQueryValues);
        }

        // input and output types binding to java classes
        if (o.getRequestBody() != null) {
            Content c = o.getRequestBody().getContent();
            if (c != null) {
                for (var m : c.entrySet()) {
                    String mt = m.getKey();
                    if (mt.contains("json") || mt.contains("xml")) {
                        Schema s = m.getValue().getSchema();
                        // $ref is null, so we need to know the schema name via XML
                        if (s != null && s.getXml() != null) {
                            String ref = s.getXml().getName();
                            boolean array = "array".equals(s.getType());
                            if (ref != null) {
                                Class<?> clazz = loadBindingClass(camelContext, ref);
                                if (clazz != null) {
                                    String name = clazz.getName();
                                    if (array) {
                                        name = name + "[]";
                                    }
                                    bc.setType(name);
                                    break; // okay set this just once
                                }
                            }
                        }
                    }
                }
            }
        }
        if (o.getResponses() != null) {
            for (var a : o.getResponses().values()) {
                Content c = a.getContent();
                if (c != null) {
                    for (var m : c.entrySet()) {
                        String mt = m.getKey();
                        if (mt.contains("json") || mt.contains("xml")) {
                            Schema s = m.getValue().getSchema();
                            // $ref is null, so we need to know the schema name via XML
                            if (s != null && s.getXml() != null) {
                                String ref = s.getXml().getName();
                                boolean array = "array".equals(s.getType());
                                if (ref != null) {
                                    Class<?> clazz = loadBindingClass(camelContext, ref);
                                    if (clazz != null) {
                                        String name = clazz.getName();
                                        if (array) {
                                            name = name + "[]";
                                        }
                                        bc.setOutType(name);
                                        break; // okay set this just once
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return bc;
    }

    private Class<?> loadBindingClass(CamelContext camelContext, String ref) {
        if (ref == null) {
            return null;
        }

        if (packageScanInit.compareAndSet(false, true)) {
            String base = endpoint.getBindingPackageScan();
            if (base != null) {
                StartupStepRecorder recorder = camelContext.getCamelContextExtension().getStartupStepRecorder();
                StartupStep step = recorder.beginStep(RestOpenApiProcessor.class, "openapi-binding",
                        "OpenAPI binding classes package scan");
                String[] pcks = base.split(",");
                PackageScanClassResolver resolver = PluginHelper.getPackageScanClassResolver(camelContext);
                // just add all classes as the POJOs can be generated with all kind of tools and with and without annotations
                scannedClasses.addAll(resolver.findImplementations(Object.class, pcks));
                if (!scannedClasses.isEmpty()) {
                    LOG.info("Binding package scan found {} classes in packages: {}", scannedClasses.size(), base);
                }
                recorder.endStep(step);
            }
        }

        // must refer to a class name, so upper case
        ref = Character.toUpperCase(ref.charAt(0)) + ref.substring(1);
        // find class via simple name
        for (Class<?> clazz : scannedClasses) {
            if (clazz.getSimpleName().equals(ref)) {
                return clazz;
            }
        }

        // class not found
        return null;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        ServiceHelper.startService(restOpenapiProcessorStrategy);
        for (var p : paths) {
            if (p instanceof RestOpenApiConsumerPath rcp) {
                ServiceHelper.startService(rcp.getBinding());
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        ServiceHelper.stopService(restOpenapiProcessorStrategy);
        for (var p : paths) {
            if (p instanceof RestOpenApiConsumerPath rcp) {
                ServiceHelper.stopService(rcp.getBinding());
            }
        }
        paths.clear();
    }
}
