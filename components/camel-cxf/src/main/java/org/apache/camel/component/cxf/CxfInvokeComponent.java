/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.cxf;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;
import org.apache.cxf.Bus;
import org.apache.cxf.bus.CXFBusFactory;

import java.net.URI;
import java.util.Map;
import java.util.Properties;

/**
 * @version $Revision$
 */
public class CxfInvokeComponent extends DefaultComponent<CxfExchange> {
    private Bus bus;

    public CxfInvokeComponent() {
        bus = CXFBusFactory.getDefaultBus();
    }

    public CxfInvokeComponent(CamelContext context) {
        super(context);
        bus = CXFBusFactory.getDefaultBus();
    }

    @Override
    protected Endpoint<CxfExchange> createEndpoint(String uri, String remaining, Map parameters) throws Exception {
        return new CxfInvokeEndpoint(remaining, this, createProperties(parameters));
    }

    protected Properties createProperties(Map parameters) {
        Properties answer = new Properties();
        answer.putAll(parameters);
        return answer;
    }

    public Bus getBus() {
        return bus;
    }
}