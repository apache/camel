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

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.spi.CamelContextCustomizer;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.PropertiesSource;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.OrderedProperties;
import org.apache.camel.util.StringHelper;

/**
 * This trait is a {@link PropertiesSource} so Camel properties component will use this directly when looking for
 * property values.
 */
@JdkService("properties-source-factory")
public class PropertyTrait implements Trait, PropertiesSource, CamelContextAware {

    private final Properties properties = new OrderedProperties();
    private CamelContext camelContext;
    private PropertiesComponent pc;

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
    public CamelContextCustomizer parseTrait(String trait) {
        if (trait.contains("=")) {
            String key = StringHelper.before(trait, "=").trim();
            String value = StringHelper.after(trait, "=").trim();
            setProperty(key, value);
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
                        setProperty(key, value);
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
        }
        return null;
    }

    protected void setProperty(String key, String value) {
        properties.setProperty(key, value);
        if (!camelContext.isStarted()) {
            // if we are bootstrapping then also set as initial property, so it can be used there as well
            camelContext.getPropertiesComponent().addInitialProperty(key, value);
        }
    }

    @Override
    public String toString() {
        return "camel-dsl-modeline";
    }
}
