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
package org.apache.camel.component.platform.http;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.StringJoiner;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultMessage;
import org.apache.camel.util.IOHelper;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.ContextHandler;

public class JettyCustomPlatformHttpConsumer extends DefaultConsumer {
    private static final Pattern PATH_PARAMETER_PATTERN = Pattern.compile("\\{([^/}]+)\\}");

    public JettyCustomPlatformHttpConsumer(PlatformHttpEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        final PlatformHttpEndpoint endpoint = getEndpoint();
        final String path = endpoint.getPath();

        JettyServerTest jettyServerTest = CamelContextHelper.mandatoryLookup(
                getEndpoint().getCamelContext(),
                JettyServerTest.JETTY_SERVER_NAME,
                JettyServerTest.class);

        ContextHandler contextHandler = createHandler(endpoint, path);
        // add handler after starting server.
        jettyServerTest.addHandler(contextHandler);
    }

    private ContextHandler createHandler(PlatformHttpEndpoint endpoint, String path) {
        ContextHandler contextHandler = new ContextHandler();
        contextHandler.setContextPath(path);
        contextHandler.setResourceBase(".");
        contextHandler.setClassLoader(Thread.currentThread().getContextClassLoader());
        contextHandler.setAllowNullPathInfo(true);
        contextHandler.setHandler(new AbstractHandler() {
            @Override
            public void handle(
                    String s, Request request, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
                Exchange exchg = null;
                try {
                    BufferedReader reader = httpServletRequest.getReader();
                    String bodyRequest = "";
                    String strCurrentLine;
                    while ((strCurrentLine = reader.readLine()) != null) {
                        bodyRequest += strCurrentLine;
                    }
                    final Exchange exchange = exchg = toExchange(request, bodyRequest);
                    if (getEndpoint().isHttpProxy()) {
                        exchange.getMessage().removeHeader("Proxy-Connection");
                    }
                    exchange.getMessage().setHeader(Exchange.HTTP_SCHEME, httpServletRequest.getScheme());
                    exchange.getMessage().setHeader(Exchange.HTTP_HOST, httpServletRequest.getServerName());
                    exchange.getMessage().setHeader(Exchange.HTTP_PORT, httpServletRequest.getServerPort());
                    exchange.getMessage().setHeader(Exchange.HTTP_PATH, httpServletRequest.getPathInfo());
                    if (getEndpoint().isHttpProxy()) {
                        exchange.getExchangeExtension().setStreamCacheDisabled(true);
                    }
                    createUoW(exchange);
                    getProcessor().process(exchange);
                    httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                    request.setHandled(true);
                    if (getEndpoint().isHttpProxy()) {
                        // extract response
                        InputStream response = exchange.getMessage().getBody(InputStream.class);
                        String body = JettyCustomPlatformHttpConsumer.toString(response);
                        exchange.getMessage().setBody(body);
                    }
                    httpServletResponse.getWriter().println(exchange.getMessage().getBody());
                } catch (Exception e) {
                    getExceptionHandler().handleException("Failed handling platform-http endpoint " + endpoint.getPath(), exchg,
                            e);
                } finally {
                    if (exchg != null) {
                        doneUoW(exchg);
                    }
                }
            }
        });
        return contextHandler;
    }

    private Exchange toExchange(Request request, String body) {
        final Exchange exchange = getEndpoint().createExchange();
        final Message message = new DefaultMessage(exchange);

        final String charset = request.getHeader("charset");
        if (charset != null) {
            exchange.setProperty(Exchange.CHARSET_NAME, charset);
            message.setHeader(Exchange.HTTP_CHARACTER_ENCODING, charset);
        }

        message.setBody(body.length() != 0 ? body : null);
        exchange.setMessage(message);
        return exchange;
    }

    @Override
    public PlatformHttpEndpoint getEndpoint() {
        return (PlatformHttpEndpoint) super.getEndpoint();
    }

    private static String toString(InputStream input) throws IOException {
        BufferedReader reader = IOHelper.buffered(new InputStreamReader(input));
        StringJoiner builder = new StringJoiner(" ");
        while (true) {
            String line = reader.readLine();
            if (line == null) {
                return builder.toString();
            }
            builder.add(line);
        }
    }

}
