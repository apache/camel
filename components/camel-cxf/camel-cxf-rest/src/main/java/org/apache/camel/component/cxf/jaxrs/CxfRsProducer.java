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
package org.apache.camel.component.cxf.jaxrs;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.URLDecoder;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.InvocationCallback;
import jakarta.ws.rs.client.ResponseProcessingException;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.cxf.common.CxfOperationException;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.http.base.cookie.CookieHandler;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.LRUCache;
import org.apache.camel.support.LRUCacheFactory;
import org.apache.camel.util.ObjectHelper;
import org.apache.cxf.Bus;
import org.apache.cxf.jaxrs.JAXRSServiceFactoryBean;
import org.apache.cxf.jaxrs.client.Client;
import org.apache.cxf.jaxrs.client.JAXRSClientFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CxfRsProducer binds a Camel exchange to a CXF exchange, acts as a CXF JAXRS client, it will turn the normal Object
 * invocation to a RESTful request according to resource annotation. Any response will be bound to Camel exchange.
 */
public class CxfRsProducer extends DefaultAsyncProducer {

    private static final Logger LOG = LoggerFactory.getLogger(CxfRsProducer.class);

    private boolean throwException;

    // using a cache of factory beans instead of setting the address of a single cfb
    // to avoid concurrent issues
    private ClientFactoryBeanCache clientFactoryBeanCache;

    public CxfRsProducer(CxfRsEndpoint endpoint) {
        super(endpoint);
        this.throwException = endpoint.isThrowExceptionOnFailure();
        clientFactoryBeanCache = new ClientFactoryBeanCache(endpoint.getMaxClientCacheSize());
    }

    @Override
    protected void doStart() throws Exception {
        clientFactoryBeanCache.start();
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        clientFactoryBeanCache.stop();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Message inMessage = exchange.getIn();
        Boolean httpClientAPI = inMessage.getHeader(CxfConstants.CAMEL_CXF_RS_USING_HTTP_API, Boolean.class);
        // set the value with endpoint's option
        if (httpClientAPI == null) {
            httpClientAPI = ((CxfRsEndpoint) getEndpoint()).isHttpClientAPI();
        }
        if (httpClientAPI.booleanValue()) {
            invokeHttpClient(exchange);
        } else {
            invokeProxyClient(exchange);
        }
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {
            Message inMessage = exchange.getIn();
            Boolean httpClientAPI = inMessage.getHeader(CxfConstants.CAMEL_CXF_RS_USING_HTTP_API, Boolean.class);
            // set the value with endpoint's option
            if (httpClientAPI == null) {
                httpClientAPI = ((CxfRsEndpoint) getEndpoint()).isHttpClientAPI();
            }
            if (httpClientAPI.booleanValue()) {
                invokeAsyncHttpClient(exchange, callback);
            } else {
                invokeAsyncProxyClient(exchange, callback);
            }
            return false;
        } catch (Exception exception) {
            LOG.error("Error invoking request", exception);
            exchange.setException(exception);
            callback.done(true);
            return true;
        }
    }

