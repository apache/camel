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
package org.apache.camel.component.cxf;

import org.apache.cxf.endpoint.Client;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.frontend.AbstractWSDLBasedEndpointFactory;

public final class ChainedCxfEndpointConfigurer implements CxfEndpointConfigurer {
    private CxfEndpointConfigurer parent;
    private CxfEndpointConfigurer child;

    private ChainedCxfEndpointConfigurer() {
    }

    public static ChainedCxfEndpointConfigurer create(CxfEndpointConfigurer parent, CxfEndpointConfigurer child) {
        ChainedCxfEndpointConfigurer result = new ChainedCxfEndpointConfigurer();
        result.parent = parent;
        result.child = child;
        return result;
    }

    public ChainedCxfEndpointConfigurer addChild(CxfEndpointConfigurer cxfEndpointConfigurer) {
        ChainedCxfEndpointConfigurer result = new ChainedCxfEndpointConfigurer();
        result.parent = this;
        result.child = cxfEndpointConfigurer;
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

    public static class NullCxfEndpointConfigurer implements CxfEndpointConfigurer {

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
