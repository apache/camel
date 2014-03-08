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
package org.apache.camel.component.http;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.component.http.helper.HttpHelper;
import org.apache.camel.impl.DefaultExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version 
 */
public class CamelServlet extends HttpServlet {
    private static final long serialVersionUID = -7061982839117697829L;
    protected final Logger log = LoggerFactory.getLogger(getClass());
    
    /**
     *  We have to define this explicitly so the name can be set as we can not always be
     *  sure that it is already set via the init method
     */
    private String servletName;

    private ConcurrentMap<String, HttpConsumer> consumers = new ConcurrentHashMap<String, HttpConsumer>();
   
    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        this.servletName = config.getServletName();
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        log.trace("Service: {}", request);

        // Is there a consumer registered for the request.
        HttpConsumer consumer = resolve(request);
        if (consumer == null) {
            log.debug("No consumer to service request {}", request);
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }       
        
        // are we suspended?
        if (consumer.isSuspended()) {
            log.debug("Consumer suspended, cannot service request {}", request);
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        }
        
        if (consumer.getEndpoint().getHttpMethodRestrict() != null 
            && !consumer.getEndpoint().getHttpMethodRestrict().equals(request.getMethod())) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }

        if ("TRACE".equals(request.getMethod()) && !consumer.isTraceEnabled()) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }
        
        // create exchange and set data on it
        Exchange exchange = new DefaultExchange(consumer.getEndpoint(), ExchangePattern.InOut);

        if (consumer.getEndpoint().isBridgeEndpoint()) {
            exchange.setProperty(Exchange.SKIP_GZIP_ENCODING, Boolean.TRUE);
        }
        if (consumer.getEndpoint().isDisableStreamCache()) {
            exchange.setProperty(Exchange.DISABLE_HTTP_STREAM_CACHE, Boolean.TRUE);
        }

        // we override the classloader before building the HttpMessage just in case the binding
        // does some class resolution
        ClassLoader oldTccl = overrideTccl(exchange);
        HttpHelper.setCharsetFromContentType(request.getContentType(), exchange);
        exchange.setIn(new HttpMessage(exchange, request, response));
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

    protected HttpConsumer resolve(HttpServletRequest request) {
        String path = request.getPathInfo();
        if (path == null) {
            return null;
        }
        HttpConsumer answer = consumers.get(path);
               
        if (answer == null) {
            for (String key : consumers.keySet()) {
                if (consumers.get(key).getEndpoint().isMatchOnUriPrefix() && path.startsWith(key)) {
                    answer = consumers.get(key);
                    break;
                }
            }
        }
        return answer;
    }

    public void connect(HttpConsumer consumer) {
        log.debug("Connecting consumer: {}", consumer);
        consumers.put(consumer.getPath(), consumer);
    }

    public void disconnect(HttpConsumer consumer) {
        log.debug("Disconnecting consumer: {}", consumer);
        consumers.remove(consumer.getPath());
    }

    public String getServletName() {
        return servletName;
    }

    public void setServletName(String servletName) {
        this.servletName = servletName;
    }
    
    /**
     * Override the Thread Context ClassLoader if need be.
     * @param exchange
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
