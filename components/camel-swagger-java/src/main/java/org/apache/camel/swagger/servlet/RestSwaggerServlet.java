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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.swagger.jaxrs.config.BeanConfig;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultClassResolver;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.swagger.RestApiResponseAdapter;
import org.apache.camel.swagger.RestSwaggerSupport;
import org.apache.camel.util.EndpointHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.swagger.SwaggerHelper.buildUrl;

/**
 * The default Camel swagger servlet to use when exposing the APIs of the rest-dsl using swagger.
 * <p/>
 * This requires Camel version 2.15 or better at runtime (and JMX to be enabled).
 *
 * @deprecated do not use this directly but use rest-dsl the regular way with rest-dsl configuration.
 */
@Deprecated
public class RestSwaggerServlet extends HttpServlet {

    private static final Logger LOG = LoggerFactory.getLogger(RestSwaggerServlet.class);
    private final BeanConfig swaggerConfig = new BeanConfig();
    private final RestSwaggerSupport support = new RestSwaggerSupport();
    private final ClassResolver classResolver = new DefaultClassResolver();
    private volatile boolean initDone;

    private String apiContextIdPattern;
    private boolean apiContextIdListing;

    public String getApiContextIdPattern() {
        return apiContextIdPattern;
    }

    /**
     * Optional CamelContext id pattern to only allow Rest APIs from rest services within CamelContext's which name matches the pattern.
     * <p/>
     * The pattern uses the rules from {@link org.apache.camel.util.EndpointHelper#matchPattern(String, String)}
     *
     * @param apiContextIdPattern  the pattern
     */
    public void setApiContextIdPattern(String apiContextIdPattern) {
        this.apiContextIdPattern = apiContextIdPattern;
    }

    public boolean isApiContextIdListing() {
        return apiContextIdListing;
    }

    /**
     * Sets whether listing of all available CamelContext's with REST services in the JVM is enabled. If enabled it allows to discover
     * these contexts, if <tt>false</tt> then only if there is exactly one CamelContext then its used.
     */
    public void setApiContextIdListing(boolean apiContextIdListing) {
        this.apiContextIdListing = apiContextIdListing;
    }

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
        // when using servlet then use the cors filter to enable cors
        if (parameters.get("cors") != null) {
            LOG.warn("Use RestSwaggerCorsFilter when uisng this Servlet to enable CORS");
            parameters.remove("cors");
        }
        support.initSwagger(swaggerConfig, parameters);

        // allow to configure these options from the servlet config as well
        Object pattern = parameters.remove("apiContextIdPattern");
        if (pattern != null) {
            apiContextIdPattern = pattern.toString();
        }
        Object listing = parameters.remove("apiContextIdListing");
        if (listing != null) {
            apiContextIdListing = Boolean.valueOf(listing.toString());
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        if (!initDone) {
            initBaseAndApiPaths(request);
        }

        String contextId = null;
        String route = request.getPathInfo();
        String accept = request.getHeader("Accept");

        // whether to use json or yaml
        boolean json = false;
        boolean yaml = false;
        if (route != null && route.endsWith("/swagger.json")) {
            json = true;
            route = route.substring(0, route.length() - 13);
        } else if (route != null && route.endsWith("/swagger.yaml")) {
            yaml = true;
            route = route.substring(0, route.length() - 13);
        }
        if (accept != null && !json && !yaml) {
            json = accept.toLowerCase(Locale.US).contains("json");
            yaml = accept.toLowerCase(Locale.US).contains("yaml");
        }
        if (!json && !yaml) {
            // json is default
            json = true;
        }

        RestApiResponseAdapter adapter = new ServletRestApiResponseAdapter(response);

        try {
            // render list of camel contexts as root
            if (apiContextIdListing && (ObjectHelper.isEmpty(route) || route.equals("/"))) {
                support.renderCamelContexts(adapter, contextId, apiContextIdPattern, json, yaml, null);
            } else {
                String name = null;
                if (ObjectHelper.isNotEmpty(route)) {
                    // first part is the camel context
                    if (route.startsWith("/")) {
                        route = route.substring(1);
                    }
                    // the remainder is the route part
                    name = route.split("/")[0];
                    if (ObjectHelper.isNotEmpty(name)) {
                        route = route.substring(name.length());
                    }
                } else {
                    // listing not enabled then see if there is only one CamelContext and use that as the name
                    List<String> contexts = support.findCamelContexts();
                    if (contexts.size() == 1) {
                        name = contexts.get(0);
                    }
                }

                boolean match = false;
                if (name != null) {
                    match = true;
                    if (apiContextIdPattern != null) {
                        if ("#name#".equals(apiContextIdPattern)) {
                            // always match as we do not know what is the current CamelContext in a plain servlet
                            match = true;
                        } else {
                            match = EndpointHelper.matchPattern(name, apiContextIdPattern);
                        }
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Match contextId: {} with pattern: {} -> {}", new Object[]{name, apiContextIdPattern, match});
                        }
                    }
                }

                if (!match) {
                    adapter.noContent();
                } else {
                    support.renderResourceListing(adapter, swaggerConfig, name, route, json, yaml, classResolver, null);
                }
            }
        } catch (Exception e) {
            LOG.warn("Error rendering Swagger API due " + e.getMessage(), e);
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

            // setup host if not configured
            if (swaggerConfig.getHost() == null) {
                swaggerConfig.setHost(url.getHost());
                if (url.getPort() != 80 && url.getPort() != -1) {
                    swaggerConfig.setHost(url.getHost() + ":" + url.getPort());
                } else {
                    swaggerConfig.setHost(url.getHost());
                }
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