    protected void invokeAsyncHttpClient(Exchange exchange, final AsyncCallback callback) throws Exception {
        Message inMessage = exchange.getIn();
        JAXRSClientFactoryBean cfb = clientFactoryBeanCache.get(CxfRsEndpointUtils
                .getEffectiveAddress(exchange, ((CxfRsEndpoint) getEndpoint()).getAddress()));
        Bus bus = ((CxfRsEndpoint) getEndpoint()).getBus();
        // We need to apply the bus setting from the CxfRsEndpoint which is not use the default bus
        if (bus != null) {
            cfb.setBus(bus);
        }
        WebClient client = cfb.createWebClient();
        ((CxfRsEndpoint) getEndpoint()).getChainedCxfRsEndpointConfigurer().configureClient(client);
        String httpMethod = inMessage.getHeader(CxfConstants.HTTP_METHOD, String.class);
        Class<?> responseClass = inMessage.getHeader(CxfConstants.CAMEL_CXF_RS_RESPONSE_CLASS, Class.class);
        Type genericType = inMessage.getHeader(CxfConstants.CAMEL_CXF_RS_RESPONSE_GENERIC_TYPE, Type.class);
        Object[] pathValues = inMessage.getHeader(CxfConstants.CAMEL_CXF_RS_VAR_VALUES, Object[].class);
        String path = inMessage.getHeader(CxfConstants.HTTP_PATH, String.class);

        if (LOG.isTraceEnabled()) {
            LOG.trace("HTTP method = {}", httpMethod);
            LOG.trace("path = {}", path);
            LOG.trace("responseClass = {}", responseClass);
        }

        // set the path
        if (path != null) {
            if (ObjectHelper.isNotEmpty(pathValues) && pathValues.length > 0) {
                client.path(path, pathValues);
            } else {
                client.path(path);
            }
        }

        CxfRsEndpoint cxfRsEndpoint = (CxfRsEndpoint) getEndpoint();
        CxfRsBinding binding = cxfRsEndpoint.getBinding();
        Object body = getBody(exchange, inMessage, httpMethod, cxfRsEndpoint, binding);
        setupClientMatrix(client, exchange);
        setupClientQueryAndHeaders(client, exchange);

        // ensure the CONTENT_TYPE header can be retrieved
        if (ObjectHelper.isEmpty(inMessage.getHeader(CxfConstants.CONTENT_TYPE, String.class))
                && ObjectHelper.isNotEmpty(client.getHeaders().get(CxfConstants.CONTENT_TYPE))) {
            inMessage.setHeader(CxfConstants.CONTENT_TYPE, client.getHeaders().get(CxfConstants.CONTENT_TYPE).get(0));
        }

        //Build message entity
        Entity<Object> entity = binding.bindCamelMessageToRequestEntity(body, inMessage, exchange, client);

        // handle cookies
        CookieHandler cookieHandler = ((CxfRsEndpoint) getEndpoint()).getCookieHandler();
        loadCookies(exchange, client, cookieHandler);

        // invoke the client
        if (responseClass == null || Response.class.equals(responseClass)) {
            client.async().method(httpMethod, entity,
                    new CxfInvocationCallback(client, exchange, cxfRsEndpoint, null, callback, null));
        } else {
            client.async().method(httpMethod, entity,
                    new CxfInvocationCallback(client, exchange, cxfRsEndpoint, responseClass, callback, genericType));
        }

    }

    protected void invokeAsyncProxyClient(Exchange exchange, final AsyncCallback callback) throws Exception {
        Message inMessage = exchange.getIn();
        Object[] varValues = inMessage.getHeader(CxfConstants.CAMEL_CXF_RS_VAR_VALUES, Object[].class);
        String methodName = inMessage.getHeader(CxfConstants.OPERATION_NAME, String.class);
        Client target;

        JAXRSClientFactoryBean cfb = clientFactoryBeanCache.get(CxfRsEndpointUtils
                .getEffectiveAddress(exchange, ((CxfRsEndpoint) getEndpoint()).getAddress()));
        Bus bus = ((CxfRsEndpoint) getEndpoint()).getBus();
        // We need to apply the bus setting from the CxfRsEndpoint which is not use the default bus
        if (bus != null) {
            cfb.setBus(bus);
        }
        if (varValues == null) {
            target = cfb.create();
        } else {
            target = cfb.createWithValues(varValues);
        }

        ((CxfRsEndpoint) getEndpoint()).getChainedCxfRsEndpointConfigurer().configureClient(target);

        setupClientHeaders(target, exchange);

        // find out the method which we want to invoke
        JAXRSServiceFactoryBean sfb = cfb.getServiceFactory();
        sfb.getResourceClasses();
        // check the null body first
        Object[] parameters = null;
        if (inMessage.getBody() != null) {
            parameters = inMessage.getBody(Object[].class);
        }
        // get the method
        Method method = findRightMethod(sfb.getResourceClasses(), methodName, getParameterTypes(parameters));

        CxfRsEndpoint cxfRsEndpoint = (CxfRsEndpoint) getEndpoint();
        final CxfProxyInvocationCallback invocationCallback
                = new CxfProxyInvocationCallback(target, exchange, cxfRsEndpoint, callback);
        WebClient.getConfig(target).getRequestContext().put(InvocationCallback.class.getName(), invocationCallback);

        // handle cookies
        CookieHandler cookieHandler = ((CxfRsEndpoint) getEndpoint()).getCookieHandler();
        loadCookies(exchange, target, cookieHandler);

        method.invoke(target, parameters);
    }

