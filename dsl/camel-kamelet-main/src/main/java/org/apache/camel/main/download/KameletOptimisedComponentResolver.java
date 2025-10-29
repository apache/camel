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
import org.apache.camel.Component;
import org.apache.camel.component.kamelet.KameletComponent;
import org.apache.camel.impl.engine.DefaultOptimisedComponentResolver;
import org.apache.camel.language.simple.SimpleLanguage;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.RouteTemplateHelper;

/**
 * Dynamic EIPs that are using kamelets should preload kamelets so we can resolve dependencies and what components these
 * kamelets are using as well.
 */
public class KameletOptimisedComponentResolver extends DefaultOptimisedComponentResolver {

    private final CamelContext camelContext;

    public KameletOptimisedComponentResolver(CamelContext camelContext) {
        super(camelContext);
        this.camelContext = camelContext;
    }

    @Override
    public Component resolveComponent(String uri) {
        Component answer = super.resolveComponent(uri);
        String scheme = ExchangeHelper.resolveScheme(uri);
        // if a kamelet then we need to know the name of the kamelet spec in use
        if ("kamelet".equals(scheme)) {
            String name = ExchangeHelper.resolveContextPath(uri);
            // must be a static name (so we can load the template and resolve nested dependencies)
            if (!SimpleLanguage.hasSimpleFunction(name) && answer instanceof KameletComponent kc) {
                // need to resolve dependencies from kamelet also
                String loc = kc.getLocation();
                DependencyDownloaderKamelet listener = camelContext.hasService(DependencyDownloaderKamelet.class);
                try {
                    RouteTemplateHelper.loadRouteTemplateFromLocation(camelContext, listener, name, loc);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return answer;
    }
}
