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
package org.apache.camel.component.servletlistener;

import org.apache.camel.impl.SimpleRegistry;
import org.apache.camel.util.jndi.JndiContext;

// START SNIPPET: e1
/**
 * Our custom {@link CamelContextLifecycle} which allows us to enlist beans in the {@link JndiContext}
 * so the Camel application can lookup the beans in the {@link org.apache.camel.spi.Registry}.
 * <p/>
 * We can of course also do other kind of custom logic as well.
 */
public class MyLifecycle implements CamelContextLifecycle<SimpleRegistry> {

    @Override
    public void beforeStart(ServletCamelContext camelContext, SimpleRegistry registry) throws Exception {
        // enlist our bean(s) in the registry
        registry.put("myBean", new HelloBean());
    }

    @Override
    public void afterStart(ServletCamelContext camelContext, SimpleRegistry registry) throws Exception {
        // noop
    }

    @Override
    public void beforeStop(ServletCamelContext camelContext, SimpleRegistry registry) throws Exception {
        // noop
    }

    @Override
    public void afterStop(ServletCamelContext camelContext, SimpleRegistry registry) throws Exception {
        // unbind our bean when Camel has been stopped
        registry.remove("myBean");
    }

    @Override
    public void beforeAddRoutes(ServletCamelContext camelContext, SimpleRegistry registry) throws Exception {
        // noop
    }

    @Override
    public void afterAddRoutes(ServletCamelContext camelContext, SimpleRegistry registry) throws Exception {
        // noop
    }
}
// END SNIPPET: e1
