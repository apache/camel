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
package org.apache.camel.main.download;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.engine.DefaultResourceResolvers;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.ResourceResolver;
import org.apache.camel.support.ResourceResolverSupport;
import org.apache.camel.support.service.ServiceHelper;

/**
 * A fallback {@link ResourceResolver} that camel-jbang uses to be able to load resources from both classpath and file,
 * which is needed when users use camel run * and have files that should be loaded via Camels resource loader.
 */
public class KameletFallbackResourceResolver extends ResourceResolverSupport {

    private final ResourceResolver classpath;
    private final ResourceResolver file;

    public KameletFallbackResourceResolver(CamelContext camelContext) {
        super("");
        setCamelContext(camelContext);
        classpath = new DefaultResourceResolvers.ClasspathResolver();
        classpath.setCamelContext(camelContext);
        file = new DefaultResourceResolvers.FileResolver();
        file.setCamelContext(camelContext);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        ServiceHelper.startService(classpath, file);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        ServiceHelper.startService(classpath, file);
    }

    @Override
    public Resource resolve(String location) {
        if (location.contains("{{") && location.contains("}}")) {
            location = getCamelContext().getPropertiesComponent().parseUri(location);
        }
        return createResource(location, null);
    }

    @Override
    protected Resource createResource(String location, String remaining) {
        Resource answer = classpath.resolve(classpath.getSupportedScheme() + ":" + location);
        if (answer == null || !answer.exists()) {
            answer = file.resolve(file.getSupportedScheme() + ":" + location);
            if (answer != null && !answer.exists()) {
                answer = null;
            }
        }
        return answer;
    }
}
