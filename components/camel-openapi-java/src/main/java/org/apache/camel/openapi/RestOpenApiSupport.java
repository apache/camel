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
package org.apache.camel.openapi;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.apicurio.datamodels.core.models.common.Contact;
import io.apicurio.datamodels.core.models.common.License;
import io.apicurio.datamodels.openapi.models.OasDocument;
import io.apicurio.datamodels.openapi.v2.models.Oas20Document;
import io.apicurio.datamodels.openapi.v2.models.Oas20Info;
import io.apicurio.datamodels.openapi.v3.models.Oas30Document;
import io.apicurio.datamodels.openapi.v3.models.Oas30Info;
import io.apicurio.datamodels.openapi.v3.models.Oas30Server;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.support.ResolverHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.openapi.OpenApiHelper.clearVendorExtensions;
import static org.apache.camel.openapi.RestDefinitionsResolver.JMX_REST_DEFINITION_RESOLVER;

/**
 * A support class for that allows SPI to plugin and offer OpenApi API service listings as part of the Camel component.
 * This allows rest-dsl components such as servlet/jetty/netty-http to offer OpenApi API listings with minimal effort.
 */
public class RestOpenApiSupport {

    static final String HEADER_X_FORWARDED_PREFIX = "X-Forwarded-Prefix";
    static final String HEADER_X_FORWARDED_HOST = "X-Forwarded-Host";
    static final String HEADER_X_FORWARDED_PROTO = "X-Forwarded-Proto";
    static final String HEADER_HOST = "Host";

    private static final Logger LOG = LoggerFactory.getLogger(RestOpenApiSupport.class);
    private final RestOpenApiReader reader = new RestOpenApiReader();
    private final RestDefinitionsResolver localRestDefinitionResolver = new DefaultRestDefinitionsResolver();
    private volatile RestDefinitionsResolver jmxRestDefinitionResolver;
    private boolean cors;

    private static void setupCorsHeaders(RestApiResponseAdapter response, Map<String, String> corsHeaders) {
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

        if (LOG.isTraceEnabled()) {
            LOG.trace("Using CORS headers[");
            LOG.trace("  Access-Control-Allow-Origin={}", allowOrigin);
            LOG.trace("  Access-Control-Allow-Methods={}", allowMethods);
            LOG.trace("  Access-Control-Allow-Headers={}", allowHeaders);
            LOG.trace("  Access-Control-Max-Age={}", maxAge);
            LOG.trace("]");
        }
        response.setHeader("Access-Control-Allow-Origin", allowOrigin);
        response.setHeader("Access-Control-Allow-Methods", allowMethods);
        response.setHeader("Access-Control-Allow-Headers", allowHeaders);
        response.setHeader("Access-Control-Max-Age", maxAge);
    }

    static void setupXForwardedHeaders(OasDocument openApi, Map<String, Object> headers) {

        String basePath = getBasePathFromOasDocument(openApi);

        if (openApi instanceof Oas20Document) {
            String host = (String) headers.get(HEADER_HOST);
            if (ObjectHelper.isNotEmpty(host)) {
                ((Oas20Document) openApi).host = host;
            }

            String forwardedPrefix = (String) headers.get(HEADER_X_FORWARDED_PREFIX);
            if (ObjectHelper.isNotEmpty(forwardedPrefix)) {
                ((Oas20Document) openApi).basePath = URISupport.joinPaths(forwardedPrefix, basePath);
            }

            String forwardedHost = (String) headers.get(HEADER_X_FORWARDED_HOST);
            if (ObjectHelper.isNotEmpty(forwardedHost)) {
                ((Oas20Document) openApi).host = forwardedHost;
            }

            String proto = (String) headers.get(HEADER_X_FORWARDED_PROTO);
            if (ObjectHelper.isNotEmpty(proto)) {
                String[] schemes = proto.split(",");
                for (String scheme : schemes) {
                    String trimmedScheme = scheme.trim();
                    if (ObjectHelper.isNotEmpty(trimmedScheme)) {
                        if (((Oas20Document) openApi).schemes == null) {
                            ((Oas20Document) openApi).schemes = new ArrayList<>();
                        }
                        ((Oas20Document) openApi).schemes.add(trimmedScheme.toLowerCase());
                    }
                }
            }
        } else if (openApi instanceof Oas30Document) {

            String host = (String) headers.get(HEADER_HOST);

            String forwardedPrefix = (String) headers.get(HEADER_X_FORWARDED_PREFIX);

            if (ObjectHelper.isNotEmpty(forwardedPrefix)) {
                basePath = URISupport.joinPaths(forwardedPrefix, basePath);
            }

            String forwardedHost = (String) headers.get(HEADER_X_FORWARDED_HOST);
            if (ObjectHelper.isNotEmpty(forwardedHost)) {
                host = forwardedHost;
            }

            String proto = (String) headers.get(HEADER_X_FORWARDED_PROTO);
            if (((Oas30Document) openApi).getServers() != null) {
                ((Oas30Document) openApi).getServers().clear();
            }
            if (ObjectHelper.isNotEmpty(proto)) {
                String[] schemes = proto.split(",");
                for (String schema : schemes) {
                    String trimmedScheme = schema.trim();
                    String serverUrl = String.format("%s://%s%s", trimmedScheme.toLowerCase(), host, basePath);
                    ((Oas30Document) openApi).addServer(serverUrl, null);

                }
            } else {
                ((Oas30Document) openApi).addServer(basePath, null);
            }

        }
    }