    @SuppressWarnings("unchecked")
    protected void setupClientQueryAndHeaders(WebClient client, Exchange exchange) throws Exception {
        Message inMessage = exchange.getIn();
        CxfRsEndpoint cxfRsEndpoint = (CxfRsEndpoint) getEndpoint();
        // check if there is a query map in the message header
        Map<String, String> maps = inMessage.getHeader(CxfConstants.CAMEL_CXF_RS_QUERY_MAP, Map.class);
        if (maps != null) {
            insertQueryParametersFromMap(client, maps);
        } else {
            String queryString = inMessage.getHeader(CxfConstants.HTTP_QUERY, String.class);
            if (queryString != null) {
                // Insert QueryParameters from HTTP_QUERY header
                insertQueryParametersFromQueryString(client, queryString, ExchangeHelper.getCharsetName(exchange));
            } else {
                insertQueryParametersFromMap(client, cxfRsEndpoint.getParameters());
            }
        }

        setupClientHeaders(client, exchange);
    }

    private void insertQueryParametersFromMap(WebClient client, Map<String, String> maps) {
        if (maps != null) {
            for (Map.Entry<String, String> entry : maps.entrySet()) {
                client.query(entry.getKey(), entry.getValue());
            }
        }
    }

    protected void setupClientMatrix(WebClient client, Exchange exchange) throws Exception {

        org.apache.cxf.message.Message cxfMessage
                = (org.apache.cxf.message.Message) exchange.getIn().getHeader(CxfConstants.CAMEL_CXF_MESSAGE);
        if (cxfMessage != null) {
            String requestURL = (String) cxfMessage.get("org.apache.cxf.request.uri");
            String matrixParam = null;
            int matrixStart = requestURL.indexOf(';');
            int matrixEnd = requestURL.indexOf('?') > -1 ? requestURL.indexOf('?') : requestURL.length();
            Map<String, String> maps = null;
            if (matrixStart > 0) {
                matrixParam = requestURL.substring(matrixStart + 1, matrixEnd);
                maps = getMatrixParametersFromMatrixString(matrixParam, ExchangeHelper.getCharsetName(exchange));
            }
            if (maps != null) {
                for (Map.Entry<String, String> entry : maps.entrySet()) {
                    client.matrix(entry.getKey(), entry.getValue());
                    LOG.debug("Matrix param {} :: {}", entry.getKey(), entry.getValue());
                }
            }
        }
    }

    protected void setupClientHeaders(Client client, Exchange exchange) throws Exception {
        Message inMessage = exchange.getIn();
        CxfRsEndpoint cxfRsEndpoint = (CxfRsEndpoint) getEndpoint();
        CxfRsBinding binding = cxfRsEndpoint.getBinding();
        // set headers
        client.headers(binding.bindCamelHeadersToRequestHeaders(inMessage.getHeaders(), exchange));
    }

