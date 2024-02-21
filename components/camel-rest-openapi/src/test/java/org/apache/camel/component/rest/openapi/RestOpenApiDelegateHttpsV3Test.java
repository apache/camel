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
package org.apache.camel.component.rest.openapi;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.PropertyBindingSupport;

public class RestOpenApiDelegateHttpsV3Test extends HttpsV3Test {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        final CamelContext camelContext = super.createCamelContext();

        // since camel context is not started, then we need to manually initialize the delegate
        final Component delegate = PluginHelper.getComponentResolver(camelContext)
                .resolveComponent(componentName, camelContext);
        delegate.setCamelContext(camelContext);
        delegate.init();

        // and configure the ssl context parameters via binding
        new PropertyBindingSupport.Builder()
                .withCamelContext(camelContext)
                .withProperty("sslContextParameters", createHttpsParameters(camelContext))
                .withTarget(delegate)
                .withConfigurer(delegate.getComponentPropertyConfigurer())
                .bind();
        camelContext.addComponent(componentName, delegate);

        return camelContext;
    }

}
