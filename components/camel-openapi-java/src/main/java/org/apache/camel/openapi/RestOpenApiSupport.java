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
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.core.util.Yaml31;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
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

    public static DateFormat DEFAULT_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

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

    static void setupXForwardedHeaders(OpenAPI openApi, Map<String, Object> headers) {
        String basePath = getBasePathFromOasDocument(openApi);
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
        if (openApi.getServers() != null) {
            openApi.getServers().clear();
        }
        if (ObjectHelper.isNotEmpty(proto)) {
            String[] schemes = proto.split(",");
            for (String schema : schemes) {
                String trimmedScheme = schema.trim();
                String serverUrl = String.format("%s://%s%s", trimmedScheme.toLowerCase(), host, basePath);
                openApi.addServersItem(new Server().url(serverUrl));
            }
        } else {
            openApi.addServersItem(new Server().url(basePath));
        }
    }

    public static String getHostFromOasDocument(final OpenAPI openapi) {
        String host = null;
        if (openapi.getServers() != null
                && openapi.getServers().get(0) != null) {
            try {
                URL serverUrl = URI.create(
                        parseVariables(openapi.getServers().get(0).getUrl(),
                                openapi.getServers().get(0)))
                        .toURL();
                host = serverUrl.getHost();
            } catch (MalformedURLException e) {
                LOG.debug("Error when parsing OpenApi 3.0 doc server url. This exception is ignored.", e);
            }
        }
        return host;
    }

    public static String getBasePathFromOasDocument(final OpenAPI openapi) {
        String basePath = null;
        if (openapi.getServers() != null
                && openapi.getServers().get(0) != null) {
            try {
                Server server = openapi.getServers().get(0);
                if (server.getVariables() != null && server.getVariables().get("basePath") != null) {
                    basePath = server.getVariables().get("basePath").getDefault();
                }
                if (basePath == null) {
                    // parse server url as fallback
                    URL serverUrl = URI.create(
                            parseVariables(openapi.getServers().get(0).getUrl(),
                                    openapi.getServers().get(0)))
                            .toURL();
                    // strip off the first "/" if double "/" exists
                    basePath = serverUrl.getPath().replace("//", "/");
                    if ("/".equals(basePath)) {
                        basePath = "";
                    }
                }

            } catch (MalformedURLException e) {
                //not a valid whole url, just the basePath
                basePath = openapi.getServers().get(0).getUrl();
            }
        }

        return basePath;
    }

    public static String parseVariables(String url, Server server) {
        Pattern p = Pattern.compile("\\{(.*?)}");
        Matcher m = p.matcher(url);
        while (m.find()) {
            String variable = m.group(1);
            if (server != null && server.getVariables() != null && server.getVariables().get(variable) != null) {
                String varValue = server.getVariables().get(variable).getDefault();
                url = url.replace("{" + variable + "}", varValue);
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

        String defaultConsumes = (String) config.get("api.default.consumes");
        if (defaultConsumes != null) {
            openApiConfig.setDefaultConsumes(defaultConsumes);
        }

        String defaultProduces = (String) config.get("api.default.produces");
        if (defaultProduces != null) {
            openApiConfig.setDefaultProduces(defaultProduces);
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

        setInfo(openApiConfig, version, title, description, termsOfService, licenseName, licenseUrl,
                contactName, contactUrl, contactEmail);

        String externalDocsDescription = (String) config.get("externalDocs.description");
        String externalDocsUrl = (String) config.get("externalDocs.url");

        setExternalDocs(openApiConfig, externalDocsUrl, externalDocsDescription);
    }

    private static void setExternalDocs(BeanConfig openApiConfig, String externalDocsUrl, String externalDocsDescription) {
        if (externalDocsUrl != null) {
            ExternalDocumentation externalDocumentation = new ExternalDocumentation();
            externalDocumentation.setDescription(externalDocsDescription);
            externalDocumentation.setUrl(externalDocsUrl);
            openApiConfig.setExternalDocs(externalDocumentation);
        }
    }

    private void setInfo(
            BeanConfig openApiConfig, String version, String title, String description,
            String termsOfService, String licenseName, String licenseUrl,
            String contactName, String contactUrl, String contactEmail) {
        Info info = new Info().version(version).title(title).description(description).termsOfService(termsOfService);

        if (licenseName != null || licenseUrl != null) {
            License license = new License().name(licenseName).url(licenseUrl);
            info.setLicense(license);
        }

        if (contactName != null || contactUrl != null || contactEmail != null) {
            Contact contact = new Contact().name(contactName).url(contactUrl).email(contactEmail);
            info.setContact(contact);
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
                camelContext.getCamelContextExtension().getBootstrapFactoryFinder(),
                JMX_REST_DEFINITION_RESOLVER,
                RestDefinitionsResolver.class)
                .orElseThrow(
                        () -> new IllegalArgumentException("Cannot find camel-openapi-java on classpath."));
    }

    public void setupXForwardHeaders(RestApiResponseAdapter response, Exchange exchange) {
        setupXForwardedHeaders(response.getOpenApi(), exchange.getMessage().getHeaders());
    }

    public void renderResourceListing(
            CamelContext camelContext, RestApiResponseAdapter response,
            BeanConfig openApiConfig, boolean json,
            ClassResolver classResolver,
            RestConfiguration configuration,
            Exchange exchange)
            throws Exception {

        LOG.trace("renderResourceListing");

        if (cors) {
            setupCorsHeaders(response, configuration.getCorsHeaders());
        }

        List<RestDefinition> rests = getRestDefinitions(camelContext);
        if (rests != null) {
            final Map<String, Object> apiProperties = configuration.getApiProperties() != null
                    ? configuration.getApiProperties() : new HashMap<>();
            String key = json ? "api.specification.contentType.json" : "api.specification.contentType.yaml";
            String defaultValue = json ? "application/json" : "text/yaml";
            response.setHeader(Exchange.CONTENT_TYPE, (String) apiProperties.getOrDefault(key, defaultValue));
            // read the rest-dsl into openApi model
            OpenAPI openApi = response.getOpenApi();
            if (openApi == null) {
                openApi = reader.read(camelContext, rests, openApiConfig, camelContext.getName(), classResolver);
                if (!configuration.isApiVendorExtension()) {
                    clearVendorExtensions(openApi);
                }
            }
            response.setOpenApi(openApi);
            if (configuration.isUseXForwardHeaders() && exchange != null) {
                setupXForwardHeaders(response, exchange);
            }
            byte[] bytes = getFromOpenAPI(openApi, openApiConfig, byte[].class, json);
            int len = bytes.length;
            response.setHeader(Exchange.CONTENT_LENGTH, Integer.toString(len));
            response.writeBytes(bytes);
        } else {
            response.noContent();
        }
    }

    public static String getJsonFromOpenAPIAsString(OpenAPI openApi, BeanConfig config) {
        return getFromOpenAPI(openApi, config, String.class, true);
    }

    public static <T extends Object> T getFromOpenAPI(OpenAPI openApi, BeanConfig config, Class<T> type, boolean json) {
        if (config.isOpenApi2()) {
            throw new IllegalArgumentException("OpenAPI 2.x is not supported");
        } else {
            ObjectMapper mapper = json ? config.isOpenApi31() ? Json31.mapper() : Json.mapper()
                    : config.isOpenApi31() ? Yaml31.mapper() : Yaml.mapper();
            String result = getFromOpenAPI3(openApi, mapper);
            if (type.equals(byte[].class)) {
                if (result != null) {
                    return type.cast(result.getBytes(StandardCharsets.UTF_8));
                } else {
                    return type.cast(new byte[0]);
                }
            }
            return type.cast(result);
        }
    }

    private static String getFromOpenAPI3(OpenAPI openAPI3, ObjectMapper mapper) {
        DateFormat origin = mapper.getDateFormat();
        try {
            mapper.setDateFormat(DEFAULT_DATE_FORMAT);
            return mapper.writer(new DefaultPrettyPrinter()).writeValueAsString(openAPI3);
        } catch (Exception e) {
            return null;
        } finally {
            mapper.setDateFormat(origin);
        }
    }
}
