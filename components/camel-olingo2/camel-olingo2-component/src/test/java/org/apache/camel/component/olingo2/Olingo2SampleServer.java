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

package org.apache.camel.component.olingo2;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.webapp.WebAppContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The embedded server for hosting the olingo2 sample service during the tests
 */
public class Olingo2SampleServer {
    private static final Logger LOG = LoggerFactory.getLogger(Olingo2SampleServer.class);

    private org.eclipse.jetty.server.Server server;
    private int port;

    /**
     * 
     * @param port
     * @param resourcePath
     */
    public Olingo2SampleServer(int port, String resourcePath) {
        this.port = port;
        server = new org.eclipse.jetty.server.Server(port);

        WebAppContext webappcontext = new WebAppContext();
        String contextPath = null;
        try {
            contextPath = Olingo2SampleServer.class.getResource(resourcePath).toURI().getPath();
        } catch (URISyntaxException e) {
            LOG.error("Unable to read the resource at {}", resourcePath, e);
        }
        webappcontext.setContextPath("/");

        webappcontext.setWar(contextPath);

        HandlerCollection handlers = new HandlerCollection();
        handlers.setHandlers(new Handler[] {webappcontext, new DefaultHandler()});
        server.setHandler(handlers);
    }

    public void start() throws Exception {
        server.start();
        LOG.debug("Olingo sample server started at port {}", port);
    }

    public void stop() throws Exception {
        server.stop();
    }

    public void destroy() {
        server.destroy();
    }

    static void generateSampleData(String serviceUrl) throws IOException {
        try {
            // need to use reflection to avoid a build error even when the sample source is not available 
            Class<?> clz = Class.forName("org.apache.olingo.sample.annotation.util.AnnotationSampleDataGenerator"); 
            Method m = clz.getMethod("generateData", String.class);
            m.invoke(null, serviceUrl);
        } catch (Throwable t) {
            LOG.error("Unable to load the required sample class", t);
            throw new IOException("unable to load the required sample class");
        }
    }
}
