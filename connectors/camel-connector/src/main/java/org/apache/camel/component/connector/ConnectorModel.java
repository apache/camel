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
package org.apache.camel.component.connector;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import org.apache.camel.json.simple.DeserializationException;
import org.apache.camel.json.simple.JsonObject;
import org.apache.camel.json.simple.Jsoner;
import org.apache.camel.util.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ConnectorModel {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectorModel.class);

    private final String componentName;
    private final String className;

    private String connectorJSon;
    private String baseScheme;
    private String baseJavaType;
    private String scheduler;
    private String connectorName;
    private DataType inputDataType;
    private DataType outputDataType;
    private Map<String, Object> defaultComponentOptions;
    private Map<String, Object> defaultEndpointOptions;
    private List<String> endpointOptions;
    private List<String> componentOptions;
    private Map<String, Object> connectorOptions;

    @SuppressWarnings("unchecked")
    ConnectorModel(String componentName, Class<?> componentClass) {
        this.componentName = componentName;
        this.className = componentClass.getName();
        this.connectorJSon = findCamelConnectorJSonSchema(componentClass);

        // parse the json
        JsonObject json;
        try {
            json = (JsonObject) Jsoner.deserialize(connectorJSon);
        } catch (DeserializationException e) {
            throw new RuntimeException("Error parsing camel-connector.json file due " + e.getMessage(), e);
        }

        this.connectorName = json.getString("name");
        this.baseScheme = json.getString("baseScheme");
        this.baseJavaType = json.getString("baseJavaType");
        this.scheduler = json.getString("scheduler");
        String type = json.getString("inputDataType");
        if (type != null) {
            this.inputDataType = new DataType(type);
        }
        type = json.getString("outputDataType");
        if (type != null) {
            this.outputDataType = new DataType(type);
        }

        this.defaultComponentOptions = json.getMap("componentValues");
        if (this.defaultComponentOptions == null) {
            this.defaultComponentOptions = Collections.EMPTY_MAP;
        }
        this.defaultEndpointOptions = json.getMap("endpointValues");
        if (this.defaultEndpointOptions == null) {
            this.defaultEndpointOptions = Collections.EMPTY_MAP;
        }
        this.endpointOptions = json.getCollection("endpointOptions");
        if (this.endpointOptions == null) {
            this.endpointOptions = Collections.EMPTY_LIST;
        }
        this.componentOptions = json.getCollection("componentOptions");
        if (this.componentOptions == null) {
            this.componentOptions = Collections.EMPTY_LIST;
        }
        this.connectorOptions = json.getMap("connectorProperties");
        if (this.connectorOptions == null) {
            this.connectorOptions = Collections.EMPTY_MAP;
        }
    }

    public String getComponentName() {
        return componentName;
    }

    public String getClassName() {
        return className;
    }

    public String getConnectorJSon() {
        return connectorJSon;
    }

    public String getBaseScheme() {
        return baseScheme;
    }

    public String getBaseJavaType() {
        return baseJavaType;
    }

    public String getScheduler() {
        return scheduler;
    }

    public String getConnectorName() {
        return connectorName;
    }

    public DataType getInputDataType() {
        return inputDataType;
    }

    public DataType getOutputDataType() {
        return outputDataType;
    }

    public Map<String, Object> getDefaultComponentOptions() {
        return defaultComponentOptions;
    }

    public Map<String, Object> getDefaultEndpointOptions() {
        return defaultEndpointOptions;
    }

    public List<String> getEndpointOptions() {
        return endpointOptions;
    }

    public List<String> getComponentOptions() {
        return componentOptions;
    }

    public Map<String, Object> getConnectorOptions() {
        return connectorOptions;
    }

    // ***************************************
    // Helpers
    // ***************************************

    private String findCamelConnectorJSonSchema(Class<?> componentClass) {
        LOGGER.debug("Finding camel-connector.json in classpath for connector: {}", componentName);

        Enumeration<URL> urls;
        try {
            urls = componentClass.getClassLoader().getResources("camel-connector.json");
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot open camel-connector.json in classpath for connector " + componentName);
        }

        while (urls.hasMoreElements()) {
            try (InputStream is = urls.nextElement().openStream()) {
                String json = IOHelper.loadText(is);

                JsonObject output = (JsonObject) Jsoner.deserialize(json);
                String javaType = output.getString("javaType");

                LOGGER.debug("Found camel-connector.json in classpath with javaType: {}", javaType);

                if (className.equals(javaType)) {
                    return json;
                }
            } catch (Exception e) {
                throw new IllegalArgumentException("Cannot read camel-connector.json in classpath for connector " + componentName);
            }
        }

        return null;
    }

}