    public static String getHostFromOasDocument(final OasDocument openapi) {
        String host = null;
        if (openapi instanceof Oas20Document) {
            host = ((Oas20Document) openapi).host;
        } else if (openapi instanceof Oas30Document) {
            if (((Oas30Document) openapi).getServers() != null
                    && ((Oas30Document) openapi).getServers().get(0) != null) {
                try {
                    URL serverUrl = new URL(
                            parseVariables(((Oas30Document) openapi).getServers().get(0).url,
                                    (Oas30Server) ((Oas30Document) openapi).getServers().get(0)));
                    host = serverUrl.getHost();

                } catch (MalformedURLException e) {
                    LOG.info("error when parsing OpenApi 3.0 doc server url", e);
                }
            }
        }
        return host;

    }

    public static String getBasePathFromOasDocument(final OasDocument openapi) {
        String basePath = null;
        if (openapi instanceof Oas20Document) {
            basePath = ((Oas20Document) openapi).basePath;
        } else if (openapi instanceof Oas30Document) {
            if (((Oas30Document) openapi).getServers() != null
                    && ((Oas30Document) openapi).getServers().get(0) != null) {
                try {
                    Oas30Server server = (Oas30Server) ((Oas30Document) openapi).getServers().get(0);
                    if (server.variables != null && server.variables.get("basePath") != null) {
                        basePath = server.variables.get("basePath").default_;
                    }
                    if (basePath == null) {
                        // parse server url as fallback
                        URL serverUrl = new URL(
                                parseVariables(((Oas30Document) openapi).getServers().get(0).url,
                                        (Oas30Server) ((Oas30Document) openapi).getServers().get(0)));
                        // strip off the first "/" if double "/" exists
                        basePath = serverUrl.getPath().replaceAll("//", "/");
                        if ("/".equals(basePath)) {
                            basePath = "";
                        }
                    }

                } catch (MalformedURLException e) {
                    //not a valid whole url, just the basePath
                    basePath = ((Oas30Document) openapi).getServers().get(0).url;
                }
            }

        }

        return basePath;
    }

    public static String parseVariables(String url, Oas30Server server) {
        Pattern p = Pattern.compile("\\{(.*?)}");
        Matcher m = p.matcher(url);
        while (m.find()) {
            String var = m.group(1);
            if (server != null && server.variables != null && server.variables.get(var) != null) {
                String varValue = server.variables.get(var).default_;
                url = url.replace("{" + var + "}", varValue);
            }
        }
        return url;
    }

    public void initOpenApi(BeanConfig openApiConfig, Map<String, Object> config) {
        // configure openApi options
        String s = (String) config.get("openapi.version");
        if (s != null) {
            openApiConfig.setVersion(s);
        }
        s = (String) config.get("base.path");
        if (s != null) {
            openApiConfig.setBasePath(s);
        }
        s = (String) config.get("host");
        if (s != null) {
            openApiConfig.setHost(s);
        }
        s = (String) config.get("cors");
        if (s != null) {
            cors = "true".equalsIgnoreCase(s);
        }
        s = (String) config.get("schemes");
        if (s == null) {
            // deprecated due typo
            s = (String) config.get("schemas");
        }
        if (s != null) {
            String[] schemes = s.split(",");
            openApiConfig.setSchemes(schemes);
        } else {
            // assume http by default
            openApiConfig.setSchemes(new String[] { "http" });
        }

        String version = (String) config.get("api.version");
        String title = (String) config.get("api.title");
        String description = (String) config.get("api.description");
        String termsOfService = (String) config.get("api.termsOfService");
        String licenseName = (String) config.get("api.license.name");
        String licenseUrl = (String) config.get("api.license.url");
        String contactName = (String) config.get("api.contact.name");
        String contactUrl = (String) config.get("api.contact.url");
        String contactEmail = (String) config.get("api.contact.email");

        if (!openApiConfig.isOpenApi3()) {
            setInfoOas20(openApiConfig, version, title, description, termsOfService, licenseName, licenseUrl,
                    contactName, contactUrl, contactEmail);
        } else {
            setInfoOas30(openApiConfig, version, title, description, termsOfService, licenseName, licenseUrl,
                    contactName, contactUrl, contactEmail);
        }
    }