    protected void invokeHttpClient(Exchange exchange) throws Exception {
        Message inMessage = exchange.getIn();
        JAXRSClientFactoryBean cfb = clientFactoryBeanCache.get(CxfRsEndpointUtils
                .getEffectiveAddress(exchange, ((CxfRsEndpoint) getEndpoint()).getAddress()));
        Bus bus = ((CxfRsEndpoint) getEndpoint()).getBus();
        // We need to apply the bus setting from the CxfRsEndpoint which is not use the default bus
        if (bus != null) {
            cfb.setBus(bus);
        }
        WebClient client = cfb.createWebClient();
        ((CxfRsEndpoint) getEndpoint()).getChainedCxfRsEndpointConfigurer().configureClient(client);
        String httpMethod = inMessage.getHeader(CxfConstants.HTTP_METHOD, String.class);
        Class<?> responseClass = inMessage.getHeader(CxfConstants.CAMEL_CXF_RS_RESPONSE_CLASS, Class.class);
        Type genericType = inMessage.getHeader(CxfConstants.CAMEL_CXF_RS_RESPONSE_GENERIC_TYPE, Type.class);
        Object[] pathValues = inMessage.getHeader(CxfConstants.CAMEL_CXF_RS_VAR_VALUES, Object[].class);
        String path = inMessage.getHeader(CxfConstants.HTTP_PATH, String.class);

        if (LOG.isTraceEnabled()) {
            LOG.trace("HTTP method = {}", httpMethod);
            LOG.trace("path = {}", path);
            LOG.trace("responseClass = {}", responseClass);
        }

        // set the path
        if (path != null) {
            if (ObjectHelper.isNotEmpty(pathValues) && pathValues.length > 0) {
                client.path(path, pathValues);
            } else {
                client.path(path);
            }
        }

        CxfRsEndpoint cxfRsEndpoint = (CxfRsEndpoint) getEndpoint();

        CxfRsBinding binding = cxfRsEndpoint.getBinding();

        Object body = getBody(exchange, inMessage, httpMethod, cxfRsEndpoint, binding);

        setupClientMatrix(client, exchange);

        setupClientQueryAndHeaders(client, exchange);

        // handle cookies
        CookieHandler cookieHandler = ((CxfRsEndpoint) getEndpoint()).getCookieHandler();
        loadCookies(exchange, client, cookieHandler);

        // invoke the client
        Object response = null;
        if (responseClass == null || Response.class.equals(responseClass)) {
            response = client.invoke(httpMethod, body);
        } else {
            if (Collection.class.isAssignableFrom(responseClass)) {
                if (genericType instanceof ParameterizedType) {
                    // Get the collection member type first
                    Type[] actualTypeArguments = ((ParameterizedType) genericType).getActualTypeArguments();
                    response = client.invokeAndGetCollection(httpMethod, body, (Class<?>) actualTypeArguments[0]);

                } else {
                    throw new CamelExchangeException(
                            "Header " + CxfConstants.CAMEL_CXF_RS_RESPONSE_GENERIC_TYPE + " not found in message", exchange);
                }
            } else {
                response = client.invoke(httpMethod, body, responseClass);
            }
        }
        int statesCode = client.getResponse().getStatus();
        // handle cookies
        saveCookies(exchange, client, cookieHandler);
        //Throw exception on a response > 207
        //http://en.wikipedia.org/wiki/List_of_HTTP_status_codes
        if (throwException) {
            if (response instanceof Response) {
                int respCode = ((Response) response).getStatus();
                if (respCode > 207) {
                    throw populateCxfRsProducerException(exchange, (Response) response, respCode);
                }
            }
        }
        // set response
        if (exchange.getPattern().isOutCapable()) {
            LOG.trace("Response body = {}", response);
            exchange.getOut().getHeaders().putAll(exchange.getIn().getHeaders());
            exchange.getMessage().setBody(binding.bindResponseToCamelBody(response, exchange));
            exchange.getMessage().getHeaders().putAll(binding.bindResponseHeadersToCamelHeaders(response, exchange));
            exchange.getMessage().setHeader(CxfConstants.HTTP_RESPONSE_CODE, statesCode);
        } else {
            // just close the input stream of the response object
            if (response instanceof Response) {
                ((Response) response).close();
            }
        }
    }

    private void saveCookies(Exchange exchange, Client client, CookieHandler cookieHandler) {
        if (cookieHandler != null) {
            CookieStore cookieStore = cookieHandler.getCookieStore(exchange);
            for (NewCookie newCookie : client.getResponse().getCookies().values()) {
                HttpCookie cookie = new HttpCookie(newCookie.getName(), newCookie.getValue());
                cookie.setComment(newCookie.getComment());
                cookie.setDomain(newCookie.getDomain());
                cookie.setHttpOnly(newCookie.isHttpOnly());
                cookie.setMaxAge(newCookie.getMaxAge());
                cookie.setPath(newCookie.getPath());
                cookie.setSecure(newCookie.isSecure());
                cookie.setVersion(newCookie.getVersion());
                cookieStore.add(client.getCurrentURI(), cookie);
            }
        }
    }

    private void loadCookies(Exchange exchange, Client client, CookieHandler cookieHandler) throws IOException {
        if (cookieHandler != null) {
            for (Map.Entry<String, List<String>> cookie : cookieHandler.loadCookies(exchange, client.getCurrentURI())
                    .entrySet()) {
                if (!cookie.getValue().isEmpty()) {
                    client.header(cookie.getKey(), cookie.getValue());
                }
            }
        }
    }

