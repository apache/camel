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

package org.apache.camel.test.infra.jetty.services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.servlet.Servlet;

import org.eclipse.jetty.servlet.ServletHolder;

/**
 * A configuration holder for embedded Jetty instances
 */
public class JettyConfiguration {
    public static final String ROOT_CONTEXT_PATH = "/";

    /**
     * A configuration holder for Jetty servlet holders
     * 
     * @param <T>
     */
    public static class ServletConfiguration<T> {
        public static final String ROOT_PATH_SPEC = "/*";

        private final T servlet;
        private final String pathSpec;

        public ServletConfiguration(T servlet, String pathSpec) {
            this.servlet = servlet;
            this.pathSpec = pathSpec;
        }

        public T getServlet() {
            return servlet;
        }

        public String getPathSpec() {
            return pathSpec;
        }

        public ServletHolder buildServletHolder() {
            if (servlet instanceof ServletHolder) {
                return (ServletHolder) servlet;
            }

            ServletHolder servletHolder = new ServletHolder();

            if (servlet instanceof String) {
                servletHolder.setClassName((String) servlet);
            } else {
                if (servlet instanceof Servlet) {
                    servletHolder.setServlet((Servlet) servlet);
                } else {
                    throw new IllegalArgumentException(
                            "Unknown servlet type: " + (servlet == null ? "null" : servlet.getClass()));
                }
            }

            return servletHolder;
        }
    }

    private int port;
    private SSLContext sslContext;
    private List<ServletConfiguration<?>> servletConfigurations = new ArrayList<>();
    private String contextPath;

    public int getPort() {
        return port;
    }

    void setPort(int port) {
        this.port = port;
    }

    public SSLContext getSslContext() {
        return sslContext;
    }

    void setSslContext(SSLContext sslContext) {
        this.sslContext = sslContext;
    }

    public String getContextPath() {
        return contextPath;
    }

    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }

    void addServletConfiguration(ServletConfiguration<?> servletConfiguration) {
        servletConfigurations.add(servletConfiguration);
    }

    public List<ServletConfiguration<?>> getServletConfigurations() {
        return Collections.unmodifiableList(servletConfigurations);
    }
}
