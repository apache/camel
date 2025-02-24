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
package org.apache.camel.spring.spi;

import java.util.Properties;
import java.util.function.Predicate;

import org.apache.camel.component.properties.PropertiesLookup;
import org.apache.camel.component.properties.PropertiesParser;
import org.apache.camel.spi.LoadablePropertiesSource;
import org.apache.camel.spi.PropertiesSource;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;
import org.springframework.core.Constants;
import org.springframework.util.PropertyPlaceholderHelper;

/**
 * A {@link PropertyPlaceholderConfigurer} that bridges Camel's
 * <a href="http://camel.apache.org/using-propertyplaceholder.html"> property placeholder</a> with the Spring property
 * placeholder mechanism.
 */
public class BridgePropertyPlaceholderConfigurer extends PropertyPlaceholderConfigurer
        implements PropertiesParser, PropertiesSource, LoadablePropertiesSource {

    // NOTE: this class must be in the spi package as if its in the root package, then Spring fails to parse the XML
    // files due some weird spring issue. But that is okay as having this class in the spi package is fine anyway.

    private final Properties properties = new Properties();
    private PropertiesParser parser;
    private PropertyPlaceholderHelper helper;
    private int systemPropertiesMode = SYSTEM_PROPERTIES_MODE_FALLBACK;

    @Override
    protected void processProperties(ConfigurableListableBeanFactory beanFactoryToProcess, Properties props)
            throws BeansException {
        super.processProperties(beanFactoryToProcess, props);
        // store all the spring properties so we can refer to them later
        properties.putAll(props);
        // create helper
        helper = new PropertyPlaceholderHelper(
                placeholderPrefix, placeholderSuffix, valueSeparator, ignoreUnresolvablePlaceholders);
    }

    public int getSystemPropertiesMode() {
        return systemPropertiesMode;
    }

    @Override
    public void setSystemPropertiesModeName(String constantName) throws IllegalArgumentException {
        super.setSystemPropertiesModeName(constantName);
        Constants constants = new Constants(PropertyPlaceholderConfigurer.class);
        this.systemPropertiesMode = constants.asNumber(constantName).intValue();
    }

    @Override
    public void setSystemPropertiesMode(int systemPropertiesMode) {
        super.setSystemPropertiesMode(systemPropertiesMode);
        this.systemPropertiesMode = systemPropertiesMode;
    }

    @Override
    protected String resolvePlaceholder(String placeholder, Properties props) {
        String value = props.getProperty(placeholder);
        if (parser != null) {
            // Just apply the parser to the place holder value to avoid configuring the other placeholder configure twice for the inside and outside camel context
            return parser.parseProperty(placeholder, value, props::getProperty);
        } else {
            return value;
        }
    }

    @Override
    public String parseUri(
            String text, PropertiesLookup properties, boolean fallback, boolean keepUnresolvedOptional,
            boolean nestedPlaceholder)
            throws IllegalArgumentException {
        // first let Camel parse the text as it may contain Camel placeholders
        String answer = parser.parseUri(text, properties, fallback, keepUnresolvedOptional, nestedPlaceholder);

        // then let Spring parse it to resolve any Spring placeholders
        if (answer != null) {
            answer = springResolvePlaceholders(answer, properties);
        } else {
            answer = springResolvePlaceholders(text, properties);
        }
        return answer;
    }

    @Override
    public String parseProperty(String key, String value, PropertiesLookup properties) {
        String answer = parser.parseProperty(key, value, properties);
        if (answer != null) {
            answer = springResolvePlaceholders(answer, properties);
        } else {
            answer = springResolvePlaceholders(value, properties);
        }
        return answer;
    }

    /**
     * Resolves the placeholders using Spring's property placeholder functionality.
     *
     * @param  text       the text which may contain spring placeholders
     * @param  properties the properties
     * @return            the parsed text with replaced placeholders, or the original text as is
     */
    protected String springResolvePlaceholders(String text, PropertiesLookup properties) {
        return helper.replacePlaceholders(text, new BridgePropertyPlaceholderResolver(properties));
    }

    public void setParser(PropertiesParser parser) {
        if (this.parser != null) {
            // use a bridge if there is already a parser configured
            this.parser = new BridgePropertiesParser(this.parser, parser);
        } else {
            this.parser = parser;
        }
    }

    @Override
    public String getName() {
        return "BridgePropertyPlaceholderConfigurer";
    }

    @Override
    public String getProperty(String name) {
        return properties.getProperty(name);
    }

    @Override
    public Properties loadProperties() {
        return properties;
    }

    @Override
    public Properties loadProperties(Predicate<String> filter) {
        Properties props = new Properties();

        for (String name : properties.stringPropertyNames()) {
            if (filter.test(name)) {
                props.put(name, properties.get(name));
            }
        }

        return props;
    }

    @Override
    public void reloadProperties(String location) {
        // not possible with spring
    }

    private class BridgePropertyPlaceholderResolver implements PropertyPlaceholderHelper.PlaceholderResolver {

        private final PropertiesLookup properties;

        BridgePropertyPlaceholderResolver(PropertiesLookup properties) {
            this.properties = properties;
        }

        @Override
        public String resolvePlaceholder(String placeholderName) {
            String propVal = null;
            if (systemPropertiesMode == SYSTEM_PROPERTIES_MODE_OVERRIDE) {
                propVal = resolveSystemProperty(placeholderName);
            }
            if (propVal == null) {
                propVal = properties.lookup(placeholderName, null);
            }
            if (propVal == null && systemPropertiesMode == SYSTEM_PROPERTIES_MODE_FALLBACK) {
                propVal = resolveSystemProperty(placeholderName);
            }
            return propVal;
        }
    }

    private static final class BridgePropertiesParser implements PropertiesParser {

        private final PropertiesParser delegate;
        private final PropertiesParser parser;

        private BridgePropertiesParser(PropertiesParser delegate, PropertiesParser parser) {
            this.delegate = delegate;
            this.parser = parser;
        }

        @Override
        public String parseUri(
                String text, PropertiesLookup properties, boolean fallback, boolean keepUnresolvedOptional,
                boolean nestedPlaceholder)
                throws IllegalArgumentException {
            String answer = null;
            if (delegate != null) {
                answer = delegate.parseUri(text, properties, fallback, keepUnresolvedOptional, nestedPlaceholder);
            }
            if (answer != null) {
                text = answer;
            }
            return parser.parseUri(text, properties, fallback, keepUnresolvedOptional, nestedPlaceholder);
        }

        @Override
        public String parseProperty(String key, String value, PropertiesLookup properties) {
            String answer = null;
            if (delegate != null) {
                answer = delegate.parseProperty(key, value, properties);
            }
            if (answer != null) {
                value = answer;
            }
            return parser.parseProperty(key, value, properties);
        }
    }

}
