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
package org.apache.camel.component.resteasy.servlet;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.component.resteasy.ResteasyComponent;
import org.apache.camel.component.resteasy.ResteasyConstants;
import org.apache.camel.component.resteasy.ResteasyEndpoint;
import org.apache.camel.http.common.DefaultHttpRegistry;
import org.apache.camel.http.common.HttpConsumer;
import org.apache.camel.http.common.HttpHelper;
import org.apache.camel.http.common.HttpMessage;
import org.apache.camel.http.common.HttpRegistry;
import org.apache.camel.http.common.HttpRegistryProvider;
import org.apache.camel.support.DefaultExchange;
import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class extending HttpServletDispatcher from Resteasy and representing servlet used as Camel Consumer. This servlet
 * needs to be used in application if you want to use Camel Resteasy consumer in your camel routes.
 */
public class ResteasyCamelServlet extends HttpServletDispatcher implements HttpRegistryProvider {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(ResteasyCamelServlet.class);

    private HttpRegistry httpRegistry;

    private String servletName;

    private final ConcurrentMap<String, HttpConsumer> consumers = new ConcurrentHashMap<String, HttpConsumer>();

    /**
     * Init method for ResteasyCamelServlet, which registering servlets to HttpRegistry and it is also registering proxy
     * classes to Resteasy dispatcher
     *
     * @param  servletConfig    configuration of the servlet
     * @throws ServletException exception thrown from the super method
     */
    @SuppressWarnings("rawtypes")
    @Override
    public void init(ServletConfig servletConfig) throws ServletException {
        super.init(servletConfig);

        String name = servletConfig.getServletName();
        if (httpRegistry == null) {
            httpRegistry = DefaultHttpRegistry.getHttpRegistry(name);
            HttpRegistryProvider existing = httpRegistry.getCamelServlet(name);
            if (existing != null) {
                LOG.info("Duplicate ServletName detected: {}. Existing: {} This: {}. "
                         + "Its advised to use unique ServletName per Camel application.",
                        name, existing, this.toString());
            }
            httpRegistry.register(this);
        }

        for (Map.Entry<String, HttpConsumer> entry : consumers.entrySet()) {
            String proxyClasses
                    = ((ResteasyComponent) getServletEndpoint(entry.getValue()).getComponent()).getProxyConsumersClasses();
            if (proxyClasses != null) {
                String[] classes = proxyClasses.split(",");
                LOG.debug("Proxy classes defined in the component {}", Arrays.asList(classes));

                for (String clazz : classes) {
                    try {
                        Class realClazz = Class.forName(clazz);
                        // Create dynamic proxy class implementing interface
                        InvocationHandler handler = new ResteasyInvocationHandler();
                        Object proxy = Proxy.newProxyInstance(realClazz.getClassLoader(), new Class[] { realClazz }, handler);

                        // register new created proxy to the resteasy registry
                        getDispatcher().getRegistry().addSingletonResource(proxy);
                    } catch (ClassNotFoundException e) {
                        LOG.error("Error initializing servlet: {}", e.getMessage(), e);
                        throw new ServletException(e);
                    }
                }
                return;
            }
        }
    }

