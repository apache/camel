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

import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import org.apache.camel.component.properties.PropertiesLookup;
import org.apache.camel.component.properties.PropertiesParser;
import org.apache.camel.spi.PropertiesSource;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.ConfigurablePropertyResolver;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.PropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * A {@link PropertySourcesPlaceholderConfigurer} that bridges Camel's <a href="http://camel.apache.org/using-propertyplaceholder.html">
 * property placeholder</a> with the Spring property placeholder mechanism.
 */
public class BridgePropertyPlaceholderConfigurer extends PropertySourcesPlaceholderConfigurer implements PropertiesParser, PropertiesSource {    

    // NOTE: this class must be in the spi package as if its in the root package, then Spring fails to parse the XML
    // files due some weird spring issue. But that is okay as having this class in the spi package is fine anyway.

    private ConfigurablePropertyResolver resolver;
    private PropertiesParser parser;

    private MutablePropertySources propertySources;
    private PropertySources appliedPropertySources;
    private Environment environment;

    @Override
    protected void processProperties(ConfigurableListableBeanFactory beanFactoryToProcess, ConfigurablePropertyResolver propertyResolver) throws BeansException {
        super.processProperties(beanFactoryToProcess, propertyResolver);
        resolver = propertyResolver;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        if (this.propertySources == null) {
            this.propertySources = new MutablePropertySources();
            if (this.environment != null) {
                this.propertySources.addLast(
                    new PropertySource<Environment>(ENVIRONMENT_PROPERTIES_PROPERTY_SOURCE_NAME, this.environment) {
                    @Override
                    @Nullable
                    public String getProperty(String key) {
                        return this.source.getProperty(key);
                    }
                }
                );
            }
            try {
                PropertySource<?> localPropertySource =
                    new PropertiesPropertySource(LOCAL_PROPERTIES_PROPERTY_SOURCE_NAME, mergeProperties());
                if (this.localOverride) {
                    this.propertySources.addFirst(localPropertySource);
                } else {
                    this.propertySources.addLast(localPropertySource);
                }
            } catch (IOException ex) {
                throw new BeanInitializationException("Could not load properties", ex);
            }
        }

        List<String> sourceNames = this.propertySources.stream()
            .map(source -> source.getName())
            .collect(Collectors.toList());
        for (String sourceName : sourceNames) {
            this.propertySources.replace(sourceName, new ConvertingPropertySourceDecorator<>(this.propertySources.get(sourceName)));
        }
        processProperties(beanFactory, new PropertySourcesPropertyResolver(this.propertySources));
        this.appliedPropertySources = this.propertySources;
    }

    @Override
    public void setPropertySources(PropertySources propertySources) {
        this.propertySources = new MutablePropertySources(propertySources);
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    public PropertySources getAppliedPropertySources() throws IllegalStateException {
        Assert.state(this.appliedPropertySources != null, "PropertySources have not yet been applied");
        return this.appliedPropertySources;
    }

    @Override
    protected void processProperties(ConfigurableListableBeanFactory beanFactoryToProcess, Properties props) throws BeansException {
        //Throws UnsupportedOperationException
        super.processProperties(beanFactoryToProcess, props);
    }

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

    private class ConvertingPropertySourceDecorator<T> extends PropertySource<T> {

        private final PropertySource<T> delegate;

        public ConvertingPropertySourceDecorator(PropertySource<T> delegate) {
            super(delegate.getName(), delegate.getSource());
            this.delegate = delegate;
        }

        @Override
        public Object getProperty(String name) {
            Object value = this.delegate.getProperty(name);
            if (value != null) {
                return convertProperty(name, value);
            }
            return value;
        }
    }
}
