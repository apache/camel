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
import java.util.HashMap;
import java.util.Map;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

import org.apache.camel.spi.RestConfiguration;

/**
 * A simple CORS filter that can used to allow the swagger ui or other API browsers from remote origins to access the
 * Rest services exposes by this Camel swagger component.
 * <p/>
 * You can configure CORS headers in the init parameters to the Servlet Filter using the names:
 * <ul>
 *     <li>Access-Control-Allow-Origin</li>
 *     <li>Access-Control-Allow-Methods</li>
 *     <li>Access-Control-Allow-Headers</li>
 *     <li>Access-Control-Max-Age</li>
 * </ul>
 * If a parameter is not configured then the default value is used.
 * The default values are defined as:
 * <ul>
 *     <li>{@link RestConfiguration#CORS_ACCESS_CONTROL_ALLOW_ORIGIN}</li>
 *     <li>{@link RestConfiguration#CORS_ACCESS_CONTROL_ALLOW_METHODS}</li>
 *     <li>{@link RestConfiguration#CORS_ACCESS_CONTROL_ALLOW_HEADERS}</li>
 *     <li>{@link RestConfiguration#CORS_ACCESS_CONTROL_MAX_AGE}</li>
 * </ul>
 *
 * @deprecated do not use this directly but use rest-dsl the regular way with rest-dsl configuration.
 */
@Deprecated
public class RestSwaggerCorsFilter implements Filter {

    private final Map<String, String> corsHeaders = new HashMap<String, String>();

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        String s = filterConfig.getInitParameter("Access-Control-Allow-Origin");
        if (s != null) {
            corsHeaders.put("Access-Control-Allow-Origin", s);
        }
        s = filterConfig.getInitParameter("Access-Control-Allow-Methods");
        if (s != null) {
            corsHeaders.put("Access-Control-Allow-Methods", s);
        }
        s = filterConfig.getInitParameter("Access-Control-Allow-Headers");
        if (s != null) {
            corsHeaders.put("Access-Control-Allow-Headers", s);
        }
        s = filterConfig.getInitParameter("Access-Control-Max-Age");
        if (s != null) {
            corsHeaders.put("Access-Control-Max-Age", s);
        }
    }

    @Override
    public void destroy() {
        // noop
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletResponse res = (HttpServletResponse) response;

        setupCorsHeaders(res, corsHeaders);

        chain.doFilter(request, response);
    }

    private static void setupCorsHeaders(HttpServletResponse response, Map<String, String> corsHeaders) {
        // use default value if none has been configured
        String allowOrigin = corsHeaders != null ? corsHeaders.get("Access-Control-Allow-Origin") : null;
        if (allowOrigin == null) {
            allowOrigin = RestConfiguration.CORS_ACCESS_CONTROL_ALLOW_ORIGIN;
        }
        String allowMethods = corsHeaders != null ? corsHeaders.get("Access-Control-Allow-Methods") : null;
        if (allowMethods == null) {
            allowMethods = RestConfiguration.CORS_ACCESS_CONTROL_ALLOW_METHODS;
        }
        String allowHeaders = corsHeaders != null ? corsHeaders.get("Access-Control-Allow-Headers") : null;
        if (allowHeaders == null) {
            allowHeaders = RestConfiguration.CORS_ACCESS_CONTROL_ALLOW_HEADERS;
        }
        String maxAge = corsHeaders != null ? corsHeaders.get("Access-Control-Max-Age") : null;
        if (maxAge == null) {
            maxAge = RestConfiguration.CORS_ACCESS_CONTROL_MAX_AGE;
        }

        response.setHeader("Access-Control-Allow-Origin", allowOrigin);
        response.setHeader("Access-Control-Allow-Methods", allowMethods);
        response.setHeader("Access-Control-Allow-Headers", allowHeaders);
        response.setHeader("Access-Control-Max-Age", maxAge);
    }

}
