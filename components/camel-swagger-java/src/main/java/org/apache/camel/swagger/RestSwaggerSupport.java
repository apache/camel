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

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.swagger.jaxrs.config.BeanConfig;
import io.swagger.models.Contact;
import io.swagger.models.Info;
import io.swagger.models.License;
import io.swagger.models.Swagger;
import org.apache.camel.Exchange;
import org.apache.camel.model.ModelHelper;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.RestsDefinition;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.util.CamelVersionHelper;
import org.apache.camel.util.EndpointHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A support class for that allows SPI to plugin
 * and offer Swagger API service listings as part of the Camel component. This allows rest-dsl components
 * such as servlet/jetty/netty4-http to offer Swagger API listings with minimal effort.
 */
public class RestSwaggerSupport {

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
        s = (String) config.get("schemas");
        if (s != null) {
            String[] schemas = s.split(",");
            swaggerConfig.setSchemes(schemas);
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

    public List<RestDefinition> getRestDefinitions(String camelId) throws Exception {
        ObjectName found = null;

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
            }
        }

        if (found != null) {
            String xml = (String) server.invoke(found, "dumpRestsAsXml", null, null);
            if (xml != null) {
                RestsDefinition rests = ModelHelper.createModelFromXml(null, xml, RestsDefinition.class);
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
            String version = (String) server.getAttribute(on, "CamelVersion");
            if (CamelVersionHelper.isGE("2.15.0", version)) {
                answer.add(id);
            }
        }
        return answer;
    }

    public void renderResourceListing(RestApiResponseAdapter response, BeanConfig swaggerConfig, String contextId, String route, ClassResolver classResolver) throws Exception {
        LOG.trace("renderResourceListing");

        if (cors) {
            response.setHeader("Access-Control-Allow-Headers", "Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers");
            response.setHeader("Access-Control-Allow-Methods", "GET, HEAD, POST, PUT, DELETE, TRACE, OPTIONS, CONNECT, PATCH");
            response.setHeader("Access-Control-Allow-Origin", "*");
        }

        List<RestDefinition> rests = getRestDefinitions(contextId);
        if (rests != null) {
            response.setHeader(Exchange.CONTENT_TYPE, "application/json");

            // read the rest-dsl into swagger model
            Swagger swagger = reader.read(rests, route, swaggerConfig, contextId, classResolver);

            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
            byte[] bytes = mapper.writeValueAsBytes(swagger);

            int len = bytes.length;
            response.setHeader(Exchange.CONTENT_LENGTH, "" + len);

            response.writeBytes(bytes);
        } else {
            response.noContent();
        }
    }

    /**
     * Renders a list of available CamelContexts in the JVM
     */
    public void renderCamelContexts(RestApiResponseAdapter response, String contextId, String contextIdPattern) throws Exception {
        LOG.trace("renderCamelContexts");

        if (cors) {
            response.setHeader("Access-Control-Allow-Headers", "Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers");
            response.setHeader("Access-Control-Allow-Methods", "GET, HEAD, POST, PUT, DELETE, TRACE, OPTIONS, CONNECT, PATCH");
            response.setHeader("Access-Control-Allow-Origin", "*");
        }

        response.setHeader(Exchange.CONTENT_TYPE, "application/json");

        StringBuffer sb = new StringBuffer();

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
                    match = EndpointHelper.matchPattern(name, contextIdPattern);
                }
                if (!match) {
                    it.remove();
                }
            }
        }

        sb.append("[\n");
        for (int i = 0; i < contexts.size(); i++) {
            String name = contexts.get(i);
            sb.append("{\"name\": \"").append(name).append("\"}");
            if (i < contexts.size() - 1) {
                sb.append(",\n");
            }
        }
        sb.append("\n]");

        int len = sb.length();
        response.setHeader(Exchange.CONTENT_LENGTH, "" + len);

        response.writeBytes(sb.toString().getBytes());
    }

}
