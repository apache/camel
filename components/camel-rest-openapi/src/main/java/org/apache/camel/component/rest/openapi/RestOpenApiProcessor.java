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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.StartupStep;
import org.apache.camel.component.platform.http.spi.PlatformHttpConsumerAware;
import org.apache.camel.http.base.HttpHelper;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.DataTypeAware;
import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.StartupStepRecorder;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.RestConsumerContextPathMatcher;
import org.apache.camel.support.processor.DelegateAsyncProcessor;
import org.apache.camel.support.processor.RestBindingAdvice;
import org.apache.camel.support.processor.RestBindingAdviceFactory;
import org.apache.camel.support.processor.RestBindingConfiguration;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.support.http.RestUtil.isValidOrAcceptedContentType;

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

            // binding mode
            RestConfiguration config = camelContext.getRestConfiguration();
            RestConfiguration.RestBindingMode bindingMode = config.getBindingMode();

            // map path-parameters from operation to camel headers
            HttpHelper.evalPlaceholders(exchange.getMessage().getHeaders(), uri, rcp.getConsumerPath());

            // we have found the op to call, but if validation is enabled then we need
            // to validate the incoming request first
            if (endpoint.isClientRequestValidation() && isInvalidClientRequest(exchange, callback, o, bindingMode)) {
                // okay some validation error so return true
                return true;
            }

            // process the incoming request
            return restOpenapiProcessorStrategy.process(o, uri, rcp.getBinding(), exchange, callback);
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

    /**
     * Checks if the incoming request is invalid (has some error) according to the OpenAPI operation that is intended to
     * be invoked.
     *
     * @return true if some validation error and should stop routing
     */
    protected boolean isInvalidClientRequest(
            Exchange exchange, AsyncCallback callback, Operation o, RestConfiguration.RestBindingMode bindingMode) {

        // this code is similar to logic in camel-core (RestBindingAdvice) for rest-dsl with code-first approach

        boolean isXml = false;
        boolean isJson = false;
        String contentType = ExchangeHelper.getContentType(exchange);
        if (contentType != null) {
            isXml = contentType.toLowerCase(Locale.ENGLISH).contains("xml");
            isJson = contentType.toLowerCase(Locale.ENGLISH).contains("json");
        }
        String accept = exchange.getMessage().getHeader("Accept", String.class);

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
            for (var a : o.getResponses().values()) {
                Content c = a.getContent();
                if (c != null) {
                    produces = c.keySet().stream().sorted().collect(Collectors.joining(","));
                }
            }
        }
        // if content type could not tell us if it was json or xml, then fallback to if the binding was configured with
        // that information in the consumes
        if (!isXml && !isJson) {
            isXml = consumes != null && consumes.toLowerCase(Locale.ENGLISH).contains("xml");
            isJson = consumes != null && consumes.toLowerCase(Locale.ENGLISH).contains("json");
        }

        // set data type if in use
        if (exchange.getContext().isUseDataType()) {
            if (exchange.getIn() instanceof DataTypeAware && (isJson || isXml)) {
                ((DataTypeAware) exchange.getIn()).setDataType(new DataType(isJson ? "json" : "xml"));
            }
        }

        // check if the content-type is accepted according to consumes
        if (!isValidOrAcceptedContentType(consumes, contentType)) {
            LOG.trace("Consuming content type does not match contentType header {}. Stopping routing.", contentType);
            // the content-type is not something we can process so its a HTTP_ERROR 415
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 415);
            // set empty response body as http error code indicate the problem
            exchange.getMessage().setBody(null);
            // stop routing and return
            exchange.setRouteStop(true);
            callback.done(true);
            return true;
        }
        // check if what is produces is accepted by the client
        if (!isValidOrAcceptedContentType(produces, accept)) {
            LOG.trace("Produced content type does not match accept header {}. Stopping routing.", contentType);
            // the response type is not accepted by the client so its a HTTP_ERROR 406
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 406);
            // set empty response body as http error code indicate the problem
            exchange.getMessage().setBody(null);
            // stop routing and return
            exchange.setRouteStop(true);
            callback.done(true);
            return true;
        }

        // only allow xml/json if the binding mode allows that
        isXml &= bindingMode.equals(RestConfiguration.RestBindingMode.auto)
                || bindingMode.equals(RestConfiguration.RestBindingMode.xml)
                || bindingMode.equals(RestConfiguration.RestBindingMode.json_xml);
        isJson &= bindingMode.equals(RestConfiguration.RestBindingMode.auto)
                || bindingMode.equals(RestConfiguration.RestBindingMode.json)
                || bindingMode.equals(RestConfiguration.RestBindingMode.json_xml);

        // if we do not yet know if its xml or json, then use the binding mode to know the mode
        if (!isJson && !isXml) {
            isXml = bindingMode.equals(RestConfiguration.RestBindingMode.auto)
                    || bindingMode.equals(RestConfiguration.RestBindingMode.xml)
                    || bindingMode.equals(RestConfiguration.RestBindingMode.json_xml);
            isJson = bindingMode.equals(RestConfiguration.RestBindingMode.auto)
                    || bindingMode.equals(RestConfiguration.RestBindingMode.json)
                    || bindingMode.equals(RestConfiguration.RestBindingMode.json_xml);
        }
        boolean requiredBody = false;
        if (o.getRequestBody() != null) {
            requiredBody = Boolean.TRUE == o.getRequestBody().getRequired();
        }
        if (requiredBody) {
            String body = null;
            if (exchange.getIn().getBody() != null) {
                // okay we have a binding mode, so need to check for empty body as that can cause the marshaller to fail
                // as they assume a non-empty body
                if (isXml || isJson) {
                    // we have binding enabled, so we need to know if there body is empty or not
                    // so force reading the body as a String which we can work with
                    body = MessageHelper.extractBodyAsString(exchange.getIn());
                    if (body != null) {
                        if (exchange.getIn() instanceof DataTypeAware) {
                            ((DataTypeAware) exchange.getIn()).setBody(body, new DataType(isJson ? "json" : "xml"));
                        } else {
                            exchange.getIn().setBody(body);
                        }
                        if (isXml && isJson) {
                            // we have still not determined between xml or json, so check the body if its xml based or not
                            isXml = body.startsWith("<");
                            isJson = !isXml;
                        }
                    }
                }
            }
            // the body is required so we need to know if we have a body or not
            // so force reading the body as a String which we can work with
            if (body == null) {
                body = MessageHelper.extractBodyAsString(exchange.getIn());
                if (ObjectHelper.isNotEmpty(body)) {
                    exchange.getIn().setBody(body);
                }
            }
            if (ObjectHelper.isEmpty(body)) {
                // this is a bad request, the client did not include a message body
                exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 400);
                exchange.getMessage().setBody("The request body is missing.");
                // stop routing and return
                exchange.setRouteStop(true);
                callback.done(true);
                return true;
            }
        }
        Map<String, Parameter> requiredQueryParameters = null;
        if (o.getParameters() != null) {
            requiredQueryParameters = o.getParameters().stream()
                    .filter(p -> "query".equals(p.getIn()))
                    .filter(p -> Boolean.TRUE == p.getRequired())
                    .collect(Collectors.toMap(Parameter::getName, Function.identity()));
        }
        if (requiredQueryParameters != null
                && !exchange.getIn().getHeaders().keySet().containsAll(requiredQueryParameters.keySet())) {
            // this is a bad request, the client did not include some required query parameters
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 400);
            exchange.getMessage().setBody("Some of the required query parameters are missing.");
            // stop routing and return
            exchange.setRouteStop(true);
            callback.done(true);
            return true;
        }
        Map<String, Parameter> requiredHeaders = null;
        if (o.getParameters() != null) {
            requiredHeaders = o.getParameters().stream()
                    .filter(p -> "header".equals(p.getIn()))
                    .filter(p -> Boolean.TRUE == p.getRequired())
                    .collect(Collectors.toMap(Parameter::getName, Function.identity()));
        }
        if (requiredHeaders != null && !exchange.getIn().getHeaders().keySet().containsAll(requiredHeaders.keySet())) {
            // this is a bad request, the client did not include some required http headers
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 400);
            exchange.getMessage().setBody("Some of the required HTTP headers are missing.");
            // stop routing and return
            exchange.setRouteStop(true);
            callback.done(true);
            return true;
        }
        Map<String, List> allowedValues = null;
        if (o.getParameters() != null) {
            allowedValues = o.getParameters().stream()
                    .filter(p -> "query".equals(p.getIn()))
                    .filter(p -> p.getSchema() != null)
                    .filter(p -> p.getSchema().getEnum() != null)
                    .collect(Collectors.toMap(Parameter::getName, e -> e.getSchema().getEnum()));
        }
        if (allowedValues != null) {
            for (var e : allowedValues.entrySet()) {
                String k = e.getKey();
                Object v = exchange.getMessage().getHeader(k);
                if (v != null) {
                    if (e.getValue().stream().noneMatch(v::equals)) {
                        // this is a bad request, the client did not include some required query parameters
                        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 400);
                        exchange.getMessage().setBody("Some of the query parameters or HTTP headers has a not-allowed value.");
                        // stop routing and return
                        exchange.setRouteStop(true);
                        callback.done(true);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    protected void doBuild() throws Exception {
        super.doBuild();

        CamelContextAware.trySetCamelContext(restOpenapiProcessorStrategy, getCamelContext());

        // register all openapi paths
        for (var e : openAPI.getPaths().entrySet()) {
            String path = e.getKey(); // path
            for (var o : e.getValue().readOperationsMap().entrySet()) {
                String v = o.getKey().name(); // verb
                // create per operation binding
                RestBindingAdvice binding = createRestBinding(o.getValue());
                ServiceHelper.buildService(binding);
                paths.add(new RestOpenApiConsumerPath(v, path, o.getValue(), binding));
            }
        }
        scannedClasses.clear(); // no longer needed
        ServiceHelper.buildService(restOpenapiProcessorStrategy);
    }

    private RestBindingAdvice createRestBinding(Operation o) throws Exception {
        RestConfiguration config = camelContext.getRestConfiguration();
        RestConfiguration.RestBindingMode mode = config.getBindingMode();

        RestBindingConfiguration bc = new RestBindingConfiguration();
        bc.setBindingMode(mode.name());
        bc.setEnableCORS(config.isEnableCORS());
        bc.setCorsHeaders(config.getCorsHeaders());
        bc.setClientRequestValidation(config.isClientRequestValidation());
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
            for (var a : o.getResponses().values()) {
                Content c = a.getContent();
                if (c != null) {
                    produces = c.keySet().stream().sorted().collect(Collectors.joining(","));
                }
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

        return RestBindingAdviceFactory.build(camelContext, bc);
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
    protected void doInit() throws Exception {
        super.doInit();

        restOpenapiProcessorStrategy.setMissingOperation(endpoint.getMissingOperation());
        restOpenapiProcessorStrategy.setMockIncludePattern(endpoint.getMockIncludePattern());
        ServiceHelper.initService(restOpenapiProcessorStrategy);

        // validate openapi contract
        restOpenapiProcessorStrategy.validateOpenApi(openAPI, platformHttpConsumer);
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
