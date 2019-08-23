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
package org.apache.camel.component.cxf;

import org.apache.cxf.endpoint.Client;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.frontend.AbstractWSDLBasedEndpointFactory;

public final class ChainedCxfConfigurer implements CxfConfigurer {
    private CxfConfigurer parent;
    private CxfConfigurer child;

    private ChainedCxfConfigurer() {
    }

    public static ChainedCxfConfigurer create(CxfConfigurer parent, CxfConfigurer child) {
        ChainedCxfConfigurer result = new ChainedCxfConfigurer();
        result.parent = parent;
        result.child = child;
        return result;
    }

    public ChainedCxfConfigurer addChild(CxfConfigurer cxfConfigurer) {
        ChainedCxfConfigurer result = new ChainedCxfConfigurer();
        result.parent = this;
        result.child = cxfConfigurer;
        return result;
    }

    @Override
    public void configure(AbstractWSDLBasedEndpointFactory factoryBean) {
        parent.configure(factoryBean);
        child.configure(factoryBean);
    }

    @Override
    public void configureClient(Client client) {
        parent.configureClient(client);
        child.configureClient(client);
    }

    @Override
    public void configureServer(Server server) {
        parent.configureServer(server);
        child.configureServer(server);
    }

    public static class NullCxfConfigurer implements CxfConfigurer {

        @Override
        public void configure(AbstractWSDLBasedEndpointFactory factoryBean) {
        }

        @Override
        public void configureClient(Client client) {
        }

        @Override
        public void configureServer(Server server) {
        }
    }
}