    protected void invokeProxyClient(Exchange exchange) throws Exception {
        Message inMessage = exchange.getIn();
        Object[] varValues = inMessage.getHeader(CxfConstants.CAMEL_CXF_RS_VAR_VALUES, Object[].class);
        String methodName = inMessage.getHeader(CxfConstants.OPERATION_NAME, String.class);
        Client target = null;

        JAXRSClientFactoryBean cfb = clientFactoryBeanCache.get(CxfRsEndpointUtils
                .getEffectiveAddress(exchange, ((CxfRsEndpoint) getEndpoint()).getAddress()));
        Bus bus = ((CxfRsEndpoint) getEndpoint()).getBus();
        // We need to apply the bus setting from the CxfRsEndpoint which is not use the default bus
        if (bus != null) {
            cfb.setBus(bus);
        }
        if (varValues == null) {
            target = cfb.create();
        } else {
            target = cfb.createWithValues(varValues);
        }

        ((CxfRsEndpoint) getEndpoint()).getChainedCxfRsEndpointConfigurer().configureClient(target);

        setupClientHeaders(target, exchange);

        // find out the method which we want to invoke
        JAXRSServiceFactoryBean sfb = cfb.getServiceFactory();
        sfb.getResourceClasses();
        // check the null body first
        Object[] parameters = null;
        if (inMessage.getBody() != null) {
            parameters = inMessage.getBody(Object[].class);
        }
        // get the method
        Method method = findRightMethod(sfb.getResourceClasses(), methodName, getParameterTypes(parameters));

        // handle cookies
        CookieHandler cookieHandler = ((CxfRsEndpoint) getEndpoint()).getCookieHandler();
        loadCookies(exchange, target, cookieHandler);

        // Will send out the message to
        // Need to deal with the sub resource class
        Object response = method.invoke(target, parameters);
        int statesCode = target.getResponse().getStatus();
        // handle cookies
        saveCookies(exchange, target, cookieHandler);
        if (throwException) {
            if (response instanceof Response) {
                int respCode = ((Response) response).getStatus();
                if (respCode > 207) {
                    throw populateCxfRsProducerException(exchange, (Response) response, respCode);
                }
            }
        }
        CxfRsEndpoint cxfRsEndpoint = (CxfRsEndpoint) getEndpoint();
        CxfRsBinding binding = cxfRsEndpoint.getBinding();

        if (exchange.getPattern().isOutCapable()) {
            LOG.trace("Response body = {}", response);
            exchange.getOut().getHeaders().putAll(exchange.getIn().getHeaders());
            exchange.getMessage().setBody(binding.bindResponseToCamelBody(response, exchange));
            exchange.getMessage().getHeaders().putAll(binding.bindResponseHeadersToCamelHeaders(response, exchange));
            exchange.getMessage().setHeader(CxfConstants.HTTP_RESPONSE_CODE, statesCode);
        } else {
            // just close the input stream of the response object
            if (response instanceof Response) {
                ((Response) response).close();
            }
        }
    }

    protected ClientFactoryBeanCache getClientFactoryBeanCache() {
        return clientFactoryBeanCache;
    }

    private void insertQueryParametersFromQueryString(WebClient client, String queryString, String charset)
            throws UnsupportedEncodingException {
        for (String param : queryString.split("&")) {
            String[] pair = param.split("=", 2);
            if (pair.length == 2) {
                String name = URLDecoder.decode(pair[0], charset);
                String value = URLDecoder.decode(pair[1], charset);
                client.query(name, value);
            } else {
                throw new IllegalArgumentException("Invalid parameter, expected to be a pair but was " + param);
            }
        }
    }

    private Method findRightMethod(
            List<Class<?>> resourceClasses, String methodName,
            Class<?>[] parameterTypes)
            throws NoSuchMethodException {
        for (Class<?> clazz : resourceClasses) {
            try {
                Method[] m = clazz.getMethods();
                iterate_on_methods: for (Method method : m) {
                    if (!method.getName().equals(methodName)) {
                        continue;
                    }
                    Class<?>[] params = method.getParameterTypes();
                    if (params.length != parameterTypes.length) {
                        continue;
                    }
                    for (int i = 0; i < parameterTypes.length; i++) {
                        if (parameterTypes[i] != null && !params[i].isAssignableFrom(parameterTypes[i])) {
                            continue iterate_on_methods;
                        }
                    }
                    return method;
                }
            } catch (SecurityException ex) {
                // keep looking
            }
        }
        throw new NoSuchMethodException(
                "Cannot find method with name: " + methodName
                                        + " having parameters assignable from: "
                                        + arrayToString(parameterTypes));
    }

