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
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.camel.util.StringHelper;
import org.apache.camel.util.function.Suppliers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ConnectorModel {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectorModel.class);

    private static final Pattern NAME_PATTERN = Pattern.compile("\"name\"\\s?:\\s?\"([\\w|.-]+)\".*");
    private static final Pattern JAVA_TYPE_PATTERN = Pattern.compile("\"javaType\"\\s?:\\s?\"([\\w|.]+)\".*");
    private static final Pattern BASE_JAVA_TYPE_PATTERN = Pattern.compile("\"baseJavaType\"\\s?:\\s?\"([\\w|.]+)\".*");
    private static final Pattern BASE_SCHEME_PATTERN = Pattern.compile("\"baseScheme\"\\s?:\\s?\"([\\w|.-]+)\".*");
    private static final Pattern SCHEDULER_PATTERN = Pattern.compile("\"scheduler\"\\s?:\\s?\"([\\w|.-]+)\".*");
    private static final Pattern INPUT_DATA_TYPE_PATTERN = Pattern.compile("\"inputDataType\"\\s?:\\s?\"(\\*|[\\w|.:*]+)\".*");
    private static final Pattern OUTPUT_DATA_TYPE_PATTERN = Pattern.compile("\"outputDataType\"\\s?:\\s?\"([\\w|.:*]+)\".*");

    private final String componentName;
    private final String className;
    private final Supplier<List<String>> lines;

    private String baseScheme;
    private String baseJavaType;
    private String scheduler;
    private String connectorJSon;
    private String connectorName;
    private DataType inputDataType;
    private DataType outputDataType;
    private Map<String, String> defaultComponentOptions;
    private Map<String, String> defaultEndpointOptions;
    private List<String> endpointOptions;
    private List<String> componentOptions;

    ConnectorModel(String componentName, String className) {
        this.componentName = componentName;
        this.className = className;
        this.lines = Suppliers.memorize(() -> findCamelConnectorJSonSchema());
    }

    public String getComponentName() {
        return componentName;
    }

    public String getClassName() {
        return className;
    }

    public String getBaseScheme() {
        if (baseScheme == null) {
            baseScheme = extractBaseScheme(lines.get());
        }

        return baseScheme;
    }

    public String getBaseJavaType() {
        if (baseJavaType == null) {
            baseJavaType = extractBaseJavaType(lines.get());
        }

        return baseJavaType;
    }

    public String getScheduler() {
        if (scheduler == null) {
            scheduler = extractScheduler(lines.get());
        }

        return scheduler;
    }

    public String getConnectorName() {
        if (connectorName == null) {
            connectorName = extractName(lines.get());
        }

        return connectorName;
    }

    public String getConnectorJSon() {
        if (connectorJSon == null) {
            connectorJSon = lines.get().stream().collect(Collectors.joining("\n"));
        }

        return connectorJSon;
    }

    public Map<String, String> getDefaultComponentOptions() {
        if (defaultComponentOptions == null) {
            defaultComponentOptions = Collections.unmodifiableMap(extractComponentDefaultValues(lines.get()));
        }

        return defaultComponentOptions;
    }

    public Map<String, String> getDefaultEndpointOptions() {
        if (defaultEndpointOptions == null) {
            defaultEndpointOptions = Collections.unmodifiableMap(extractEndpointDefaultValues(lines.get()));
        }

        return defaultEndpointOptions;
    }

    public List<String> getEndpointOptions() {
        if (endpointOptions == null) {
            endpointOptions = Collections.unmodifiableList(extractEndpointOptions(lines.get()));
        }

        return endpointOptions;
    }

    public List<String> getComponentOptions() {
        if (endpointOptions == null) {
            endpointOptions = Collections.unmodifiableList(extractComponentOptions(lines.get()));
        }

        return endpointOptions;
    }

    public DataType getInputDataType() {
        if (inputDataType == null) {
            String line = extractInputDataType(lines.get());
            if (line != null) {
                inputDataType = new DataType(line);
            }
        }
        return inputDataType;
    }

    public DataType getOutputDataType() {
        if (outputDataType == null) {
            String line = extractOutputDataType(lines.get());
            if (line != null) {
                outputDataType = new DataType(line);
            }
        }
        return outputDataType;
    }

    // ***************************************
    // Helpers
    // ***************************************

    private List<String> findCamelConnectorJSonSchema() {
        LOGGER.debug("Finding camel-connector.json in classpath for connector: {}", componentName);

        Enumeration<URL> urls;
        try {
            urls = ConnectorModel.class.getClassLoader().getResources("camel-connector.json");
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot open camel-connector.json in classpath for connector " + componentName);
        }

        while (urls.hasMoreElements()) {
            try (InputStream is = urls.nextElement().openStream()) {
                List<String> lines = loadFile(is);

                String javaType = extractJavaType(lines);
                LOGGER.debug("Found camel-connector.json in classpath with javaType: {}", javaType);

                if (className.equals(javaType)) {
                    return lines;
                }
            } catch (Exception e) {
                throw new IllegalArgumentException("Cannot read camel-connector.json in classpath for connector " + componentName);
            }
        }

        return Collections.emptyList();
    }

    private static List<String> loadFile(InputStream fis) throws Exception {
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

    private static String extractName(List<String> json) {
        for (String line : json) {
            line = line.trim();
            Matcher matcher = NAME_PATTERN.matcher(line);
            if (matcher.matches()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    private static String extractJavaType(List<String> json) {
        for (String line : json) {
            line = line.trim();
            Matcher matcher = JAVA_TYPE_PATTERN.matcher(line);
            if (matcher.matches()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    private static String extractBaseJavaType(List<String> json) {
        for (String line : json) {
            line = line.trim();
            Matcher matcher = BASE_JAVA_TYPE_PATTERN.matcher(line);
            if (matcher.matches()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    private static String extractScheduler(List<String> json) {
        for (String line : json) {
            line = line.trim();
            Matcher matcher = SCHEDULER_PATTERN.matcher(line);
            if (matcher.matches()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    private static String extractBaseScheme(List<String> json) {
        for (String line : json) {
            line = line.trim();
            Matcher matcher = BASE_SCHEME_PATTERN.matcher(line);
            if (matcher.matches()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    private static String extractInputDataType(List<String> json) {
        for (String line : json) {
            line = line.trim();
            Matcher matcher = INPUT_DATA_TYPE_PATTERN.matcher(line);
            if (matcher.matches()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    private static String extractOutputDataType(List<String> json) {
        for (String line : json) {
            line = line.trim();
            Matcher matcher = OUTPUT_DATA_TYPE_PATTERN.matcher(line);
            if (matcher.matches()) {
                return matcher.group(1);
            }
        }
        return null;
    }

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
                value = value.trim();
                key = key.trim();
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
                value = value.trim();
                key = key.trim();
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

    private List<String> extractComponentOptions(List<String> lines) {
        List<String> answer = new ArrayList<>();

        // extract the default options
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("\"componentOptions\"")) {
                int start = line.indexOf('[');
                if (start == -1) {
                    throw new IllegalStateException("Malformed camel-connector.json");
                }

                int end = line.indexOf(']', start);
                if (end == -1) {
                    throw new IllegalStateException("Malformed camel-connector.json");
                }

                line = line.substring(start + 1, end).trim();
                for (String option : line.split(",")) {
                    answer.add(StringHelper.removeLeadingAndEndingQuotes(option));
                }

                break;
            }
        }

        return answer;
    }

    private List<String> extractEndpointOptions(List<String> lines) {
        List<String> answer = new ArrayList<>();

        // extract the default options
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("\"endpointOptions\"")) {
                int start = line.indexOf('[');
                if (start == -1) {
                    throw new IllegalStateException("Malformed camel-connector.json");
                }

                int end = line.indexOf(']', start);
                if (end == -1) {
                    throw new IllegalStateException("Malformed camel-connector.json");
                }

                line = line.substring(start + 1, end).trim();
                for (String option : line.split(",")) {
                    answer.add(StringHelper.removeLeadingAndEndingQuotes(option));
                }

                break;
            }
        }

        return answer;
    }
}