    /**
     * Overridden service method to consume requests and create responses and propagate them to the Camel routes. If
     * proxies options are used then only request is propagated to the Camel route and user must create some response,
     * which will be returned to the client.
     *
     * @param  httpServletRequest  to be processed
     * @param  httpServletResponse to be returned
     * @throws ServletException    if there was problem in Resteasy servlet, which we are extending
     * @throws IOException         if there was problem in Resteasy servlet, which we are extending
     */
    @Override
    protected void service(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
            throws ServletException, IOException {
        // From camel servlet
        if (LOG.isTraceEnabled()) {
            LOG.trace("Service: {}", httpServletRequest);
        }

        // Is there a consumer registered for the request.
        HttpConsumer consumer = resolve(httpServletRequest);

        if (consumer == null) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("No consumer to service request {}", httpServletRequest);
            }
            // No consumer found in routes let resteasy dispatcher process the request -> returning unchanged rest answer
            super.service(httpServletRequest, httpServletResponse);
            return;
        }
        // are we suspended?
        if (consumer.isSuspended()) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Consumer suspended, cannot service request {}", httpServletRequest);
            }
            httpServletResponse.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            super.service(httpServletRequest, httpServletResponse);
            return;
        }

        // if its an OPTIONS request then return which method is allowed
        if ("OPTIONS".equals(httpServletRequest.getMethod())) {
            String s;
            if (consumer.getEndpoint().getHttpMethodRestrict() != null) {
                s = "OPTIONS," + consumer.getEndpoint().getHttpMethodRestrict();
            } else {
                // allow them all
                s = "GET,HEAD,POST,PUT,DELETE,TRACE,OPTIONS,CONNECT,PATCH";
            }
            httpServletResponse.addHeader("Allow", s);
            httpServletResponse.setStatus(HttpServletResponse.SC_OK);
            super.service(httpServletRequest, httpServletResponse);
            return;
        }

        if ("TRACE".equals(httpServletRequest.getMethod()) && !consumer.isTraceEnabled()) {
            httpServletResponse.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            super.service(httpServletRequest, httpServletResponse);
            return;
        }

        // create exchange and set data on it
        Exchange exchange = new DefaultExchange(consumer.getEndpoint(), ExchangePattern.InOut);
        if (consumer.getEndpoint().isBridgeEndpoint()) {
            exchange.setProperty(Exchange.SKIP_GZIP_ENCODING, Boolean.TRUE);
            exchange.setProperty(Exchange.SKIP_WWW_FORM_URLENCODED, Boolean.TRUE);
        }
        if (consumer.getEndpoint().isDisableStreamCache()) {
            exchange.setProperty(Exchange.DISABLE_HTTP_STREAM_CACHE, Boolean.TRUE);
        }

        // skipServletProcessing is set to true then don't process in servlet. Just continue to camel route
        // if skipServletProcessing is true then check httpMethodRestrict
        if (Boolean.TRUE.equals(getServletEndpoint(consumer).getSkipServletProcessing())) {
            if (getServletEndpoint(consumer).getHttpMethodRestrict() != null
                    && !httpServletRequest.getMethod().equals(getServletEndpoint(consumer).getHttpMethodRestrict())) {
                httpServletResponse.setStatus(405);
                return;
            }

        } else {
            super.service(httpServletRequest, httpServletResponse);
        }

        String response = "";

        if (getServletEndpoint(consumer).getSetHttpResponseDuringProcessing()
                || getServletEndpoint(consumer).getSkipServletProcessing()) {
            // Servlet is returning status code 204 if request was correct but there is no content -> if 204 continue to camel route
            if (httpServletResponse.getStatus() != 200 && httpServletResponse.getStatus() != 204) {
                // If request wasn't successful in resteasy then stop processing and return created response from resteasy
                return;
            }

            // Proxy is set to true
            HttpHelper.setCharsetFromContentType(httpServletRequest.getContentType(), exchange);
            HttpMessage m = new HttpMessage(exchange, consumer.getEndpoint(), httpServletRequest, httpServletResponse);

            m.setBody(((ResteasyHttpServletRequestWrapper) httpServletRequest).getStream());
            exchange.setIn(m);
        } else {
            // If request wasn't successful in resteasy then stop processing and return created response from resteasy
            if (httpServletResponse.getStatus() != 200) {
                return;
            }

            HttpHelper.setCharsetFromContentType(httpServletRequest.getContentType(), exchange);
            HttpMessage m = new HttpMessage(exchange, consumer.getEndpoint(), httpServletRequest, httpServletResponse);

            response = new String(
                    ((ResteasyHttpServletResponseWrapper) httpServletResponse).getCopy(),
                    httpServletResponse.getCharacterEncoding());

            m.setBody(((ResteasyHttpServletResponseWrapper) httpServletResponse).getStream());
            exchange.setIn(m);
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("Copier service: {}",
                    new String(
                            ((ResteasyHttpServletResponseWrapper) httpServletResponse).getCopy(),
                            httpServletResponse.getCharacterEncoding()));
        }

        String contextPath = consumer.getEndpoint().getPath();
        exchange.getIn().setHeader(ResteasyConstants.RESTEASY_CONTEXT_PATH, contextPath);

        // Maybe send request to camel also for some logging or something
        exchange.getIn().setHeader(ResteasyConstants.RESTEASY_HTTP_REQUEST, httpServletRequest);

        String httpPath = (String) exchange.getIn().getHeader(ResteasyConstants.HTTP_PATH);
        // here we just remove the CamelServletContextPath part from the HTTP_PATH
        if (contextPath != null
                && httpPath.startsWith(contextPath)) {
            exchange.getIn().setHeader(ResteasyConstants.HTTP_PATH,
                    httpPath.substring(contextPath.length()));
        }

        try {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Processing request for exchangeId: {}", exchange.getExchangeId());
            }
            // process the exchange
            consumer.getProcessor().process(exchange);
        } catch (Exception e) {
            exchange.setException(e);
        }

        try {
            // now lets output to the response
            if (LOG.isTraceEnabled()) {
                LOG.trace("Writing response for exchangeId: {}", exchange.getExchangeId());
            }

            // Reset buffer in response because it was sent to consumer for processing -> route handled response and returned what it should
            if (!response.isEmpty()) {
                httpServletResponse.resetBuffer();
            }

            consumer.getBinding().writeResponse(exchange, httpServletResponse);

        } catch (IOException e) {
            LOG.error("Error processing request: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            LOG.error("Error processing request: {}", e.getMessage(), e);
            throw new ServletException(e);
        }
    }

    /**
     * Connect HttpConsumer so it can be used as consumer
     *
     * @param consumer to be connected
     */
    public void connect(HttpConsumer consumer) {
        consumers.put(consumer.getPath(), consumer);
    }

    /**
     * Destroy ResteasyCamelServlet and delete registry created by it
     */
    @Override
    public void destroy() {
        DefaultHttpRegistry.removeHttpRegistry(getServletName());
        if (httpRegistry != null) {
            httpRegistry.unregister(this);
            httpRegistry = null;
        }
        LOG.info("Destroyed CamelResteasyServlet[{}]", getServletName());
    }

    /**
     * Disconnect HttpConsumer
     *
     * @param consumer to disconnect
     */
    public void disconnect(HttpConsumer consumer) {
        LOG.info("Disconnecting consumer: {}", consumer);
        consumers.remove(consumer.getPath());
    }

    /**
     * Get ResteasyEndpoint from HttpConsumer
     *
     * @param  consumer from which we need to get the endpoint
     * @return          ResteasyEndpoint for given HttpConsumer
     */
    protected ResteasyEndpoint getServletEndpoint(HttpConsumer consumer) {
        if (!(consumer.getEndpoint() instanceof ResteasyEndpoint)) {
            throw new RuntimeException(
                    "Invalid consumer type. Must be RESTEasyEndpoint but is "
                                       + consumer.getClass().getName());
        }
        return (ResteasyEndpoint) consumer.getEndpoint();
    }

    /**
     * Resolve for which HttpConsumer is given request
     *
     * @param  request to be resolved
     * @return         HttpConsumer, which must consume given request
     */
    protected HttpConsumer resolve(HttpServletRequest request) {
        String path = request.getPathInfo();
        if (path == null) {
            return null;
        }
        HttpConsumer answer = consumers.get(path);

        if (answer == null) {
            for (Map.Entry<String, HttpConsumer> consumerEntry : consumers.entrySet()) {
                if (consumerEntry.getValue().getEndpoint().isMatchOnUriPrefix() && path.startsWith(consumerEntry.getKey())) {
                    answer = consumerEntry.getValue();
                    break;
                }
            }
        }
        return answer;
    }

    @Override
    public String getServletName() {
        return servletName;
    }

    public void setServletName(String servletName) {
        this.servletName = servletName;
    }

    public Map<String, HttpConsumer> getConsumers() {
        return Collections.unmodifiableMap(consumers);
    }
}
