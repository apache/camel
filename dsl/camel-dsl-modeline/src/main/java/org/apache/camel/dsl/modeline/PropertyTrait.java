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
package org.apache.camel.dsl.modeline;

import java.io.InputStream;
import java.util.Properties;
import java.util.function.Predicate;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Ordered;
import org.apache.camel.spi.CamelContextCustomizer;
import org.apache.camel.spi.LoadablePropertiesSource;
import org.apache.camel.spi.PropertiesSource;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.OrderedLocationProperties;
import org.apache.camel.util.StringHelper;

/**
 * This trait is a {@link PropertiesSource} so Camel properties component will use this directly when looking for
 * property values.
 */
@JdkService("properties-source-factory")
public class PropertyTrait implements Trait, LoadablePropertiesSource, CamelContextAware, Ordered {

    private final OrderedLocationProperties properties = new OrderedLocationProperties();
    private CamelContext camelContext;

    @Override
    public int getOrder() {
        return 900;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public String getName() {
        return "property";
    }

    @Override
    public String getProperty(String name) {
        return properties.getProperty(name);
    }

    @Override
    public CamelContextCustomizer parseTrait(Resource resource, String trait) {
        if (trait.contains("=")) {
            String key = StringHelper.before(trait, "=").trim();
            String value = StringHelper.after(trait, "=").trim();
            setProperty(resource, key, value);
        } else {
            if (ResourceHelper.hasScheme(trait)) {
                // it is a properties file so load resource
                try (InputStream is = ResourceHelper.resolveResourceAsInputStream(camelContext, trait)) {
                    Properties prop = new Properties();
                    prop.load(is);
                    for (String k : prop.stringPropertyNames()) {
                        String v = prop.getProperty(k);
                        String key = k.trim();
                        String value = v.trim();
                        setProperty(resource, key, value);
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
        }
        return null;
    }

    protected void setProperty(Resource resource, String key, String value) {
        String loc = resource.getLocation();
        properties.put(loc, key, value);
    }

    @Override
    public Properties loadProperties() {
        return properties;
    }

    @Override
    public Properties loadProperties(Predicate<String> filter) {
        OrderedLocationProperties answer = new OrderedLocationProperties();

        for (String name : properties.stringPropertyNames()) {
            if (filter.test(name)) {
                answer.put(properties.getLocation(name), name, properties.get(name));
            }
        }

        return answer;
    }

    @Override
    public void reloadProperties(String location) {
        // noop
    }

    @Override
    public String toString() {
        return "camel-dsl-modeline";
    }

}
