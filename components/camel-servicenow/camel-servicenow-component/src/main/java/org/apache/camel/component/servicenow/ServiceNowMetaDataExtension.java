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
package org.apache.camel.component.servicenow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.extension.MetaDataExtension;
import org.apache.camel.component.extension.metadata.AbstractMetaDataExtension;
import org.apache.camel.component.extension.metadata.MetaDataBuilder;
import org.apache.camel.component.servicenow.model.DictionaryEntry;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of the MetaData extension {@link MetaDataExtension} that
 * retrieve information about ServiceNow objects as Json Schema as per draft-04
 * specs.
 */
final class ServiceNowMetaDataExtension extends AbstractMetaDataExtension {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceNowMetaDataExtension.class);
    private final ConcurrentMap<String, String> properties;

    ServiceNowMetaDataExtension() {
        this.properties = new ConcurrentHashMap<>();
    }

    @Override
    public Optional<MetaDataExtension.MetaData> meta(Map<String, Object> parameters) {
        try {
            final MetaContext context = new MetaContext(parameters);

            if (!ObjectHelper.equalIgnoreCase(context.getObjectType(), "table")) {
                throw new UnsupportedOperationException("Unsupported object type <" + context.getObjectType() + ">");
            }

            return tableMeta(context);

        } catch (UnsupportedOperationException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Optional<MetaDataExtension.MetaData> tableMeta(MetaContext context) {
        try {
            final List<String> names = getObjectHierarchy(context);
            final ObjectNode root = context.getConfiguration().getOrCreateMapper().createObjectNode();
            final String baseUrn = (String)context.getParameters().getOrDefault("baseUrn", "org:apache:camel:component:servicenow");

            if (names.isEmpty()) {
                return Optional.empty();
            }

            // Schema
            root.put("$schema", "http://json-schema.org/schema#");
            root.put("id", String.format("urn:jsonschema:%s:%s)", baseUrn, context.getObjectName()));
            root.put("type", "object");
            root.put("additionalProperties", false);

            // Schema sections
            root.putObject("properties");
            root.putArray("required");

            loadProperties(context);

            for (String name : names) {
                context.getStack().push(name);

                LOGGER.debug("Load dictionary <{}>", context.getStack());
                loadDictionary(context, name, root);
                context.getStack().pop();
            }

            final String dateFormat = properties.getOrDefault("glide.sys.date_format", "yyyy-MM-dd");
            final String timeFormat = properties.getOrDefault("glide.sys.time_format", "HH:mm:ss");

            return Optional.of(
                MetaDataBuilder.on(getCamelContext())
                    .withAttribute(MetaData.CONTENT_TYPE, "application/schema+json")
                    .withAttribute(MetaData.JAVA_TYPE, JsonNode.class)
                    .withAttribute("date.format", dateFormat)
                    .withAttribute("time.format", timeFormat)
                    .withAttribute("date-time.format", dateFormat + " " + timeFormat)
                    .withPayload(root)
                    .build()
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ********************************
    // Properties
    // ********************************

    private synchronized void loadProperties(MetaContext context) {
        if (!properties.isEmpty()) {
            return;
        }

        try {
            String offset = "0";

            while (true) {
                Response response = context.getClient().reset()
                    .types(MediaType.APPLICATION_JSON_TYPE)
                    .path("now")
                    .path(context.getConfiguration().getApiVersion())
                    .path("table")
                    .path("sys_properties")
                    .query("sysparm_exclude_reference_link", "true")
                    .query("sysparm_fields", "name%2Cvalue")
                    .query("sysparm_offset", offset)
                    .query("sysparm_query", "name=glide.sys.date_format^ORname=glide.sys.time_format")
                    .invoke(HttpMethod.GET);

                findResultNode(response).ifPresent(node -> processResult(node, n -> {
                    if (n.hasNonNull("name") && n.hasNonNull("value")) {
                        properties.put(
                            n.findValue("name").asText(),
                            n.findValue("value").asText()
                        );
                    }
                }));

                Optional<String> next = ServiceNowHelper.findOffset(response, ServiceNowConstants.LINK_NEXT);
                if (next.isPresent()) {
                    offset = next.get();
                } else {
                    break;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ********************************
    // Dictionary
    // ********************************

    private void loadDictionary(MetaContext context, String name, ObjectNode root) throws Exception {
        String offset = "0";

        while (true) {
            Response response = context.getClient().reset()
                .types(MediaType.APPLICATION_JSON_TYPE)
                .path("now")
                .path(context.getConfiguration().getApiVersion())
                .path("table")
                .path("sys_dictionary")
                .query("sysparm_display_value", "false")
                .queryF("sysparm_query", "name=%s", name)
                .query("sysparm_offset", offset)
                .invoke(HttpMethod.GET);

            findResultNode(response).ifPresent(node -> processResult(node, n -> {
                processDictionaryNode(context, root, n);
            }));

            Optional<String> next = ServiceNowHelper.findOffset(response, ServiceNowConstants.LINK_NEXT);
            if (next.isPresent()) {
                offset = next.get();
            } else {
                break;
            }
        }
    }

    private void processDictionaryNode(MetaContext context, ObjectNode root, JsonNode node) {
        if (node.hasNonNull("element")) {
            final String id = node.get("element").asText();

            if (ObjectHelper.isNotEmpty(id)) {
                String includeKey = "object." + context.getObjectName() + ".fields";
                String excludeKey = "object." + context.getObjectName() + ".fields.exclude.pattern";
                String fields = (String)context.getParameters().get(includeKey);
                String exclude = (String)context.getParameters().get(excludeKey);

                boolean included = true;

                if (ObjectHelper.isNotEmpty(fields) && ObjectHelper.isNotEmpty(exclude)) {
                    boolean isIncluded = Stream.of(fields.split(",")).map(StringHelper::trimToNull).filter(Objects::nonNull).anyMatch(id::equalsIgnoreCase);
                    boolean isExcluded = Pattern.compile(exclude).matcher(id).matches();

                    // if both include/exclude list is provided check if the
                    // fields ie either explicit included or not excluded.
                    //
                    // This is useful if you want to exclude all the i.e. sys_
                    // fields but want some i.e. the sys_id to be included
                    included = isIncluded || !isExcluded;
                } else if (ObjectHelper.isNotEmpty(fields)) {
                    // Only include fields that are explicit included
                    included = Stream.of(fields.split(",")).map(StringHelper::trimToNull).filter(Objects::nonNull).anyMatch(id::equalsIgnoreCase);
                } else if (ObjectHelper.isNotEmpty(exclude)) {
                    // Only include fields non excluded
                    included = !Pattern.compile(exclude).matcher(id).matches();
                }

                if (!included) {
                    return;
                }

                context.getStack().push(id);
                LOGGER.debug("Load dictionary element <{}>", context.getStack());

                try {
                    final DictionaryEntry entry = context.getConfiguration().getOrCreateMapper().treeToValue(node, DictionaryEntry.class);
                    final ObjectNode property = ((ObjectNode)root.get("properties")).putObject(id);

                    // Add custom fields for code generation, json schema
                    // validators are not supposed to use this extensions.
                    final ObjectNode servicenow = property.putObject("servicenow");

                    // the internal type
                    servicenow.put("internal_type", entry.getInternalType().getValue());

                    switch (entry.getInternalType().getValue()) {
                    case "integer":
                        property.put("type", "integer");
                        break;
                    case "float":
                        property.put("type", "number");
                        break;
                    case "boolean":
                        property.put("type", "boolean");
                        break;
                    case "guid":
                    case "GUID":
                        property.put("type", "string");
                        property.put("pattern", "^[a-fA-F0-9]{32}");
                        break;
                    case "glide_date":
                        property.put("type", "string");
                        property.put("format", "date");
                        break;
                    case "due_date":
                    case "glide_date_time":
                    case "glide_time":
                    case "glide_duration":
                        property.put("type", "string");
                        property.put("format", "date-time");
                        break;
                    case "reference":
                        property.put("type", "string");
                        property.put("pattern", "^[a-fA-F0-9]{32}");

                        if (entry.getReference().getValue() != null) {
                            // the referenced object type
                            servicenow.put("sys_db_object", entry.getReference().getValue());
                        }

                        break;
                    default:
                        property.put("type", "string");

                        if (entry.getMaxLength() != null) {
                            property.put("maxLength", entry.getMaxLength());
                        }
                        break;
                    }

                    if (entry.isMandatory()) {
                        ArrayNode required = (ArrayNode)root.get("required");
                        if (required == null) {
                            required = root.putArray("required");
                        }

                        required.add(id);
                    }

                } catch (JsonProcessingException e) {
                    throw new RuntimeCamelException(e);
                } finally {

                    context.getStack().pop();
                }
            }
        }
    }

    // *************************************
    // Helpers
    // *************************************

    private List<String> getObjectHierarchy(MetaContext context) throws Exception {
        List<String> hierarchy = new ArrayList<>();
        String query = String.format("name=%s", context.getObjectName());

        while (true) {
            Optional<JsonNode> response = context.getClient().reset()
                .types(MediaType.APPLICATION_JSON_TYPE)
                .path("now")
                .path(context.getConfiguration().getApiVersion())
                .path("table")
                .path("sys_db_object")
                .query("sysparm_exclude_reference_link", "true")
                .query("sysparm_fields", "name%2Csuper_class")
                .query("sysparm_query", query)
                .trasform(HttpMethod.GET, this::findResultNode);

            if (response.isPresent()) {
                JsonNode node = response.get();
                JsonNode nameNode = node.findValue("name");
                JsonNode classNode = node.findValue("super_class");

                if (nameNode != null && classNode != null) {
                    query = String.format("sys_id=%s", classNode.textValue());
                    hierarchy.add(0, nameNode.textValue());
                } else {
                    break;
                }
            } else {
                break;
            }
        }

        return hierarchy;
    }

    private void processResult(JsonNode node, Consumer<JsonNode> consumer) {
        if (node.isArray()) {
            Iterator<JsonNode> it = node.elements();
            while (it.hasNext()) {
                consumer.accept(it.next());
            }
        } else {
            consumer.accept(node);
        }
    }

    private Optional<JsonNode> findResultNode(Response response) {
        if (ObjectHelper.isNotEmpty(response.getHeaderString(HttpHeaders.CONTENT_TYPE))) {
            JsonNode root = response.readEntity(JsonNode.class);
            if (root != null) {
                Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
                while (fields.hasNext()) {
                    final Map.Entry<String, JsonNode> entry = fields.next();
                    final String key = entry.getKey();
                    final JsonNode node = entry.getValue();

                    if (ObjectHelper.equal("result", key, true)) {
                        return Optional.of(node);
                    }
                }
            }
        }

        return Optional.empty();
    }

    // *********************************
    // Context class
    // *********************************

    private final class MetaContext {
        private final Map<String, Object> parameters;
        private final ServiceNowConfiguration configuration;
        private final ServiceNowClient client;
        private final String instanceName;
        private final String objectName;
        private final String objectType;
        private final Stack<String> stack;

        MetaContext(Map<String, Object> parameters) throws Exception {
            this.parameters = parameters;
            this.configuration = getComponent(ServiceNowComponent.class).getConfiguration().copy();
            this.stack = new Stack<>();

            IntrospectionSupport.setProperties(configuration, new HashMap<>(parameters));

            this.instanceName = (String)parameters.getOrDefault("instanceName", getComponent(ServiceNowComponent.class).getInstanceName());
            this.objectType = (String)parameters.getOrDefault("objectType", "table");
            this.objectName = (String)parameters.getOrDefault("objectName", configuration.getTable());

            ObjectHelper.notNull(instanceName, "instanceName");
            ObjectHelper.notNull(objectName, "objectName");
            ObjectHelper.notNull(objectType, "objectType");

            // Configure Api and OAuthToken ULRs using instanceName
            if (!configuration.hasApiUrl()) {
                configuration.setApiUrl(String.format("https://%s.service-now.com/api", instanceName));
            }
            if (!configuration.hasOauthTokenUrl()) {
                configuration.setOauthTokenUrl(String.format("https://%s.service-now.com/oauth_token.do", instanceName));
            }

            this.client = new ServiceNowClient(getCamelContext(), configuration);
        }

        public Map<String, Object> getParameters() {
            return parameters;
        }

        public ServiceNowConfiguration getConfiguration() {
            return configuration;
        }

        public ServiceNowClient getClient() {
            return client;
        }

        public String getInstanceName() {
            return instanceName;
        }

        public String getObjectType() {
            return objectType;
        }

        public String getObjectName() {
            return objectName;
        }

        public Stack<String> getStack() {
            return stack;
        }
    }

}
