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
package org.apache.camel.example.servletlistener;

import org.apache.camel.component.servletlistener.CamelContextLifecycle;
import org.apache.camel.component.servletlistener.ServletCamelContext;
import org.apache.camel.impl.JndiRegistry;

public class MyLifecycle implements CamelContextLifecycle<JndiRegistry> {

    @Override
    public void beforeStart(ServletCamelContext camelContext, JndiRegistry registry) throws Exception {
        // enlist our bean(s) in the registry
        registry.bind("myBean", new HelloBean());
    }
    
    @Override
    public void beforeStop(ServletCamelContext camelContext, JndiRegistry registry) throws Exception {
        // noop
    }

    @Override
    public void afterStop(ServletCamelContext camelContext, JndiRegistry registry) throws Exception {
        // noop
    }

    @Override
    public void beforeAddRoutes(ServletCamelContext camelContext, JndiRegistry registry) throws Exception {
        // noop
    }

    @Override
    public void afterAddRoutes(ServletCamelContext camelContext, JndiRegistry registry) throws Exception {
        // noop
    }

    @Override
    public void afterStart(ServletCamelContext camelContext, JndiRegistry registry) throws Exception {
        // noop
    }
}