    private Class<?>[] getParameterTypes(Object[] objects) {
        // We need to handle the void parameter situation.
        if (objects == null) {
            return new Class[] {};
        }
        Class<?>[] answer = new Class[objects.length];
        int i = 0;
        for (Object obj : objects) {
            if (obj == null) {
                answer[i] = null;
            } else {
                answer[i] = obj.getClass();
            }
            i++;
        }
        return answer;
    }

    private Map<String, String> getMatrixParametersFromMatrixString(String matrixString, String charset)
            throws UnsupportedEncodingException {
        Map<String, String> answer = new LinkedHashMap<>();
        for (String param : matrixString.split(";")) {
            String[] pair = param.split("=", 2);
            if (pair.length == 2) {
                String name = URLDecoder.decode(pair[0], charset);
                String value = URLDecoder.decode(pair[1], charset);
                answer.put(name, value);
            } else {
                throw new IllegalArgumentException("Invalid parameter, expected to be a pair but was " + param);
            }
        }
        return answer;
    }

    private String arrayToString(Object[] array) {
        StringBuilder buffer = new StringBuilder("[");
        for (Object obj : array) {
            if (buffer.length() > 2) {
                buffer.append(",");
            }
            buffer.append(obj.toString());
        }
        buffer.append("]");
        return buffer.toString();
    }

    protected CxfOperationException populateCxfRsProducerException(Exchange exchange, Response response, int responseCode) {
        CxfOperationException exception;
        String uri = exchange.getFromEndpoint().getEndpointUri();
        String statusText = statusTextFromResponseCode(responseCode);
        Map<String, String> headers = parseResponseHeaders(response, exchange);
        //Get the response detail string
        String copy = exchange.getContext().getTypeConverter().convertTo(String.class, response.getEntity());
        if (responseCode >= 300 && responseCode < 400) {
            String redirectLocation;
            if (response.getMetadata().getFirst("Location") != null) {
                redirectLocation = response.getMetadata().getFirst("location").toString();
                exception = new CxfOperationException(uri, responseCode, statusText, redirectLocation, headers, copy);
            } else {
                //no redirect location
                exception = new CxfOperationException(uri, responseCode, statusText, null, headers, copy);
            }
        } else {
            //internal server error(error code 500)
            exception = new CxfOperationException(uri, responseCode, statusText, null, headers, copy);
        }

        return exception;
    }

    /**
     * Convert the given HTTP response code to its corresponding status text or response category. This is useful to
     * avoid creating NPEs if this producer is presented with an HTTP response code that the JAX-RS API doesn't know.
     *
     * @param  responseCode the HTTP response code to be converted to status text
     * @return              the status text for the code, or, if JAX-RS doesn't know the code, the status category as
     *                      text
     */
    String statusTextFromResponseCode(int responseCode) {
        Response.Status status = Response.Status.fromStatusCode(responseCode);

        return status != null ? status.toString() : responseCategoryFromCode(responseCode);
    }

    /**
     * Return the category of the given HTTP response code, as text. Invalid codes will result in appropriate text; this
     * method never returns null.
     *
     * @param  responseCode HTTP response code whose category is to be returned
     * @return              the category of the give response code; never {@code null}.
     */
    private String responseCategoryFromCode(int responseCode) {
        return Response.Status.Family.familyOf(responseCode).name();
    }

    protected Map<String, String> parseResponseHeaders(Object response, Exchange camelExchange) {

        Map<String, String> answer = new HashMap<>();
        if (response instanceof Response) {

            for (Map.Entry<String, List<Object>> entry : ((Response) response).getMetadata().entrySet()) {
                LOG.trace("Parse external header {}={}", entry.getKey(), entry.getValue());
                answer.put(entry.getKey(), entry.getValue().get(0).toString());
            }
        }

        return answer;
    }

