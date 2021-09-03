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
package org.apache.camel.main;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.PropertyBindingException;
import org.apache.camel.spi.ExtendedPropertyConfigurerGetter;
import org.apache.camel.spi.PropertyConfigurer;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.OrderedProperties;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MainHelper {
    private static final Logger LOG = LoggerFactory.getLogger(MainHelper.class);

    private final Set<String> componentEnvNames = new HashSet<>();
    private final Set<String> dataformatEnvNames = new HashSet<>();
    private final Set<String> languageEnvNames = new HashSet<>();

    public MainHelper() {
        try {
            InputStream is = MainHelper.class.getResourceAsStream("/org/apache/camel/main/components.properties");
            loadLines(is, componentEnvNames, s -> "CAMEL_COMPONENT_" + s.toUpperCase(Locale.US).replace('-', '_'));
            IOHelper.close(is);

            is = MainHelper.class.getResourceAsStream("/org/apache/camel/main/dataformats.properties");
            loadLines(is, dataformatEnvNames, s -> "CAMEL_DATAFORMAT_" + s.toUpperCase(Locale.US).replace('-', '_'));
            IOHelper.close(is);

            is = MainHelper.class.getResourceAsStream("/org/apache/camel/main/languages.properties");
            loadLines(is, languageEnvNames, s -> "CAMEL_LANGUAGE_" + s.toUpperCase(Locale.US).replace('-', '_'));
            IOHelper.close(is);
        } catch (Exception e) {
            throw new RuntimeException("Error loading catalog information from classpath", e);
        }
    }

    public void bootstrapDone() {
        // after bootstrap then these maps are no longer needed
        componentEnvNames.clear();
        dataformatEnvNames.clear();
        languageEnvNames.clear();
    }

    public static String toEnvVar(String name) {
        return name.toUpperCase(Locale.US).replaceAll("[^\\w]", "-").replace('-', '_');
    }

    public static Optional<String> lookupPropertyFromSysOrEnv(String name) {
        String answer = System.getProperty(name);
        if (answer == null) {
            answer = System.getenv(toEnvVar(name));
        }

        return Optional.ofNullable(answer);
    }

    public static Properties loadEnvironmentVariablesAsProperties(String[] prefixes) {
        Properties answer = new OrderedProperties();
        if (prefixes == null || prefixes.length == 0) {
            return answer;
        }

        for (String prefix : prefixes) {
            final String pk = prefix.toUpperCase(Locale.US).replaceAll("[^\\w]", "-");
            final String pk2 = pk.replace('-', '_');
            System.getenv().forEach((k, v) -> {
                k = k.toUpperCase(Locale.US);
                if (k.startsWith(pk) || k.startsWith(pk2)) {
                    String key = k.toLowerCase(Locale.US).replace('_', '.');
                    answer.put(key, v);
                }
            });
        }

        return answer;
    }

    public static Map<String, String> filterEnvVariables(String[] prefixes) {
        Map<String, String> answer = new HashMap<>();
        System.getenv().forEach((k, v) -> {
            final String uk = k.toUpperCase(Locale.US);
            for (String prefix : prefixes) {
                if (uk.startsWith(prefix)) {
                    answer.put(uk, v);
                }
            }
        });
        return answer;
    }

    public void addComponentEnvVariables(Map<String, String> env, Properties properties, boolean custom) {
        Set<String> toRemove = new HashSet<>();
        env.forEach((k, v) -> {
            if (custom) {
                toRemove.add(k);
                String ck = "camel.component." + k.substring(16).toLowerCase(Locale.US).replace('_', '-');
                ck = ck.replaceFirst("-", ".");
                properties.put(ck, v);
            } else {
                Optional<String> e
                        = componentEnvNames.stream().filter(k::startsWith).findFirst();
                if (e.isPresent()) {
                    toRemove.add(k);
                    String cname = "camel.component." + e.get().substring(16).toLowerCase(Locale.US).replace('_', '-');
                    String option = k.substring(cname.length() + 1).toLowerCase(Locale.US).replace('_', '-');
                    properties.put(cname + "." + option, v);
                }
            }
        });
        toRemove.forEach(env::remove);
    }

    public void addDataFormatEnvVariables(Map<String, String> env, Properties properties, boolean custom) {
        Set<String> toRemove = new HashSet<>();
        env.forEach((k, v) -> {
            if (custom) {
                toRemove.add(k);
                String ck = "camel.dataformat." + k.substring(17).toLowerCase(Locale.US).replace('_', '-');
                ck = ck.replaceFirst("-", ".");
                properties.put(ck, v);
            } else {
                Optional<String> e
                        = dataformatEnvNames.stream().filter(k::startsWith).findFirst();
                if (e.isPresent()) {
                    toRemove.add(k);
                    String cname = "camel.dataformat." + e.get().substring(17).toLowerCase(Locale.US).replace('_', '-');
                    String option = k.substring(cname.length() + 1).toLowerCase(Locale.US).replace('_', '-');
                    properties.put(cname + "." + option, v);
                }
            }
        });
        toRemove.forEach(env::remove);
    }

    public void addLanguageEnvVariables(Map<String, String> env, Properties properties, boolean custom) {
        Set<String> toRemove = new HashSet<>();
        env.forEach((k, v) -> {
            if (custom) {
                toRemove.add(k);
                String ck = "camel.language." + k.substring(15).toLowerCase(Locale.US).replace('_', '-');
                ck = ck.replaceFirst("-", ".");
                properties.put(ck, v);
            } else {
                Optional<String> e
                        = languageEnvNames.stream().filter(k::startsWith).findFirst();
                if (e.isPresent()) {
                    toRemove.add(k);
                    String cname = "camel.language." + e.get().substring(15).toLowerCase(Locale.US).replace('_', '-');
                    String option = k.substring(cname.length() + 1).toLowerCase(Locale.US).replace('_', '-');
                    properties.put(cname + "." + option, v);
                }
            }
        });
        toRemove.forEach(env::remove);
    }

    public static Properties loadJvmSystemPropertiesAsProperties(String[] prefixes) {
        Properties answer = new OrderedProperties();
        if (prefixes == null || prefixes.length == 0) {
            return answer;
        }

        for (String prefix : prefixes) {
            final String pk = prefix.toUpperCase(Locale.US).replaceAll("[^\\w]", "-");
            final String pk2 = pk.replace('-', '.');
            System.getProperties().forEach((k, v) -> {
                String key = k.toString().toUpperCase(Locale.US);
                if (key.startsWith(pk) || key.startsWith(pk2)) {
                    answer.put(k.toString(), v);
                }
            });
        }

        return answer;
    }

    public static String optionKey(String key) {
        // as we ignore case for property names we should use keys in same case and without dashes
        key = StringHelper.dashToCamelCase(key);
        return key;
    }

    public static boolean setPropertiesOnTarget(CamelContext context, Object target, Object source) throws Exception {
        ObjectHelper.notNull(context, "context");
        ObjectHelper.notNull(target, "target");

        boolean rc = false;

        PropertyConfigurer targetConfigurer = null;
        if (target instanceof Component) {
            // the component needs to be initialized to have the configurer ready
            ServiceHelper.initService(target);
            targetConfigurer = ((Component) target).getComponentPropertyConfigurer();
        }
        if (targetConfigurer == null) {
            String name = target.getClass().getName();
            // see if there is a configurer for it
            targetConfigurer = context.adapt(ExtendedCamelContext.class)
                    .getConfigurerResolver().resolvePropertyConfigurer(name, context);
        }

        PropertyConfigurer sourceConfigurer = null;
        if (source instanceof Component) {
            // the component needs to be initialized to have the configurer ready
            ServiceHelper.initService(source);
            sourceConfigurer = ((Component) source).getComponentPropertyConfigurer();
        }
        if (sourceConfigurer == null) {
            String name = source.getClass().getName();
            // see if there is a configurer for it
            sourceConfigurer = context.adapt(ExtendedCamelContext.class)
                    .getConfigurerResolver().resolvePropertyConfigurer(name, context);
        }

        if (targetConfigurer != null && sourceConfigurer instanceof ExtendedPropertyConfigurerGetter) {
            ExtendedPropertyConfigurerGetter getter = (ExtendedPropertyConfigurerGetter) sourceConfigurer;
            for (String key : getter.getAllOptions(source).keySet()) {
                Object value = getter.getOptionValue(source, key, true);
                if (value != null) {
                    rc |= targetConfigurer.configure(context, target, key, value, true);
                }
            }
        }
        return rc;
    }

    public static boolean setPropertiesOnTarget(
            CamelContext context, Object target, Map<String, Object> properties,
            String optionPrefix, boolean failIfNotSet, boolean ignoreCase,
            Map<String, String> autoConfiguredProperties) {

        ObjectHelper.notNull(context, "context");
        ObjectHelper.notNull(target, "target");
        ObjectHelper.notNull(properties, "properties");

        boolean rc = false;
        PropertyConfigurer configurer = null;
        if (target instanceof Component) {
            // the component needs to be initialized to have the configurer ready
            ServiceHelper.initService(target);
            configurer = ((Component) target).getComponentPropertyConfigurer();
        }

        if (configurer == null) {
            String name = target.getClass().getName();
            // see if there is a configurer for it (use bootstrap)
            configurer = context.adapt(ExtendedCamelContext.class)
                    .getBootstrapConfigurerResolver().resolvePropertyConfigurer(name, context);
        }

        try {
            // keep a reference of the original keys
            Map<String, Object> backup = new LinkedHashMap<>(properties);

            rc = PropertyBindingSupport.build()
                    .withMandatory(failIfNotSet)
                    .withRemoveParameters(true)
                    .withConfigurer(configurer)
                    .withIgnoreCase(ignoreCase)
                    .bind(context, target, properties);

            for (Map.Entry<String, Object> entry : backup.entrySet()) {
                if (entry.getValue() != null && !properties.containsKey(entry.getKey())) {
                    String prefix = optionPrefix;
                    if (prefix != null && !prefix.endsWith(".")) {
                        prefix = "." + prefix;
                    }

                    LOG.debug("Configured property: {}{}={} on bean: {}", prefix, entry.getKey(), entry.getValue(), target);
                    autoConfiguredProperties.put(prefix + entry.getKey(), entry.getValue().toString());
                }
            }
        } catch (PropertyBindingException e) {
            String key = e.getOptionKey();
            if (key == null) {
                String prefix = e.getOptionPrefix();
                if (prefix != null && !prefix.endsWith(".")) {
                    prefix = "." + prefix;
                }

                key = prefix != null
                        ? prefix + "." + e.getPropertyName()
                        : e.getPropertyName();
            }

            if (failIfNotSet) {
                // enrich the error with more precise details with option prefix and key
                throw new PropertyBindingException(
                        e.getTarget(), e.getPropertyName(), e.getValue(), optionPrefix, key, e.getCause());
            } else {
                LOG.debug("Error configuring property (" + key + ") with name: " + e.getPropertyName() + ") on bean: " + target
                          + " with value: " + e.getValue() + ". This exception is ignored as failIfNotSet=false.",
                        e);
            }
        }

        return rc;
    }

    public static void computeProperties(
            String keyPrefix, String key, Properties prop, Map<PropertyOptionKey, Map<String, Object>> properties,
            Function<String, Iterable<Object>> supplier) {
        if (key.startsWith(keyPrefix)) {
            // grab name
            final int dot = key.indexOf('.', keyPrefix.length());
            final String name = dot == -1 ? key.substring(keyPrefix.length()) : key.substring(keyPrefix.length(), dot);

            // enabled is a virtual property
            if ("enabled".equals(name)) {
                return;
            }
            // skip properties as its already keyPrefix earlier
            if ("properties".equals(name)) {
                return;
            }

            // determine if the service is enabled or not by taking into account two options:
            //
            //   1. ${keyPrefix}.enabled = true|false
            //   2. ${keyPrefix}.${name}.enabled = true|false
            //
            // The option [2] has the higher priority so as example:
            //
            //   camel.component.enabled = false
            //   camel.component.seda.enabled = true
            //
            // enables auto configuration of the seda component only
            if (!isServiceEnabled(keyPrefix, name, prop)) {
                return;
            }

            String prefix = dot == -1 ? "" : key.substring(0, dot + 1);
            String option = dot == -1 ? "" : key.substring(dot + 1);
            String value = prop.getProperty(key, "");

            // enabled is a virtual property
            if ("enabled".equalsIgnoreCase(option)) {
                return;
            }

            validateOptionAndValue(key, option, value);

            Iterable<Object> targets = supplier.apply(name);
            for (Object target : targets) {
                PropertyOptionKey pok = new PropertyOptionKey(target, prefix);
                Map<String, Object> values = properties.computeIfAbsent(pok, k -> new LinkedHashMap<>());

                // we ignore case for property keys (so we should store them in canonical style
                values.put(optionKey(option), value);
            }
        }
    }

    public static boolean isServiceEnabled(String prefix, String name, Properties properties) {
        ObjectHelper.notNull(prefix, "prefix");
        ObjectHelper.notNull(name, "name");
        ObjectHelper.notNull(properties, "properties");

        if (!prefix.endsWith(".")) {
            prefix = prefix + ".";
        }

        final String group = properties.getProperty(prefix + "enabled", "true");
        final String item = properties.getProperty(prefix + name + ".enabled", group);

        return Boolean.parseBoolean(item);
    }

    public static void validateOptionAndValue(String key, String option, String value) {
        if (ObjectHelper.isEmpty(option)) {
            throw new IllegalArgumentException("Error configuring property: " + key + " because option is empty");
        }
        if (ObjectHelper.isEmpty(value)) {
            throw new IllegalArgumentException("Error configuring property: " + key + " because value is empty");
        }
    }

    /**
     * Loads the entire stream into memory as a String and returns it.
     * <p/>
     * <b>Notice:</b> This implementation appends a <tt>\n</tt> as line terminator at the of the text.
     * <p/>
     * Warning, don't use for crazy big streams :)
     */
    private static void loadLines(InputStream in, Set<String> lines, Function<String, String> func) throws IOException {
        try (final InputStreamReader isr = new InputStreamReader(in);
             final BufferedReader reader = new LineNumberReader(isr)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(func.apply(line));
            }
        }
    }

}
