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
package org.apache.camel.swagger;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.swagger.jaxrs.config.BeanConfig;
import io.swagger.models.Contact;
import io.swagger.models.Info;
import io.swagger.models.License;
import io.swagger.models.Swagger;
import org.apache.camel.impl.DefaultClassResolver;
import org.apache.camel.model.rest.RestDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.swagger.SwaggerHelper.buildUrl;

/**
 * Base class for rest-dsl swagger integration to use a servlet to service the swagger api.
 */
public abstract class RestSwaggerAbstractServlet extends HttpServlet {

    private Logger LOG = LoggerFactory.getLogger(RestSwaggerAbstractServlet.class);

    private RestSwaggerReader reader = new RestSwaggerReader();
    private BeanConfig swaggerConfig = new BeanConfig();
    private boolean cors;
    private volatile boolean initDone;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);

        // configure swagger options
        String s = config.getInitParameter("swagger.version");
        if (s != null) {
            swaggerConfig.setVersion(s);
        }
        s = config.getInitParameter("base.path");
        if (s != null) {
            swaggerConfig.setBasePath(s);
        }
        s = config.getInitParameter("host");
        if (s != null) {
            swaggerConfig.setHost(s);
        }
        s = config.getInitParameter("cors");
        if (s != null) {
            cors = "true".equalsIgnoreCase(s);
        }
        s = config.getInitParameter("schemas");
        if (s != null) {
            String[] schemas = s.split(",");
            swaggerConfig.setSchemes(schemas);
        } else {
            // assume http by default
            swaggerConfig.setSchemes(new String[]{"http"});
        }

        String version = config.getInitParameter("api.version");
        String title = config.getInitParameter("api.title");
        String description = config.getInitParameter("api.description");
        String termsOfService = config.getInitParameter("api.termsOfService");
        String licenseName = config.getInitParameter("api.license.name");
        String licenseUrl = config.getInitParameter("api.license.url");
        String contactName = config.getInitParameter("api.contact.name");
        String contactUrl = config.getInitParameter("api.contact.url");
        String contactEmail = config.getInitParameter("api.contact.email");

        Info info = new Info();
        info.setVersion(version);
        info.setTitle(title);
        info.setDescription(description);
        info.setTermsOfService(termsOfService);

        if (licenseName != null || licenseUrl != null) {
            License license = new License();
            license.setName(licenseName);
            license.setUrl(licenseUrl);
            info.setLicense(license);
        }

        if (contactName != null || contactUrl != null || contactEmail != null) {
            Contact contact = new Contact();
            contact.setName(contactName);
            contact.setUrl(contactUrl);
            contact.setEmail(contactEmail);
            info.setContact(contact);
        }

        swaggerConfig.setInfo(info);
    }

    public abstract List<RestDefinition> getRestDefinitions(String camelId) throws Exception;

    public abstract List<String> findCamelContexts() throws Exception;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        if (!initDone) {
            initBaseAndApiPaths(request);
        }

        String contextId;
        String route = request.getPathInfo();

        try {

            // render list of camel contexts as root
            if (route == null || route.equals("") || route.equals("/")) {
                renderCamelContexts(request, response);
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

                renderResourceListing(request, response, contextId, route);
            }
        } catch (Exception e) {
            LOG.warn("Error rendering swagger due " + e.getMessage(), e);
        }
    }

    private void renderResourceListing(HttpServletRequest request, HttpServletResponse response, String contextId, String route) throws Exception {
        LOG.trace("renderResourceListing");

        if (cors) {
            response.addHeader("Access-Control-Allow-Headers", "Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers");
            response.addHeader("Access-Control-Allow-Methods", "GET, HEAD, POST, PUT, DELETE, TRACE, OPTIONS, CONNECT, PATCH");
            response.addHeader("Access-Control-Allow-Origin", "*");
        }

        List<RestDefinition> rests = getRestDefinitions(contextId);
        if (rests != null) {
            // read the rest-dsl into swagger model
            Swagger swagger = reader.read(rests, route, swaggerConfig, new DefaultClassResolver());

            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
            mapper.writeValue(response.getOutputStream(), swagger);
        } else {
            response.setStatus(204);
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

    /**
     * Renders a list of available CamelContexts in the JVM
     */
    private void renderCamelContexts(HttpServletRequest request, HttpServletResponse response) throws Exception {
        LOG.trace("renderCamelContexts");

        if (cors) {
            response.addHeader("Access-Control-Allow-Headers", "Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers");
            response.addHeader("Access-Control-Allow-Methods", "GET, HEAD, POST, PUT, DELETE, TRACE, OPTIONS, CONNECT, PATCH");
            response.addHeader("Access-Control-Allow-Origin", "*");
        }

        List<String> contexts = findCamelContexts();
        response.getWriter().print("[\n");
        for (int i = 0; i < contexts.size(); i++) {
            String name = contexts.get(i);
            response.getWriter().print("{\"name\": \"" + name + "\"}");
            if (i < contexts.size() - 1) {
                response.getWriter().print(",\n");
            }
        }
        response.getWriter().print("\n]");
    }

}
