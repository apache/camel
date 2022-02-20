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
package org.apache.camel.swagger;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.swagger.jaxrs.config.BeanConfig;
import io.swagger.models.Contact;
import io.swagger.models.Info;
import io.swagger.models.License;
import io.swagger.models.Scheme;
import io.swagger.models.Swagger;
import io.swagger.util.Json;
import io.swagger.util.Yaml;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.model.Model;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.RestsDefinition;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.swagger.SwaggerHelper.clearVendorExtensions;

/**
 * A support class for that allows SPI to plugin and offer Swagger API service listings as part of the Camel component.
 * This allows rest-dsl components such as servlet/jetty/netty-http to offer Swagger API listings with minimal effort.
 */
public class RestSwaggerSupport {

    static final String HEADER_X_FORWARDED_PREFIX = "X-Forwarded-Prefix";
    static final String HEADER_X_FORWARDED_HOST = "X-Forwarded-Host";
    static final String HEADER_X_FORWARDED_PROTO = "X-Forwarded-Proto";
    static final String HEADER_HOST = "Host";

    private static final Logger LOG = LoggerFactory.getLogger(RestSwaggerSupport.class);
    private RestSwaggerReader reader = new RestSwaggerReader();
    private boolean cors;

    public void initSwagger(BeanConfig swaggerConfig, Map<String, Object> config) {
        // configure swagger options
        String s = (String) config.get("swagger.version");
        if (s != null) {
            swaggerConfig.setVersion(s);
        }
        s = (String) config.get("base.path");
        if (s != null) {
            swaggerConfig.setBasePath(s);
        }
        s = (String) config.get("host");
        if (s != null) {
            swaggerConfig.setHost(s);
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
            swaggerConfig.setSchemes(schemes);
        } else {
            // assume http by default
            swaggerConfig.setSchemes(new String[] { "http" });
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

    public List<RestDefinition> getRestDefinitions(CamelContext camelContext) throws Exception {
        Model model = camelContext.getExtension(Model.class);
        List<RestDefinition> rests = model.getRestDefinitions();
        if (rests.isEmpty()) {
            return null;
        }

        // use a routes definition to dump the rests
        RestsDefinition def = new RestsDefinition();
        def.setRests(rests);

        ExtendedCamelContext ecc = camelContext.adapt(ExtendedCamelContext.class);
        String originalXml = ecc.getModelToXMLDumper().dumpModelAsXml(camelContext, def);
        String changedXml = ecc.getModelToXMLDumper().dumpModelAsXml(camelContext, def, true, true);
        if (!Objects.equals(originalXml, changedXml)) {
            // okay so the model had property placeholders which we needed to resolve and output their actual values
            // and therefore regenerate the model classes
            InputStream isxml = camelContext.getTypeConverter().convertTo(InputStream.class, changedXml);
            def = (RestsDefinition) ecc.getXMLRoutesDefinitionLoader().loadRestsDefinition(camelContext, isxml);
            if (def != null) {
                rests = def.getRests();
            }
        }

        return rests;
    }

    public void renderResourceListing(
            CamelContext camelContext, RestApiResponseAdapter response, BeanConfig swaggerConfig,
            boolean json, boolean yaml,
            Map<String, Object> headers, ClassResolver classResolver, RestConfiguration configuration)
            throws Exception {
        LOG.trace("renderResourceListing");

        ObjectMapper mapper = Json.mapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        if (cors) {
            setupCorsHeaders(response, configuration.getCorsHeaders());
        }

        List<RestDefinition> rests = getRestDefinitions(camelContext);

        if (rests != null) {
            final Map<String, Object> apiProperties
                    = configuration.getApiProperties() != null ? configuration.getApiProperties() : new HashMap<>();
            if (json) {
                response.setHeader(Exchange.CONTENT_TYPE,
                        (String) apiProperties.getOrDefault("api.specification.contentType.json", "application/json"));

                // read the rest-dsl into swagger model
                Swagger swagger = reader.read(camelContext, rests, swaggerConfig, camelContext.getName(), classResolver);
                if (configuration.isUseXForwardHeaders()) {
                    setupXForwardedHeaders(swagger, headers);
                }

                if (!configuration.isApiVendorExtension()) {
                    clearVendorExtensions(swagger);
                }

                byte[] bytes = mapper.writeValueAsBytes(swagger);

                int len = bytes.length;
                response.setHeader(Exchange.CONTENT_LENGTH, "" + len);

                response.writeBytes(bytes);
            } else {
                response.setHeader(Exchange.CONTENT_TYPE,
                        (String) apiProperties.getOrDefault("api.specification.contentType.yaml", "text/yaml"));

                // read the rest-dsl into swagger model
                Swagger swagger = reader.read(camelContext, rests, swaggerConfig, camelContext.getName(), classResolver);
                if (configuration.isUseXForwardHeaders()) {
                    setupXForwardedHeaders(swagger, headers);
                }

                if (!configuration.isApiVendorExtension()) {
                    clearVendorExtensions(swagger);
                }

                byte[] jsonData = mapper.writeValueAsBytes(swagger);

                // json to yaml
                JsonNode node = mapper.readTree(jsonData);
                byte[] bytes = Yaml.mapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(node);

                int len = bytes.length;
                response.setHeader(Exchange.CONTENT_LENGTH, "" + len);

                response.writeBytes(bytes);
            }
        } else {
            response.noContent();
        }
    }

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

    static void setupXForwardedHeaders(Swagger swagger, Map<String, Object> headers) {

        String host = (String) headers.get(HEADER_HOST);
        if (ObjectHelper.isNotEmpty(host)) {
            swagger.setHost(host);
        }

        String forwardedPrefix = (String) headers.get(HEADER_X_FORWARDED_PREFIX);
        if (ObjectHelper.isNotEmpty(forwardedPrefix)) {
            swagger.setBasePath(URISupport.joinPaths(forwardedPrefix, swagger.getBasePath()));
        }

        String forwardedHost = (String) headers.get(HEADER_X_FORWARDED_HOST);
        if (ObjectHelper.isNotEmpty(forwardedHost)) {
            swagger.setHost(forwardedHost);
        }

        String proto = (String) headers.get(HEADER_X_FORWARDED_PROTO);
        if (ObjectHelper.isNotEmpty(proto)) {
            String[] schemes = proto.split(",");
            for (String scheme : schemes) {
                String trimmedScheme = scheme.trim();
                if (ObjectHelper.isNotEmpty(trimmedScheme)) {
                    swagger.addScheme(Scheme.forValue(trimmedScheme));
                }
            }
        }
    }

}
