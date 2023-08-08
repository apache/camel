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
package org.apache.camel.http.common;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.support.LifecycleStrategySupport;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.concurrent.TimeUnit.MINUTES;

/**
 * A servlet to use as a Camel route as entry.
 */
public class CamelServlet extends HttpServlet implements HttpRegistryProvider {
    public static final String ASYNC_PARAM = "async";
    public static final String FORCE_AWAIT_PARAM = "forceAwait";
    public static final String EXECUTOR_REF_PARAM = "executorRef";
    public static final List<String> METHODS
            = Arrays.asList("GET", "HEAD", "POST", "PUT", "DELETE", "TRACE", "OPTIONS", "CONNECT", "PATCH");

    private static final long serialVersionUID = -7061982839117697829L;

    protected final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * We have to define this explicitly so the name can be set as we can not always be sure that it is already set via
     * the init method
     */
    private String servletName;
    private boolean async;
    private boolean forceAwait;
    private String executorRef;

    private final ConcurrentMap<CamelContext, ExecutorService> executorServicePerContext = new ConcurrentHashMap<>();

    private ServletResolveConsumerStrategy servletResolveConsumerStrategy = new HttpServletResolveConsumerStrategy();
    private final ConcurrentMap<String, HttpConsumer> consumers = new ConcurrentHashMap<>();

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        this.servletName = config.getServletName();

