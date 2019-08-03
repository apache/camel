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
package org.apache.camel.component.freemarker;

import java.net.URL;
import java.util.Map;

import freemarker.cache.NullCacheStorage;
import freemarker.cache.URLTemplateLoader;
import freemarker.template.Configuration;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * Freemarker component.
 */
@Component("freemarker")
public class FreemarkerComponent extends DefaultComponent {

    @Metadata(label = "advanced")
    private Configuration configuration;
    private Configuration noCacheConfiguration;

    public FreemarkerComponent() {
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        // should we use regular configuration or no cache (content cache is default true)
        Configuration config;
        String encoding = getAndRemoveParameter(parameters, "encoding", String.class);
        boolean cache = getAndRemoveParameter(parameters, "contentCache", Boolean.class, Boolean.TRUE);
        int templateUpdateDelay = getAndRemoveParameter(parameters, "templateUpdateDelay", Integer.class, 0);
        if (cache) {
            config = getConfiguration();
            if (templateUpdateDelay > 0) {
                config.setTemplateUpdateDelay(templateUpdateDelay);
            }
        } else {
            config = getNoCacheConfiguration();
        }

        FreemarkerEndpoint endpoint = new FreemarkerEndpoint(uri, this, remaining);
        if (ObjectHelper.isNotEmpty(encoding)) {
            endpoint.setEncoding(encoding);
        }
        endpoint.setContentCache(cache);
        endpoint.setConfiguration(config);
        endpoint.setTemplateUpdateDelay(templateUpdateDelay);

        // if its a http resource then append any remaining parameters and update the resource uri
        if (ResourceHelper.isHttpUri(remaining)) {
            remaining = ResourceHelper.appendParameters(remaining, parameters);
            endpoint.setResourceUri(remaining);
        }

        return endpoint;
    }

    public synchronized Configuration getConfiguration() {
        if (configuration == null) {
            configuration = new Configuration();
            configuration.setTemplateLoader(new URLTemplateLoader() {
                @Override
                protected URL getURL(String name) {
                    try {
                        return ResourceHelper.resolveMandatoryResourceAsUrl(getCamelContext().getClassResolver(), name);
                    } catch (Exception e) {
                        // freemarker prefers to ask for locale first (eg xxx_en_GB, xxX_en), and then fallback without locale
                        // so we should return null to signal the resource could not be found
                        return null;
                    }
                }
            });
        }
        return (Configuration) configuration.clone();
    }

    /**
     * To use an existing {@link freemarker.template.Configuration} instance as the configuration.
     */
    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }

    private synchronized Configuration getNoCacheConfiguration() {
        if (noCacheConfiguration == null) {
            // create a clone of the regular configuration
            noCacheConfiguration = (Configuration) getConfiguration().clone();
            // set this one to not use cache
            noCacheConfiguration.setCacheStorage(new NullCacheStorage());
        }
        return noCacheConfiguration;
    }

}
