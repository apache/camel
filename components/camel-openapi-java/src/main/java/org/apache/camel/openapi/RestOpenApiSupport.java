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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.AttributeNotFoundException;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.w3c.dom.Document;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.apicurio.datamodels.openapi.models.OasDocument;
import io.apicurio.datamodels.openapi.v2.models.Oas20Contact;
import io.apicurio.datamodels.openapi.v2.models.Oas20Document;
import io.apicurio.datamodels.openapi.v2.models.Oas20Info;
import io.apicurio.datamodels.openapi.v2.models.Oas20License;
import io.apicurio.datamodels.openapi.v3.models.Oas30Contact;
import io.apicurio.datamodels.openapi.v3.models.Oas30Document;
import io.apicurio.datamodels.openapi.v3.models.Oas30Info;
import io.apicurio.datamodels.openapi.v3.models.Oas30License;
import io.apicurio.datamodels.openapi.v3.models.Oas30Server;
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

import static org.apache.camel.openapi.OpenApiHelper.clearVendorExtensions;

/**
 * A support class for that allows SPI to plugin and offer OpenApi API service listings as part of the Camel
 * component. This allows rest-dsl components such as servlet/jetty/netty-http to offer OpenApi API listings
 * with minimal effort.
 */
public class RestOpenApiSupport {

    static final String HEADER_X_FORWARDED_PREFIX = "X-Forwarded-Prefix";
    static final String HEADER_X_FORWARDED_HOST = "X-Forwarded-Host";
    static final String HEADER_X_FORWARDED_PROTO = "X-Forwarded-Proto";
    static final String HEADER_HOST = "Host";

    private static final Logger LOG = LoggerFactory.getLogger(RestOpenApiSupport.class);
    private RestOpenApiReader reader = new RestOpenApiReader();
    private boolean cors;

    public void initOpenApi(BeanConfig openApiConfig, Map<String, Object> config) {
        // configure openApi options
        String s = (String)config.get("openapi.version");
        if (s != null) {
            openApiConfig.setVersion(s);
        }
        s = (String)config.get("base.path");
        if (s != null) {
            openApiConfig.setBasePath(s);
        }
        s = (String)config.get("host");
        if (s != null) {
            openApiConfig.setHost(s);
        }
        s = (String)config.get("cors");
        if (s != null) {
            cors = "true".equalsIgnoreCase(s);
        }
        s = (String)config.get("schemes");
        if (s == null) {
            // deprecated due typo
            s = (String)config.get("schemas");
        }
        if (s != null) {
            String[] schemes = s.split(",");
            openApiConfig.setSchemes(schemes);
        } else {
            // assume http by default
            openApiConfig.setSchemes(new String[] {"http"});
        }

        String version = (String)config.get("api.version");
        String title = (String)config.get("api.title");
        String description = (String)config.get("api.description");
        String termsOfService = (String)config.get("api.termsOfService");
        String licenseName = (String)config.get("api.license.name");
        String licenseUrl = (String)config.get("api.license.url");
        String contactName = (String)config.get("api.contact.name");
        String contactUrl = (String)config.get("api.contact.url");
        String contactEmail = (String)config.get("api.contact.email");
        
        if (!openApiConfig.isOpenApi3()) {

            setInfoOas20(openApiConfig, version, title, description, termsOfService, licenseName, licenseUrl,
                         contactName, contactUrl, contactEmail);
        } else {
            setInfoOas30(openApiConfig, version, title, description, termsOfService, licenseName, licenseUrl,
                         contactName, contactUrl, contactEmail);
        }

        
    }

    private void setInfoOas30(BeanConfig openApiConfig, String version, String title, String description,
                              String termsOfService, String licenseName, String licenseUrl,
                              String contactName, String contactUrl, String contactEmail) {
        Oas30Info info = new Oas30Info();
        info.version = version;
        info.title = title;
        info.description = description;
        info.termsOfService = termsOfService;

        if (licenseName != null || licenseUrl != null) {
            Oas30License license = new Oas30License();
            license.name = licenseName;
            license.url = licenseUrl;
            info.license = license;
        }

        if (contactName != null || contactUrl != null || contactEmail != null) {
            Oas30Contact contact = new Oas30Contact();
            contact.name = contactName;
            contact.url = contactUrl;
            contact.email = contactEmail;
            info.contact = contact;
            contact._parent = info;
        }
        openApiConfig.setInfo(info);
    }

