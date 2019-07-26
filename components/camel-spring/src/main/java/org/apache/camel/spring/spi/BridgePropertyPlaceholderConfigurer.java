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
import org.apache.camel.component.properties.PropertiesLookup;
import org.apache.camel.component.properties.PropertiesParser;
import org.apache.camel.spi.PropertiesSource;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.ConfigurablePropertyResolver;

/**
 * A {@link PropertySourcesPlaceholderConfigurer} that bridges Camel's <a href="http://camel.apache.org/using-propertyplaceholder.html">
 * property placeholder</a> with the Spring property placeholder mechanism.
 */
public class BridgePropertyPlaceholderConfigurer extends PropertySourcesPlaceholderConfigurer implements PropertiesParser, PropertiesSource {    

    // NOTE: this class must be in the spi package as if its in the root package, then Spring fails to parse the XML
    // files due some weird spring issue. But that is okay as having this class in the spi package is fine anyway.

    private ConfigurablePropertyResolver resolver;
    private PropertiesParser parser;

    @Override
    protected void processProperties(ConfigurableListableBeanFactory beanFactoryToProcess, ConfigurablePropertyResolver propertyResolver) throws BeansException {
        super.processProperties(beanFactoryToProcess, propertyResolver);
        resolver = propertyResolver;
    }

    @Override
    protected void processProperties(ConfigurableListableBeanFactory beanFactoryToProcess, Properties props) throws BeansException {
        //Throws UnsupportedOperationException
        super.processProperties(beanFactoryToProcess, props);
    }

    @Override
    protected Object convertProperty(String propertyName, Object propertyValue) {
        if (parser != null && propertyValue instanceof String) {
            //Apply the parser to the placeholder value, to allow Camel placeholder processing to work outside the <CamelContext>, e.g. for decryption.
            return parser.parseProperty(propertyName, (String)propertyValue);
        }
        return propertyValue;
    }

    @Override
    public String parseUri(String text, PropertiesLookup properties, boolean fallback) throws IllegalArgumentException {
        // first let Camel parse the text as it may contain Camel placeholders
        String answer = parser.parseUri(text, properties, fallback);

        // then let Spring parse it to resolve any Spring placeholders
        return resolver.resolvePlaceholders(answer);
    }

    @Override
    public String parseProperty(String key, String value) {
        String answer = parser.parseProperty(key, value);
        if (answer != null) {
            return resolver.resolvePlaceholders(answer);
        }
        return resolver.resolvePlaceholders(value);
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
        return resolver.getProperty(name);
    }

    private final class BridgePropertiesParser implements PropertiesParser {

        private final PropertiesParser delegate;
        private final PropertiesParser parser;

        private BridgePropertiesParser(PropertiesParser delegate, PropertiesParser parser) {
            this.delegate = delegate;
            this.parser = parser;
        }

        @Override
        public String parseUri(String text, PropertiesLookup properties, boolean fallback) throws IllegalArgumentException {
            String answer = null;
            if (delegate != null) {
                answer = delegate.parseUri(text, properties, fallback);
            }
            if (answer != null) {
                text = answer;
            }
            return parser.parseUri(text, properties, fallback);
        }

        @Override
        public String parseProperty(String key, String value) {
            String answer = null;
            if (delegate != null) {
                answer = delegate.parseProperty(key, value);
            }
            if (answer != null) {
                value = answer;
            }
            return parser.parseProperty(key, value);
        }
    }
}
