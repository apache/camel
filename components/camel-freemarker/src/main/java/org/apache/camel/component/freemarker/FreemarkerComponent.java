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

import java.util.Map;

import freemarker.cache.ClassTemplateLoader;
import freemarker.template.Configuration;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;
import org.springframework.core.io.Resource;

/**
 * Freemarker component.
 */
public class FreemarkerComponent extends DefaultComponent {

    private Configuration configuraiton;
    private Configuration noCacheConfiguration;

    protected Endpoint createEndpoint(String uri, String remaining, Map parameters) throws Exception {
        FreemarkerEndpoint endpoint = new FreemarkerEndpoint(uri, this, remaining, parameters);

        // should we use regular configuration or no cache (content cache is default true)
        Configuration config;
        boolean cache = (Boolean) getAndRemoveParameter(parameters, "contentCache", Boolean.class, Boolean.TRUE);
        if (cache) {
            config = getConfiguraiton();
        } else {
            config = getNoCacheConfiguration();
        }

        endpoint.setConfiguration(config);
        return endpoint;
    }

    public synchronized Configuration getConfiguraiton() {
        if (configuraiton == null) {
            configuraiton = new Configuration();
            // use class template loader using Spring Resource class and / as root in classpath
            configuraiton.setTemplateLoader(new ClassTemplateLoader(Resource.class, "/"));
        }
        return (Configuration) configuraiton.clone();
    }

    public void setConfiguraiton(Configuration configuraiton) {
        this.configuraiton = configuraiton;
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