    private Object getBody(
            Exchange exchange, Message inMessage, String httpMethod, CxfRsEndpoint cxfRsEndpoint, CxfRsBinding binding)
            throws Exception {
        Object body = null;
        if (!"GET".equals(httpMethod)) {
            // need to check the request object if the http Method is not GET
            if ("DELETE".equals(httpMethod) && cxfRsEndpoint.isIgnoreDeleteMethodMessageBody()) {
                // just ignore the message body if the ignoreDeleteMethodMessageBody is true
            } else {
                body = binding.bindCamelMessageBodyToRequestBody(inMessage, exchange);
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Request body = {}", body);
                }
            }
        }
        return body;
    }

    private final class CxfInvocationCallback implements InvocationCallback<Response> {

        private final Exchange exchange;
        private final CxfRsEndpoint cxfRsEndpoint;
        private final Class<?> responseClass;
        private final AsyncCallback callback;
        private final Type genericType;
        private final Client client;

        private CxfInvocationCallback(Client client, Exchange exchange, CxfRsEndpoint cxfRsEndpoint, Class<?> responseClass,
                                      AsyncCallback callback, Type genericType) {
            this.exchange = exchange;
            this.cxfRsEndpoint = cxfRsEndpoint;
            this.responseClass = responseClass;
            this.callback = callback;
            this.genericType = genericType;
            this.client = client;
        }

        @Override
        public void completed(Response response) {
            try {
                if (shouldHandleError(response)) {
                    handleError(response);
                    return;
                }
                // handle cookies
                saveCookies(exchange, client, cxfRsEndpoint.getCookieHandler());
                if (!exchange.getPattern().isOutCapable()) {
                    return;
                }

                LOG.trace("Response body = {}", response);
                exchange.getMessage().getHeaders().putAll(exchange.getIn().getHeaders());
                final CxfRsBinding binding = cxfRsEndpoint.getBinding();
                exchange.getMessage().getHeaders().putAll(binding.bindResponseHeadersToCamelHeaders(response, exchange));

                if (genericType != null && !genericType.equals(Void.TYPE)) {
                    GenericType<?> genericTypeClone = new GenericType<>(this.genericType);
                    exchange.getMessage()
                            .setBody(binding.bindResponseToCamelBody(response.readEntity(genericTypeClone), exchange));
                } else if (responseClass != null && !responseClass.equals(Void.TYPE)) {
                    exchange.getMessage()
                            .setBody(binding.bindResponseToCamelBody(response.readEntity(responseClass), exchange));
                } else {
                    exchange.getMessage().setBody(binding.bindResponseToCamelBody(response, exchange));
                }
                exchange.getMessage().setHeader(CxfConstants.HTTP_RESPONSE_CODE, response.getStatus());
            } catch (Exception exception) {
                LOG.error("Error while processing response", exception);
                fail(exception);
            } finally {
                callback.done(false);
            }
        }

        @Override
        public void failed(Throwable throwable) {
            LOG.error("Failed request ", throwable);
            try {
                // handle cookies
                saveCookies(exchange, client, cxfRsEndpoint.getCookieHandler());
                fail(throwable);
            } catch (Exception error) {
                LOG.error("Error while processing failed request", error);
            } finally {
                callback.done(false);
            }
        }

        private void fail(Throwable throwable) {
            if (throwable.getClass().isInstance(WebApplicationException.class)) {
                final WebApplicationException cast = WebApplicationException.class.cast(throwable);
                final Response response = cast.getResponse();
                if (shouldHandleError(response)) {
                    handleError(response);
                }
            } else if (throwable.getClass().isInstance(ResponseProcessingException.class)) {
                final ResponseProcessingException cast = ResponseProcessingException.class.cast(throwable);
                final Response response = cast.getResponse();
                if (shouldHandleError(response)) {
                    handleError(response);
                }
            } else {
                exchange.setException(throwable);
            }
        }

        private boolean shouldHandleError(Response response) {
            //Throw exception on a response > 207
            //http://en.wikipedia.org/wiki/List_of_HTTP_status_codes
            if (response != null && throwException) {
                int respCode = response.getStatus();
                if (respCode > 207) {
                    return true;
                }
            }
            return false;
        }

        private void handleError(Response response) {
            exchange.setException(populateCxfRsProducerException(exchange, response, response.getStatus()));
        }
    }

    private final class CxfProxyInvocationCallback implements InvocationCallback<Object> {

        private final Exchange exchange;
        private final CxfRsEndpoint cxfRsEndpoint;
        private final AsyncCallback callback;
        private final Client client;

        private CxfProxyInvocationCallback(Client client, Exchange exchange, CxfRsEndpoint cxfRsEndpoint,
                                           AsyncCallback callback) {
            this.exchange = exchange;
            this.cxfRsEndpoint = cxfRsEndpoint;
            this.callback = callback;
            this.client = client;
        }

        @Override
        public void completed(Object body) {
            try {
                Response response = client.getResponse();
                // handle cookies
                saveCookies(exchange, client, cxfRsEndpoint.getCookieHandler());
                //handle error
                if (shouldHandleError(response)) {
                    handleError(response);
                    return;
                }
                if (!exchange.getPattern().isOutCapable()) {
                    return;
                }

                LOG.trace("Response body = {}", response);
                exchange.getMessage().getHeaders().putAll(exchange.getIn().getHeaders());
                final CxfRsBinding binding = cxfRsEndpoint.getBinding();
                exchange.getMessage().getHeaders().putAll(binding.bindResponseHeadersToCamelHeaders(response, exchange));
                exchange.getMessage().setBody(binding.bindResponseToCamelBody(body, exchange));
                exchange.getMessage().setHeader(CxfConstants.HTTP_RESPONSE_CODE, response.getStatus());
            } catch (Exception exception) {
                LOG.error("Error while processing response", exception);
                fail(exception);
            } finally {
                callback.done(false);
            }
        }

        @Override
        public void failed(Throwable throwable) {
            LOG.error("Failed request ", throwable);
            try {
                // handle cookies
                saveCookies(exchange, client, cxfRsEndpoint.getCookieHandler());
                fail(throwable);
            } catch (Exception error) {
                LOG.error("Error while processing failed request", error);
            } finally {
                callback.done(false);
            }
        }

        private void fail(Throwable throwable) {
            if (throwable.getClass().isInstance(WebApplicationException.class)) {
                final WebApplicationException cast = WebApplicationException.class.cast(throwable);
                final Response response = cast.getResponse();
                if (shouldHandleError(response)) {
                    handleError(response);
                }
            } else if (throwable.getClass().isInstance(ResponseProcessingException.class)) {
                final ResponseProcessingException cast = ResponseProcessingException.class.cast(throwable);
                final Response response = cast.getResponse();
                if (shouldHandleError(response)) {
                    handleError(response);
                }
            } else {
                exchange.setException(throwable);
            }
        }

        private void handleError(Response response) {
            exchange.setException(populateCxfRsProducerException(exchange, response, response.getStatus()));
        }

        private boolean shouldHandleError(Response response) {
            //Throw exception on a response > 207
            //http://en.wikipedia.org/wiki/List_of_HTTP_status_codes
            if (response != null && throwException) {
                int respCode = response.getStatus();
                if (respCode > 207) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Cache contains {@link org.apache.cxf.jaxrs.client.JAXRSClientFactoryBean}
     */
    class ClientFactoryBeanCache {
        private Map<String, JAXRSClientFactoryBean> cache;

        ClientFactoryBeanCache(final int maxCacheSize) {
            this.cache = LRUCacheFactory.newLRUSoftCache(maxCacheSize);
        }

        public void start() {
            if (cache instanceof LRUCache<?, ?> lruCache) {
                lruCache.resetStatistics();
            }
        }

        public void stop() {
            cache.clear();
        }

        public JAXRSClientFactoryBean get(String address) {
            JAXRSClientFactoryBean retVal = null;
            synchronized (cache) {
                retVal = cache.get(address);

                if (retVal == null) {
                    retVal = ((CxfRsEndpoint) getEndpoint()).createJAXRSClientFactoryBean(address);

                    cache.put(address, retVal);

                    LOG.trace("Created client factory bean and add to cache for address '{}'", address);

                } else {
                    LOG.trace("Retrieved client factory bean from cache for address '{}'", address);
                }
            }
            return retVal;
        }
    }
}
