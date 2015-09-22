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
package org.apache.camel.swagger.servlet;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.swagger.jaxrs.config.BeanConfig;
import org.apache.camel.spi.RestApiResponseAdapter;
import org.apache.camel.swagger.RestSwaggerSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.swagger.SwaggerHelper.buildUrl;

/**
 * The default Camel swagger servlet to use when exposing the APIs of the rest-dsl using swagger.
 * <p/>
 * This requires Camel version 2.15 or better at runtime (and JMX to be enabled).
 */
public class RestSwaggerServlet extends HttpServlet {

    private static final Logger LOG = LoggerFactory.getLogger(RestSwaggerServlet.class);
    private BeanConfig swaggerConfig = new BeanConfig();
    private RestSwaggerSupport swagger = new RestSwaggerSupport();
    private volatile boolean initDone;

    @Override
    public void init(final ServletConfig config) throws ServletException {
        super.init(config);
        Map<String, Object> parameters = new HashMap<String, Object>();
        Enumeration en = config.getInitParameterNames();
        while (en.hasMoreElements()) {
            String name = (String) en.nextElement();
            Object value = config.getInitParameter(name);
            parameters.put(name, value);
        }
        swagger.initSwagger(swaggerConfig, parameters);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        if (!initDone) {
            initBaseAndApiPaths(request);
        }

        RestApiResponseAdapter adapter = new ServletRestApiResponseAdapter(response);

        String contextId;
        String route = request.getPathInfo();

        try {

            // render list of camel contexts as root
            if (route == null || route.equals("") || route.equals("/")) {
                swagger.renderCamelContexts(adapter);
            } else {
                // first part is the camel context
                if (route.startsWith("/")) {
                    route = route.substring(1);
                }
                // the remainder is the route part
                contextId = route.split("/")[0];
                if (route.startsWith(contextId)) {
                    route = route.substring(contextId.length());
                }

                swagger.renderResourceListing(adapter, swaggerConfig, contextId, route);
            }
        } catch (Exception e) {
            LOG.warn("Error rendering swagger due " + e.getMessage(), e);
        }
    }

    private void initBaseAndApiPaths(HttpServletRequest request) throws MalformedURLException {
        String base = swaggerConfig.getBasePath();
        if (base == null || !base.startsWith("http")) {
            // base path is configured using relative, so lets calculate the absolute url now we have the http request
            URL url = new URL(request.getRequestURL().toString());
            if (base == null) {
                base = "";
            }
            String path = translateContextPath(request);
            swaggerConfig.setHost(url.getHost());

            if (url.getPort() != 80 && url.getPort() != -1) {
                swaggerConfig.setHost(url.getHost() + ":" + url.getPort());
            } else {
                swaggerConfig.setHost(url.getHost());
            }
            swaggerConfig.setBasePath(buildUrl(path, base));
        }
        initDone = true;
    }

    /**
     * We do only want the base context-path and not sub paths
     */
    private String translateContextPath(HttpServletRequest request) {
        String path = request.getContextPath();
        if (path.isEmpty() || path.equals("/")) {
            return "";
        } else {
            int idx = path.lastIndexOf("/");
            if (idx > 0) {
                return path.substring(0, idx);
            }
        }
        return path;
    }

}
