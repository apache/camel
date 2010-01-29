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
package org.apache.camel.component.freemarker;

import java.net.URL;
import java.util.Map;

import freemarker.cache.URLTemplateLoader;
import freemarker.template.Configuration;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.util.ObjectHelper;
import org.springframework.beans.factory.BeanClassLoaderAware;

/**
 * Freemarker component.
 */
public class FreemarkerComponent extends DefaultComponent implements BeanClassLoaderAware {

    private Configuration configuration;
    private Configuration noCacheConfiguration;

    private ClassLoader beanClassLoader; 

    public void setBeanClassLoader(ClassLoader classLoader) {
        this.beanClassLoader = classLoader;
    }

    public ClassLoader getBeanClassLoader() {
        return beanClassLoader;
    }

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        FreemarkerEndpoint endpoint = new FreemarkerEndpoint(uri, this, remaining);

        // should we use regular configuration or no cache (content cache is default true)
        Configuration config;
        String encoding = getAndRemoveParameter(parameters, "encoding", String.class);
        if (ObjectHelper.isNotEmpty(encoding)) {
            endpoint.setEncoding(encoding);
        }
        boolean cache = getAndRemoveParameter(parameters, "contentCache", Boolean.class, Boolean.TRUE);
        if (cache) {
            config = getConfiguraiton();
        } else {
            config = getNoCacheConfiguration();
        }

        endpoint.setConfiguration(config);
        return endpoint;
    }

    public synchronized Configuration getConfiguraiton() {
        if (configuration == null) {
            configuration = new Configuration();
            configuration.setTemplateLoader(new URLTemplateLoader() {
                @Override
                protected URL getURL(String name) {
                    ClassLoader[] loaders = {
                        beanClassLoader, 
                        Thread.currentThread().getContextClassLoader(), 
                        this.getClass().getClassLoader()
                    };
                    for (ClassLoader classLoader : loaders) {
                        if (classLoader != null) {
                            URL resource = classLoader.getResource(name);
                            if (resource != null) {
                                return resource;
                            }
                        }
                    }
                    return null;
                }
            });
        }
        return (Configuration) configuration.clone();
    }

    public void setConfiguraiton(Configuration configuration) {
        this.configuration = configuration;
    }

    private synchronized Configuration getNoCacheConfiguration() {
        if (noCacheConfiguration == null) {
            // create a clone of the regular configuration
            noCacheConfiguration = (Configuration) getConfiguraiton().clone();
            // set this one to not use cache
            noCacheConfiguration.setCacheStorage(new NoCacheStorage());
        }
        return noCacheConfiguration;
    }

}