        final String asyncParam = config.getInitParameter(ASYNC_PARAM);
        this.async = asyncParam != null && ObjectHelper.toBoolean(asyncParam);
        this.forceAwait = Boolean.parseBoolean(config.getInitParameter(FORCE_AWAIT_PARAM));
        this.executorRef = config.getInitParameter(EXECUTOR_REF_PARAM);
        log.trace("servlet '{}' initialized with: async={}", servletName, async);
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) {
        log.trace("Service: {}", request);
        try {
            handleService(request, response);
        } catch (Exception e) {
            // do not leak exception back to caller
            log.warn("Error handling request due to: {}", e.getMessage(), e);
            try {
                if (!response.isCommitted()) {
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                }
            } catch (Exception e1) {
                // ignore
            }
        }
    }

    protected void handleService(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (isAsync()) {
            if (executorRef != null) {
                HttpConsumer consumer = doResolve(req, resp); // can be done sync
                if (consumer == null) {
                    return;
                }
                Executor pool = ObjectHelper.notNull(getExecutorService(consumer), executorRef);
                final AsyncContext context = req.startAsync();
                try {
                    pool.execute(() -> {
                        try {
                            final CompletionStage<?> promise = doExecute(req, resp, consumer);
                            if (promise == null) { // early quit
                                context.complete();
                            } else {
                                promise.whenComplete((r, e) -> context.complete());
                            }
                        } catch (Exception e) {
                            onError(resp, e);
                            context.complete();
                        }
                    });
                } catch (final RuntimeException re) { // submit fails
                    context.complete();
                    throw re;
                }
            } else { // will use http servlet threads so normally http threads so better to enable useCamelExecutor
                final AsyncContext context = req.startAsync();
                try {
                    context.start(() -> doServiceAsync(context));
                } catch (final RuntimeException re) { // submit fails
                    context.complete();
                    throw re;
                }
            }
        } else {
            doService(req, resp);
        }
    }

    private void onError(HttpServletResponse resp, Exception e) {
        //An error shouldn't occur as we should handle most error in doService
        log.error("Error processing request", e);
        try {
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (Exception e1) {
            log.debug("Cannot send reply to client!", e1);
        }
        //Need to wrap it in RuntimeException as it occurs in a Runnable
        throw new RuntimeCamelException(e);
    }

    protected Executor getExecutorService(HttpConsumer consumer) {
        CamelContext camelContext = consumer.getEndpoint().getCamelContext();
        Executor pool = camelContext.getRegistry().lookupByNameAndType(executorRef, Executor.class);
        if (pool != null) {
            return pool;
        }

        if (camelContext.isStopping() || camelContext.isStopped()) { // shouldn't occur but as a protection
            return null;
        }
        return executorServicePerContext.computeIfAbsent(camelContext, ctx -> {
            ExecutorServiceManager manager = camelContext.getExecutorServiceManager();
            ExecutorService es = manager.newThreadPool(this, getClass().getSimpleName() + "Executor", executorRef);
            if (es == null) {
                getServletContext().log(
                        "ExecutorServiceRef " + executorRef + " not found in registry (as an ExecutorService instance) " +
                                        "or as a thread pool profile, will default for " + ctx.getName() + ".");
                es = manager.newDefaultThreadPool(this, getClass().getSimpleName() + "Executor");
            }
            ctx.addLifecycleStrategy(new LifecycleStrategySupport() {
                @Override
                public void onContextStopping(final CamelContext context) {
                    final ExecutorService service = executorServicePerContext.remove(context);
                    if (service != null && !service.isShutdown() && !service.isTerminated()) {
                        service.shutdownNow();
                        try { // give it a chance to finish before quitting
                            service.awaitTermination(1, MINUTES);
                        } catch (final InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            });
            return es;
        });
    }

    /**
     * This is used to handle request asynchronously
     *
     * @param context the {@link AsyncContext}
     */
    protected void doServiceAsync(AsyncContext context) {
        final HttpServletRequest request = (HttpServletRequest) context.getRequest();
        final HttpServletResponse response = (HttpServletResponse) context.getResponse();
        try {
            doService(request, response);
        } catch (Exception e) {
            //An error shouldn't occur as we should handle most of error in doService
            onError(response, e);
        } finally {
            context.complete();
        }
    }

    /**
     * This is the logical implementation to handle request with {@link CamelServlet} This is where most exceptions
     * should be handled
     *
     * @param request  the {@link HttpServletRequest}
     * @param response the {@link HttpServletResponse}
     */
    protected void doService(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        log.trace("Service: {}", request);
        HttpConsumer consumer = doResolve(request, response);
        if (consumer != null) {
            doExecute(request, response, consumer);
        }
    }

    private CompletionStage<?> doExecute(HttpServletRequest req, HttpServletResponse res, HttpConsumer consumer)
            throws IOException, ServletException {
        // are we suspended?
        if (consumer.isSuspended()) {
            log.debug("Consumer suspended, cannot service request {}", req);
            res.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return null;
        }

        // if its an OPTIONS request then return which method is allowed
        if ("OPTIONS".equals(req.getMethod()) && !consumer.isOptionsEnabled()) {
            String allowedMethods = METHODS.stream()
                    .filter(m -> getServletResolveConsumerStrategy().isHttpMethodAllowed(req, m, getConsumers()))
                    .collect(Collectors.joining(","));
            if (allowedMethods == null && consumer.getEndpoint().getHttpMethodRestrict() != null) {
                allowedMethods = consumer.getEndpoint().getHttpMethodRestrict();
            }
            if (allowedMethods == null) {
                // allow them all
                allowedMethods = "GET,HEAD,POST,PUT,DELETE,TRACE,OPTIONS,CONNECT,PATCH";
            }
            if (!allowedMethods.contains("OPTIONS")) {
                allowedMethods = allowedMethods + ",OPTIONS";
            }
            res.addHeader("Allow", allowedMethods);
            res.setStatus(HttpServletResponse.SC_OK);
            return null;
        }

        if (consumer.getEndpoint().getHttpMethodRestrict() != null
                && !consumer.getEndpoint().getHttpMethodRestrict().contains(req.getMethod())) {
            res.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return null;
        }

        if ("TRACE".equals(req.getMethod()) && !consumer.isTraceEnabled()) {
            res.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return null;
        }

        // create exchange and set data on it
        Exchange exchange = consumer.createExchange(false);
        exchange.setPattern(ExchangePattern.InOut);

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
        HttpHelper.setCharsetFromContentType(req.getContentType(), exchange);
        exchange.setIn(new HttpMessage(exchange, consumer.getEndpoint(), req, res));
        // set context path as header
        String contextPath = consumer.getEndpoint().getPath();
        exchange.getIn().setHeader("CamelServletContextPath", contextPath);

        String httpPath = (String) exchange.getIn().getHeader(Exchange.HTTP_PATH);
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

        boolean isAsync = false;
        CompletionStage<?> result = null;
        try {
            if (log.isTraceEnabled()) {
                log.trace("Processing request for exchangeId: {}", exchange.getExchangeId());
            }
            // process the exchange
            final Processor processor = consumer.getProcessor();
            isAsync = isAsync() && !forceAwait && AsyncProcessor.class.isInstance(processor);
            if (isAsync) {
                result = AsyncProcessor.class.cast(processor)
                        .processAsync(exchange)
                        .whenComplete((r, ex) -> {
                            if (ex != null) {
                                exchange.setException(ex);
                            } else {
                                try {
                                    afterProcess(res, consumer, exchange, false);
                                } catch (final IOException | ServletException e) {
                                    exchange.setException(e);
                                }
                            }
                        });
            } else {
                processor.process(exchange);
            }
        } catch (Exception e) {
            exchange.setException(e);
        }

        try {
            if (!isAsync) {
                afterProcess(res, consumer, exchange, true);
            }
        } finally {
            restoreTccl(exchange, oldTccl);
        }
        return result;
    }

    protected void afterProcess(
            HttpServletResponse res, HttpConsumer consumer, Exchange exchange,
            boolean rethrow)
            throws IOException, ServletException {
        try {
            // now lets output to the res
            if (log.isTraceEnabled()) {
                log.trace("Writing res for exchangeId: {}", exchange.getExchangeId());
            }
            Integer bs = consumer.getEndpoint().getResponseBufferSize();
            if (bs != null) {
                log.trace("Using res buffer size: {}", bs);
                res.setBufferSize(bs);
            }
            consumer.getBinding().writeResponse(exchange, res);
        } catch (IOException e) {
            log.error("Error processing request", e);
            if (rethrow) {
                throw e;
            } else {
                exchange.setException(e);
            }
        } catch (Exception e) {
            log.error("Error processing request", e);
            if (rethrow) {
                throw new ServletException(e);
            } else {
                exchange.setException(e);
            }
        } finally {
            consumer.doneUoW(exchange);
            consumer.releaseExchange(exchange, false);
        }
    }

    private HttpConsumer doResolve(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // Is there a consumer registered for the request.
        HttpConsumer consumer = resolve(request);
        if (consumer == null) {
            // okay we cannot process this requires so return either 404 or 405.
            // to know if its 405 then we need to check if any other HTTP method would have a consumer for the "same" request
            boolean hasAnyMethod = METHODS.stream()
                    .anyMatch(m -> getServletResolveConsumerStrategy().isHttpMethodAllowed(request, m, getConsumers()));
            if (hasAnyMethod) {
                log.debug("No consumer to service request {} as method {} is not allowed", request, request.getMethod());
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                return null;
            } else {
                log.debug("No consumer to service request {} as resource is not found", request);
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return null;
            }
        }
        return consumer;
    }

    /**
     * @deprecated use
     *             {@link ServletResolveConsumerStrategy#resolve(jakarta.servlet.http.HttpServletRequest, java.util.Map)}
     */
    @Deprecated
    protected HttpConsumer resolve(HttpServletRequest request) {
        return getServletResolveConsumerStrategy().resolve(request, getConsumers());
    }

    @Override
    public void connect(HttpConsumer consumer) {
        log.debug("Connecting consumer: {}", consumer);
        String endpointUri = consumer.getEndpoint().getEndpointUri();
        if (consumers.containsKey(endpointUri)) {
            throw new IllegalStateException("Duplicate request path for " + endpointUri);
        }
        consumers.put(endpointUri, consumer);
    }

    @Override
    public void disconnect(HttpConsumer consumer) {
        log.debug("Disconnecting consumer: {}", consumer);
        consumers.remove(consumer.getEndpoint().getEndpointUri());
    }

    @Override
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
     *
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
                        exchange.getExchangeId(), appCtxCl, Thread.currentThread().getName());
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
                    exchange.getExchangeId(), oldTccl, Thread.currentThread().getName());
        }
    }

}