    private void setInfoOas30(
            BeanConfig openApiConfig, String version, String title, String description,
            String termsOfService, String licenseName, String licenseUrl,
            String contactName, String contactUrl, String contactEmail) {
        Oas30Info info = new Oas30Info();
        info.version = version;
        info.title = title;
        info.description = description;
        info.termsOfService = termsOfService;

        if (licenseName != null || licenseUrl != null) {
            License license = info.createLicense();
            license.name = licenseName;
            license.url = licenseUrl;
            info.license = license;
        }

        if (contactName != null || contactUrl != null || contactEmail != null) {
            Contact contact = info.createContact();
            contact.name = contactName;
            contact.url = contactUrl;
            contact.email = contactEmail;
            info.contact = contact;
        }
        openApiConfig.setInfo(info);
    }

    private void setInfoOas20(
            BeanConfig openApiConfig, String version, String title, String description,
            String termsOfService, String licenseName, String licenseUrl,
            String contactName, String contactUrl, String contactEmail) {
        Oas20Info info = new Oas20Info();
        info.version = version;
        info.title = title;
        info.description = description;
        info.termsOfService = termsOfService;

        if (licenseName != null || licenseUrl != null) {
            License license = info.createLicense();
            license.name = licenseName;
            license.url = licenseUrl;
            info.license = license;
        }

        if (contactName != null || contactUrl != null || contactEmail != null) {
            Contact contact = info.createContact();
            contact.name = contactName;
            contact.url = contactUrl;
            contact.email = contactEmail;
            info.contact = contact;
        }
        openApiConfig.setInfo(info);
    }

    public List<RestDefinition> getRestDefinitions(CamelContext camelContext) throws Exception {
        return localRestDefinitionResolver.getRestDefinitions(camelContext, null);
    }

    public List<RestDefinition> getRestDefinitions(CamelContext camelContext, String camelId) throws Exception {
        if (jmxRestDefinitionResolver == null) {
            jmxRestDefinitionResolver = createJmxRestDefinitionsResolver(camelContext);
        }
        return jmxRestDefinitionResolver.getRestDefinitions(camelContext, camelId);
    }

    protected RestDefinitionsResolver createJmxRestDefinitionsResolver(CamelContext camelContext) {
        return ResolverHelper.resolveService(
                camelContext,
                camelContext.adapt(ExtendedCamelContext.class).getBootstrapFactoryFinder(),
                JMX_REST_DEFINITION_RESOLVER,
                RestDefinitionsResolver.class)
                .orElseThrow(
                        () -> new IllegalArgumentException("Cannot find camel-openapi-java on classpath."));
    }

    public void renderResourceListing(
            CamelContext camelContext, RestApiResponseAdapter response,
            BeanConfig openApiConfig, boolean json,
            Map<String, Object> headers, ClassResolver classResolver,
            RestConfiguration configuration)
            throws Exception {
        LOG.trace("renderResourceListing");

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        if (cors) {
            setupCorsHeaders(response, configuration.getCorsHeaders());
        }

        List<RestDefinition> rests = getRestDefinitions(camelContext);

        if (rests != null) {
            final Map<String, Object> apiProperties = configuration.getApiProperties() != null
                    ? configuration.getApiProperties() : new HashMap<>();
            if (json) {
                response.setHeader(Exchange.CONTENT_TYPE, (String) apiProperties
                        .getOrDefault("api.specification.contentType.json", "application/json"));

                // read the rest-dsl into openApi model
                OasDocument openApi = reader.read(
                        camelContext, rests, openApiConfig, camelContext.getName(), classResolver);
                if (configuration.isUseXForwardHeaders()) {
                    setupXForwardedHeaders(openApi, headers);
                }

                if (!configuration.isApiVendorExtension()) {
                    clearVendorExtensions(openApi);
                }

                Object dump = io.apicurio.datamodels.Library.writeNode(openApi);
                byte[] bytes = mapper.writeValueAsBytes(dump);
                int len = bytes.length;
                response.setHeader(Exchange.CONTENT_LENGTH, "" + len);

                response.writeBytes(bytes);
            } else {
                response.setHeader(Exchange.CONTENT_TYPE, (String) apiProperties
                        .getOrDefault("api.specification.contentType.yaml", "text/yaml"));

                // read the rest-dsl into openApi model
                OasDocument openApi = reader.read(
                        camelContext, rests, openApiConfig, camelContext.getName(), classResolver);
                if (configuration.isUseXForwardHeaders()) {
                    setupXForwardedHeaders(openApi, headers);
                }

                if (!configuration.isApiVendorExtension()) {
                    clearVendorExtensions(openApi);
                }

                Object dump = io.apicurio.datamodels.Library.writeNode(openApi);
                byte[] jsonData = mapper.writeValueAsBytes(dump);

                // json to yaml
                JsonNode node = mapper.readTree(jsonData);
                byte[] bytes = new YAMLMapper().writeValueAsString(node).getBytes();

                int len = bytes.length;
                response.setHeader(Exchange.CONTENT_LENGTH, "" + len);

                response.writeBytes(bytes);
            }
        } else {
            response.noContent();
        }
    }

}
