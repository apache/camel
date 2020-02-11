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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.management.AttributeNotFoundException;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.w3c.dom.Document;

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
import org.apache.camel.support.PatternHelper;
import org.apache.camel.util.CamelVersionHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.xml.XmlLineNumberParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.swagger.SwaggerHelper.clearVendorExtensions;

/**
 * A support class for that allows SPI to plugin
 * and offer Swagger API service listings as part of the Camel component. This allows rest-dsl components
 * such as servlet/jetty/netty-http to offer Swagger API listings with minimal effort.
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
            swaggerConfig.setSchemes(new String[]{"http"});
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
        String xml = ecc.getModelToXMLDumper().dumpModelAsXml(camelContext, def);

        // if resolving placeholders we parse the xml, and resolve the property placeholders during parsing
        final AtomicBoolean changed = new AtomicBoolean();
        InputStream is = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
        Document dom = XmlLineNumberParser.parseXml(is, new XmlLineNumberParser.XmlTextTransformer() {
            @Override
            public String transform(String text) {
                try {
                    String after = camelContext.resolvePropertyPlaceholders(text);
                    if (!changed.get()) {
                        changed.set(!text.equals(after));
                    }
                    return after;
                } catch (Exception e) {
                    // ignore
                    return text;
                }
            }
        });
        // okay there were some property placeholder replaced so re-create the model
        if (changed.get()) {
            xml = camelContext.getTypeConverter().mandatoryConvertTo(String.class, dom);
            InputStream isxml = camelContext.getTypeConverter().convertTo(InputStream.class, xml);
            def = (RestsDefinition) ecc.getXMLRoutesDefinitionLoader().loadRestsDefinition(camelContext, isxml);
            if (def != null) {
                return def.getRests();
            }
        }

        return rests;
    }

    public List<RestDefinition> getRestDefinitions(CamelContext camelContext, String camelId) throws Exception {
        ObjectName found = null;
        boolean supportResolvePlaceholder = false;

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        Set<ObjectName> names = server.queryNames(new ObjectName("org.apache.camel:type=context,*"), null);
        for (ObjectName on : names) {
            String id = on.getKeyProperty("name");
            if (id.startsWith("\"") && id.endsWith("\"")) {
                id = id.substring(1, id.length() - 1);
            }
            if (camelId == null || camelId.equals(id)) {
                // filter out older Camel versions as this requires Camel 2.15 or better (rest-dsl)
                String version = (String) server.getAttribute(on, "CamelVersion");
                if (CamelVersionHelper.isGE("2.15.0", version)) {
                    found = on;
                }
                if (CamelVersionHelper.isGE("2.15.3", version)) {
                    supportResolvePlaceholder = true;
                }
            }
        }

        if (found != null) {
            String xml;
            if (supportResolvePlaceholder) {
                xml = (String) server.invoke(found, "dumpRestsAsXml", new Object[]{true}, new String[]{"boolean"});
            } else {
                xml = (String) server.invoke(found, "dumpRestsAsXml", null, null);
            }
            if (xml != null) {
                LOG.debug("DumpRestAsXml:\n{}", xml);
                InputStream isxml = camelContext.getTypeConverter().convertTo(InputStream.class, xml);
                ExtendedCamelContext ecc = camelContext.adapt(ExtendedCamelContext.class);
                RestsDefinition rests = (RestsDefinition) ecc.getXMLRoutesDefinitionLoader().loadRestsDefinition(camelContext, isxml);
                if (rests != null) {
                    return rests.getRests();
                }
            }
        }

        return null;
    }

    public List<String> findCamelContexts() throws Exception {
        List<String> answer = new ArrayList<>();

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        Set<ObjectName> names = server.queryNames(new ObjectName("*:type=context,*"), null);
        for (ObjectName on : names) {

            String id = on.getKeyProperty("name");
            if (id.startsWith("\"") && id.endsWith("\"")) {
                id = id.substring(1, id.length() - 1);
            }

            // filter out older Camel versions as this requires Camel 2.15 or better (rest-dsl)
            try {
                String version = (String) server.getAttribute(on, "CamelVersion");
                if (CamelVersionHelper.isGE("2.15.0", version)) {
                    answer.add(id);
                }
            } catch (AttributeNotFoundException ex) {
                // ignore
            }
        }
        return answer;
    }

    public void renderResourceListing(CamelContext camelContext, RestApiResponseAdapter response, BeanConfig swaggerConfig, String contextId, String route, boolean json, boolean yaml,
                                      Map<String, Object> headers, ClassResolver classResolver, RestConfiguration configuration) throws Exception {
        LOG.trace("renderResourceListing");
        
        ObjectMapper mapper = Json.mapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        if (cors) {
            setupCorsHeaders(response, configuration.getCorsHeaders());
        }

        List<RestDefinition> rests;
        if (camelContext.getName().equals(contextId)) {
            rests = getRestDefinitions(camelContext);
        } else {
            rests = getRestDefinitions(camelContext, contextId);
        }

        if (rests != null) {
            final Map<String, Object> apiProperties = configuration.getApiProperties() != null ? configuration.getApiProperties() : new HashMap<>();
            if (json) {
                response.setHeader(Exchange.CONTENT_TYPE, (String) apiProperties.getOrDefault("api.specification.contentType.json", "application/json"));

                // read the rest-dsl into swagger model
                Swagger swagger = reader.read(rests, route, swaggerConfig, contextId, classResolver);
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
                response.setHeader(Exchange.CONTENT_TYPE, (String) apiProperties.getOrDefault("api.specification.contentType.yaml", "text/yaml"));

                // read the rest-dsl into swagger model
                Swagger swagger = reader.read(rests, route, swaggerConfig, contextId, classResolver);
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

    /**
     * Renders a list of available CamelContexts in the JVM
     */
    public void renderCamelContexts(RestApiResponseAdapter response, String contextId, String contextIdPattern, boolean json, boolean yaml,
                                    RestConfiguration configuration) throws Exception {
        LOG.trace("renderCamelContexts");

        if (cors) {
            setupCorsHeaders(response, configuration.getCorsHeaders());
        }

        List<String> contexts = findCamelContexts();

        // filter non matched CamelContext's
        if (contextIdPattern != null) {
            Iterator<String> it = contexts.iterator();
            while (it.hasNext()) {
                String name = it.next();

                boolean match;
                if ("#name#".equals(contextIdPattern)) {
                    match = name.equals(contextId);
                } else {
                    match = PatternHelper.matchPattern(name, contextIdPattern);
                }
                if (!match) {
                    it.remove();
                }
            }
        }

        StringBuffer sb = new StringBuffer();

        if (json) {
            response.setHeader(Exchange.CONTENT_TYPE, "application/json");

            sb.append("[\n");
            for (int i = 0; i < contexts.size(); i++) {
                String name = contexts.get(i);
                sb.append("{\"name\": \"").append(name).append("\"}");
                if (i < contexts.size() - 1) {
                    sb.append(",\n");
                }
            }
            sb.append("\n]");
        } else {
            response.setHeader(Exchange.CONTENT_TYPE, "text/yaml");

            for (int i = 0; i < contexts.size(); i++) {
                String name = contexts.get(i);
                sb.append("- \"").append(name).append("\"\n");
            }
        }

        int len = sb.length();
        response.setHeader(Exchange.CONTENT_LENGTH, "" + len);

        response.writeBytes(sb.toString().getBytes());
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