    private void setInfoOas20(BeanConfig openApiConfig, String version, String title, String description,
                              String termsOfService, String licenseName, String licenseUrl,
                              String contactName, String contactUrl, String contactEmail) {
        Oas20Info info = new Oas20Info();
        info.version = version;
        info.title = title;
        info.description = description;
        info.termsOfService = termsOfService;

        if (licenseName != null || licenseUrl != null) {
            Oas20License license = new Oas20License();
            license.name = licenseName;
            license.url = licenseUrl;
            info.license = license;
        }

        if (contactName != null || contactUrl != null || contactEmail != null) {
            Oas20Contact contact = new Oas20Contact();
            contact.name = contactName;
            contact.url = contactUrl;
            contact.email = contactEmail;
            info.contact = contact;
            contact._parent = info;
        }
        openApiConfig.setInfo(info);
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
            InputStream xmlis = camelContext.getTypeConverter().convertTo(InputStream.class, xml);
            def = (RestsDefinition) ecc.getXMLRoutesDefinitionLoader().loadRestsDefinition(camelContext, xmlis);
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
                String version = (String)server.getAttribute(on, "CamelVersion");
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
                xml = (String)server.invoke(found, "dumpRestsAsXml", new Object[] {true}, 
                                            new String[] {"boolean"});
            } else {
                xml = (String)server.invoke(found, "dumpRestsAsXml", null, null);
            }
            if (xml != null) {
                LOG.debug("DumpRestAsXml:\n{}", xml);
                InputStream xmlis = camelContext.getTypeConverter().convertTo(InputStream.class, xml);
                ExtendedCamelContext ecc = camelContext.adapt(ExtendedCamelContext.class);
                RestsDefinition rests = (RestsDefinition) ecc.getXMLRoutesDefinitionLoader().loadRestsDefinition(camelContext, xmlis);
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
                String version = (String)server.getAttribute(on, "CamelVersion");
                if (CamelVersionHelper.isGE("2.15.0", version)) {
                    answer.add(id);
                }
            } catch (AttributeNotFoundException ex) {
                // ignore
            }
        }
        return answer;
    }

    public void renderResourceListing(CamelContext camelContext, RestApiResponseAdapter response,
                                      BeanConfig openApiConfig, String contextId, String route, boolean json,
                                      boolean yaml, Map<String, Object> headers, ClassResolver classResolver,
                                      RestConfiguration configuration)
        throws Exception {
        LOG.trace("renderResourceListing");

        ObjectMapper mapper = new ObjectMapper();
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
            final Map<String, Object> apiProperties = configuration.getApiProperties() != null
                ? configuration.getApiProperties() : new HashMap<>();
            if (json) {
                response.setHeader(Exchange.CONTENT_TYPE, (String)apiProperties
                    .getOrDefault("api.specification.contentType.json", "application/json"));

                // read the rest-dsl into openApi model
                OasDocument openApi = reader.read(rests, route, openApiConfig, contextId, classResolver);
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
                response.setHeader(Exchange.CONTENT_TYPE, (String)apiProperties
                    .getOrDefault("api.specification.contentType.yaml", "text/yaml"));

                // read the rest-dsl into openApi model
                OasDocument openApi = reader.read(rests, route, openApiConfig, contextId, classResolver);
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

    /**
     * Renders a list of available CamelContexts in the JVM
     */
    public void renderCamelContexts(RestApiResponseAdapter response, String contextId,
                                    String contextIdPattern, boolean json, boolean yaml,
                                    RestConfiguration configuration)
        throws Exception {
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

    static void setupXForwardedHeaders(OasDocument openApi, Map<String, Object> headers) {
        
        String basePath = getBasePathFromOasDocument(openApi);

        if (openApi instanceof Oas20Document) {
            String host = (String)headers.get(HEADER_HOST);
            if (ObjectHelper.isNotEmpty(host)) {
                ((Oas20Document)openApi).host = host;
            }

            String forwardedPrefix = (String)headers.get(HEADER_X_FORWARDED_PREFIX);
            if (ObjectHelper.isNotEmpty(forwardedPrefix)) {
                ((Oas20Document)openApi).basePath = URISupport.joinPaths(forwardedPrefix, basePath);
            }

            String forwardedHost = (String)headers.get(HEADER_X_FORWARDED_HOST);
            if (ObjectHelper.isNotEmpty(forwardedHost)) {
                ((Oas20Document)openApi).host = forwardedHost;
            }

            String proto = (String)headers.get(HEADER_X_FORWARDED_PROTO);
            if (ObjectHelper.isNotEmpty(proto)) {
                String[] schemes = proto.split(",");
                for (String scheme : schemes) {
                    String trimmedScheme = scheme.trim();
                    if (ObjectHelper.isNotEmpty(trimmedScheme)) {
                        if (((Oas20Document)openApi).schemes == null) {
                            ((Oas20Document)openApi).schemes = new ArrayList();
                        }
                        ((Oas20Document)openApi).schemes.add(trimmedScheme.toLowerCase());
                    }
                }
            }
        } else if (openApi instanceof Oas30Document) {
            
            String host = (String)headers.get(HEADER_HOST);
            

            String forwardedPrefix = (String)headers.get(HEADER_X_FORWARDED_PREFIX);
                            
            if (ObjectHelper.isNotEmpty(forwardedPrefix)) {
                basePath = URISupport.joinPaths(forwardedPrefix, basePath);
            }

            String forwardedHost = (String)headers.get(HEADER_X_FORWARDED_HOST);
            if (ObjectHelper.isNotEmpty(forwardedHost)) {
                host = forwardedHost;
            }

            String proto = (String)headers.get(HEADER_X_FORWARDED_PROTO);
            if (((Oas30Document)openApi).getServers() != null) {
                ((Oas30Document)openApi).getServers().clear();
            }
            if (ObjectHelper.isNotEmpty(proto)) {
                String[] schemes = proto.split(",");
                for (String schema : schemes) {
                    String trimmedScheme = schema.trim();
                    String serverUrl = new StringBuilder().append(trimmedScheme.toLowerCase()).append("://").append(host).append(basePath).toString();
                    ((Oas30Document)openApi).addServer(serverUrl, null);
                        
                }
            } else {
                ((Oas30Document)openApi).addServer(basePath, null);
            }
            
        }
    }
    
    public static String getHostFromOasDocument(final OasDocument openapi) {
        String host = null;
        if (openapi instanceof Oas20Document) {
            host = ((Oas20Document)openapi).host;
        } else if (openapi instanceof Oas30Document) {
            if (((Oas30Document)openapi).getServers() != null 
                && ((Oas30Document)openapi).getServers().get(0) != null) {
                try {
                    URL serverUrl = new URL(parseVariables(((Oas30Document)openapi).getServers().get(0).url, (Oas30Server)((Oas30Document)openapi).getServers().get(0)));
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
            basePath = ((Oas20Document)openapi).basePath;
        } else if (openapi instanceof Oas30Document) {
            if (((Oas30Document)openapi).getServers() != null 
                && ((Oas30Document)openapi).getServers().get(0) != null) {
                try {
                    Oas30Server server = (Oas30Server)((Oas30Document)openapi).getServers().get(0);
                    if (server.variables != null && server.variables.get("basePath") != null) {
                        basePath = server.variables.get("basePath").default_;
                    }
                    if (basePath == null) {
                        // parse server url as fallback
                        URL serverUrl = new URL(parseVariables(((Oas30Document)openapi).getServers().get(0).url, (Oas30Server)((Oas30Document)openapi).getServers().get(0)));
                        basePath = serverUrl.getPath();
                        if (basePath.indexOf("//") == 0) {
                            // strip off the first "/" if double "/" exists
                            basePath = basePath.substring(1);
                        }
                        if ("/".equals(basePath)) {
                            basePath = "";
                        }
                    } 
                                    
                } catch (MalformedURLException e) {
                    //not a valid whole url, just the basePath
                    basePath = ((Oas30Document)openapi).getServers().get(0).url;
                }
            }
            
        }
        return basePath;
        
    }
    
    public static String parseVariables(String url, Oas30Server server) {
        Pattern p = Pattern.compile("\\{(.*?)\\}");
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
}


