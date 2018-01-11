/**
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
package org.apache.camel.http.common;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.servlet.AsyncContext;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A servlet to use as a Camel route as entry.
 */
public class CamelServlet extends HttpServlet {
    public static final String ASYNC_PARAM = "async";

    private static final long serialVersionUID = -7061982839117697829L;
    private static final List<String> METHODS = Arrays.asList("GET", "HEAD", "POST", "PUT", "DELETE", "TRACE", "OPTIONS", "CONNECT", "PATCH");

    protected final Logger log = LoggerFactory.getLogger(getClass());

    /**
     *  We have to define this explicitly so the name can be set as we can not always be
     *  sure that it is already set via the init method
     */
    private String servletName;
    private boolean async;

    private ServletResolveConsumerStrategy servletResolveConsumerStrategy = new HttpServletResolveConsumerStrategy();
    private final ConcurrentMap<String, HttpConsumer> consumers = new ConcurrentHashMap<String, HttpConsumer>();

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        this.servletName = config.getServletName();

        final String asyncParam = config.getInitParameter(ASYNC_PARAM);
        this.async = asyncParam == null ? false : ObjectHelper.toBoolean(asyncParam);
        log.trace("servlet '{}' initialized with: async={}", new Object[]{servletName, async});
    }

    @Override
    protected final void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (isAsync()) {
            final AsyncContext context = req.startAsync();
            //run async
            context.start(() -> doServiceAsync(context));
        } else {
            doService(req, resp);
        }
    }

    /**
     * This is used to handle request asynchronously
     * @param context the {@link AsyncContext}
     */
    protected void doServiceAsync(AsyncContext context) {
        final HttpServletRequest request = (HttpServletRequest) context.getRequest();
        final HttpServletResponse response = (HttpServletResponse) context.getResponse();
        try {
            doService(request, response);
        } catch (Exception e) {
            //An error shouldn't occur as we should handle most of error in doService
            log.error("Error processing request", e);
            try {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            } catch (Exception e1) {
                log.debug("Cannot send reply to client!", e1);
            }
            //Need to wrap it in RuntimeException as it occurs in a Runnable
            throw new RuntimeCamelException(e);
        } finally {
            context.complete();
        }
    }

    /**
     * This is the logical implementation to handle request with {@link CamelServlet}
     * This is where most exceptions should be handled
     *
     * @param request the {@link HttpServletRequest}
     * @param response the {@link HttpServletResponse}
     * @throws ServletException
     * @throws IOException
     */
    protected void doService(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        log.trace("Service: {}", request);

        // Is there a consumer registered for the request.
        HttpConsumer consumer = resolve(request);
        if (consumer == null) {
            // okay we cannot process this requires so return either 404 or 405.
            // to know if its 405 then we need to check if any other HTTP method would have a consumer for the "same" request
            boolean hasAnyMethod = METHODS.stream().anyMatch(m -> getServletResolveConsumerStrategy().isHttpMethodAllowed(request, m, getConsumers()));
            if (hasAnyMethod) {
                log.debug("No consumer to service request {} as method {} is not allowed", request, request.getMethod());
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                return;
            } else {
                log.debug("No consumer to service request {} as resource is not found", request);
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
        }       
        
        // are we suspended?
        if (consumer.isSuspended()) {
            log.debug("Consumer suspended, cannot service request {}", request);
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        }

        // if its an OPTIONS request then return which method is allowed
        if ("OPTIONS".equals(request.getMethod()) && !consumer.isOptionsEnabled()) {
            String s;
            if (consumer.getEndpoint().getHttpMethodRestrict() != null) {
                s = "OPTIONS," + consumer.getEndpoint().getHttpMethodRestrict();
            } else {
                // allow them all
                s = "GET,HEAD,POST,PUT,DELETE,TRACE,OPTIONS,CONNECT,PATCH";
            }
            response.addHeader("Allow", s);
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }
        
        if (consumer.getEndpoint().getHttpMethodRestrict() != null 
            && !consumer.getEndpoint().getHttpMethodRestrict().contains(request.getMethod())) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }

        if ("TRACE".equals(request.getMethod()) && !consumer.isTraceEnabled()) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }
        
        // create exchange and set data on it
        Exchange exchange = consumer.getEndpoint().createExchange(ExchangePattern.InOut);

        if (consumer.getEndpoint().isBridgeEndpoint()) {
            exchange.setProperty(Exchange.SKIP_GZIP_ENCODING, Boolean.TRUE);
            exchange.setProperty(Exchange.SKIP_WWW_FORM_URLENCODED, Boolean.TRUE);
        }
        if (consumer.getEndpoint().isDisableStreamCache()) {
            exchange.setProperty(Exchange.DISABLE_HTTP_STREAM_CACHE, Boolean.TRUE);
        }

        // we override the classloader before building the HttpMessage just in case the binding
        // does some class resolution
        ClassLoader oldTccl = overrideTccl(exchange);
        HttpHelper.setCharsetFromContentType(request.getContentType(), exchange);
        exchange.setIn(new HttpMessage(exchange, consumer.getEndpoint(), request, response));
        // set context path as header
        String contextPath = consumer.getEndpoint().getPath();
        exchange.getIn().setHeader("CamelServletContextPath", contextPath);

        String httpPath = (String)exchange.getIn().getHeader(Exchange.HTTP_PATH);
        // here we just remove the CamelServletContextPath part from the HTTP_PATH
        if (contextPath != null
            && httpPath.startsWith(contextPath)) {
            exchange.getIn().setHeader(Exchange.HTTP_PATH,
                    httpPath.substring(contextPath.length()));
        }

        // we want to handle the UoW
        try {
            consumer.createUoW(exchange);
        } catch (Exception e) {
            log.error("Error processing request", e);
            throw new ServletException(e);
        }

        try {
            if (log.isTraceEnabled()) {
                log.trace("Processing request for exchangeId: {}", exchange.getExchangeId());
            }
            // process the exchange
            consumer.getProcessor().process(exchange);
        } catch (Exception e) {
            exchange.setException(e);
        }

        try {
            // now lets output to the response
            if (log.isTraceEnabled()) {
                log.trace("Writing response for exchangeId: {}", exchange.getExchangeId());
            }
            Integer bs = consumer.getEndpoint().getResponseBufferSize();
            if (bs != null) {
                log.trace("Using response buffer size: {}", bs);
                response.setBufferSize(bs);
            }
            consumer.getBinding().writeResponse(exchange, response);
        } catch (IOException e) {
            log.error("Error processing request", e);
            throw e;
        } catch (Exception e) {
            log.error("Error processing request", e);
            throw new ServletException(e);
        } finally {
            consumer.doneUoW(exchange);
            restoreTccl(exchange, oldTccl);
        }
    }

    /**
     * @deprecated use {@link ServletResolveConsumerStrategy#resolve(javax.servlet.http.HttpServletRequest, java.util.Map)}
     */
    @Deprecated
    protected HttpConsumer resolve(HttpServletRequest request) {
        return getServletResolveConsumerStrategy().resolve(request, getConsumers());
    }

    public void connect(HttpConsumer consumer) {
        log.debug("Connecting consumer: {}", consumer);
        consumers.put(consumer.getEndpoint().getEndpointUri(), consumer);
    }

    public void disconnect(HttpConsumer consumer) {
        log.debug("Disconnecting consumer: {}", consumer);
        consumers.remove(consumer.getEndpoint().getEndpointUri());
    }

    public String getServletName() {
        return servletName;
    }

    public void setServletName(String servletName) {
        this.servletName = servletName;
    }

    public ServletResolveConsumerStrategy getServletResolveConsumerStrategy() {
        return servletResolveConsumerStrategy;
    }

    public void setServletResolveConsumerStrategy(ServletResolveConsumerStrategy servletResolveConsumerStrategy) {
        this.servletResolveConsumerStrategy = servletResolveConsumerStrategy;
    }

    public boolean isAsync() {
        return async;
    }

    public void setAsync(boolean async) {
        this.async = async;
    }

    public Map<String, HttpConsumer> getConsumers() {
        return Collections.unmodifiableMap(consumers);
    }

    /**
     * Override the Thread Context ClassLoader if need be.
     * @return old classloader if overridden; otherwise returns null
     */
    protected ClassLoader overrideTccl(final Exchange exchange) {
        ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader appCtxCl = exchange.getContext().getApplicationContextClassLoader();
        if (oldClassLoader == null || appCtxCl == null) {
            return null;
        }
        
        if (!oldClassLoader.equals(appCtxCl)) {
            Thread.currentThread().setContextClassLoader(appCtxCl);
            if (log.isTraceEnabled()) {
                log.trace("Overrode TCCL for exchangeId {} to {} on thread {}", 
                        new Object[] {exchange.getExchangeId(), appCtxCl, Thread.currentThread().getName()});
            }
            return oldClassLoader;
        }
        return null;
    }

    /**
     * Restore the Thread Context ClassLoader if the old TCCL is not null.
     */
    protected void restoreTccl(final Exchange exchange, ClassLoader oldTccl) {
        if (oldTccl == null) {
            return;
        }
        Thread.currentThread().setContextClassLoader(oldTccl);
        if (log.isTraceEnabled()) {
            log.trace("Restored TCCL for exchangeId {} to {} on thread {}", 
                    new String[] {exchange.getExchangeId(), oldTccl.toString(), Thread.currentThread().getName()});
        }
    }
    
}
