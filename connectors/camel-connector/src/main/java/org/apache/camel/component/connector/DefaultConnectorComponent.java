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

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.Endpoint;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for Camel Connector components.
 */
public abstract class DefaultConnectorComponent extends DefaultComponent {

    private static final Pattern JAVA_TYPE_PATTERN = Pattern.compile("\"javaType\"\\s?:\\s?\"([\\w|.]+)\".*");
    private static final Pattern BASE_JAVA_TYPE_PATTERN = Pattern.compile("\"baseJavaType\"\\s?:\\s?\"([\\w|.]+)\".*");
    private static final Pattern BASE_SCHEMA_PATTERN = Pattern.compile("\"baseScheme\"\\s?:\\s?\"([\\w|.]+)\".*");

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final CamelCatalog catalog = new DefaultCamelCatalog(false);

    private final String componentName;
    private final String className;
    private List<String> lines;

    public DefaultConnectorComponent(String componentName, String className) {
        this.componentName = componentName;
        this.className = className;

        // add to catalog
        catalog.addComponent(componentName, className);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        String scheme = extractBaseScheme(lines);

        Map<String, String> defaultOptions = extractEndpointDefaultValues(lines);

        // gather all options to use when building the delegate uri
        Map<String, String> options = new LinkedHashMap<>();

        // default options from connector json
        if (!defaultOptions.isEmpty()) {
            options.putAll(defaultOptions);
        }
        // options from query parameters
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            String key = entry.getKey();
            String value = null;
            if (entry.getValue() != null) {
                value = entry.getValue().toString();
            }
            options.put(key, value);
        }
        parameters.clear();

        // add extra options from remaining (context-path)
        if (remaining != null) {
            String targetUri = scheme + ":" + remaining;
            Map<String, String> extra = catalog.endpointProperties(targetUri);
            if (extra != null && !extra.isEmpty()) {
                options.putAll(extra);
            }
        }

        String delegateUri = catalog.asEndpointUri(scheme, options, false);
        log.debug("Connector resolved: {} -> {}", uri, delegateUri);

        Endpoint delegate = getCamelContext().getEndpoint(delegateUri);

        return new DefaultConnectorEndpoint(uri, this, delegate);
    }

    private List<String> findCamelConnectorJSonSchema() throws Exception {
        Enumeration<URL> urls = getClass().getClassLoader().getResources("camel-connector.json");
        while (urls.hasMoreElements()) {
            URL url = urls.nextElement();
            InputStream is = url.openStream();
            if (is != null) {
                List<String> lines = loadFile(is);
                IOHelper.close(is);

                String javaType = extractJavaType(lines);
                log.trace("Found camel-connector.json in classpath with javaType: {}", javaType);

                if (className.equals(javaType)) {
                    return lines;
                }
            }
        }
        return null;
    }

    @Override
    protected void doStart() throws Exception {
        this.lines = findCamelConnectorJSonSchema();
        if (lines == null) {
            throw new IllegalArgumentException("Cannot find camel-connector.json in classpath for connector " + componentName);
        }

        // it may be a custom component so we need to register this in the camel catalog also
        String scheme = extractBaseScheme(lines);
        if (!catalog.findComponentNames().contains(scheme)) {
            String javaType = extractBaseJavaType(lines);
            catalog.addComponent(scheme, javaType);
        }

        // the connector may have default values for the component level also
        // and if so we need to prepare these values and set on this component before we can start

        Map<String, String> defaultOptions = extractComponentDefaultValues(lines);

        if (!defaultOptions.isEmpty()) {
            Map<String, Object> parameters = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : defaultOptions.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (value != null) {
                    // also support {{ }} placeholders so resolve those first
                    value = getCamelContext().resolvePropertyPlaceholders(value);
                    log.debug("Using component option: {}={}", key, value);
                    parameters.put(key, value);
                }
            }
            IntrospectionSupport.setProperties(getCamelContext(), getCamelContext().getTypeConverter(), this, parameters);
        }

        log.debug("Starting connector: {}", componentName);

        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        log.debug("Stopping connector: {}", componentName);

        super.doStop();
    }

    // --------------------------------------------------------------

    private Map<String, String> extractComponentDefaultValues(List<String> lines) {
        Map<String, String> answer = new LinkedHashMap<>();

        // extract the default options
        boolean found = false;
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("\"componentValues\"")) {
                found = true;
            } else if (line.startsWith("}")) {
                found = false;
            } else if (found) {
                int pos = line.indexOf(':');
                String key = line.substring(0, pos);
                String value = line.substring(pos + 1);
                if (value.endsWith(",")) {
                    value = value.substring(0, value.length() - 1);
                }
                key = StringHelper.removeLeadingAndEndingQuotes(key);
                value = StringHelper.removeLeadingAndEndingQuotes(value);
                answer.put(key, value);
            }
        }

        return answer;
    }

    private Map<String, String> extractEndpointDefaultValues(List<String> lines) {
        Map<String, String> answer = new LinkedHashMap<>();

        // extract the default options
        boolean found = false;
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("\"endpointValues\"")) {
                found = true;
            } else if (line.startsWith("}")) {
                found = false;
            } else if (found) {
                int pos = line.indexOf(':');
                String key = line.substring(0, pos);
                String value = line.substring(pos + 1);
                if (value.endsWith(",")) {
                    value = value.substring(0, value.length() - 1);
                }
                key = StringHelper.removeLeadingAndEndingQuotes(key);
                value = StringHelper.removeLeadingAndEndingQuotes(value);
                answer.put(key, value);
            }
        }

        return answer;
    }

    private List<String> loadFile(InputStream fis) throws Exception {
        List<String> lines = new ArrayList<>();
        LineNumberReader reader = new LineNumberReader(new InputStreamReader(fis));

        String line;
        do {
            line = reader.readLine();
            if (line != null) {
                lines.add(line);
            }
        } while (line != null);
        reader.close();

        return lines;
    }

    private String extractJavaType(List<String> json) {
        for (String line : json) {
            line = line.trim();
            Matcher matcher = JAVA_TYPE_PATTERN.matcher(line);
            if (matcher.matches()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    private String extractBaseJavaType(List<String> json) {
        for (String line : json) {
            line = line.trim();
            Matcher matcher = BASE_JAVA_TYPE_PATTERN.matcher(line);
            if (matcher.matches()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    private String extractBaseScheme(List<String> json) {
        for (String line : json) {
            line = line.trim();
            Matcher matcher = BASE_SCHEMA_PATTERN.matcher(line);
            if (matcher.matches()) {
                return matcher.group(1);
            }
        }
        return null;
    }

}

