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
package org.apache.camel.component.restbinding;

import java.util.Map;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.component.seda.SedaEndpoint;
import org.apache.camel.impl.ActiveMQUuidGenerator;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.spi.RestBindingCapable;

public class DummyRestBindingCapableComponent extends DefaultComponent implements RestBindingCapable {

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        return null;
    }

    @Override
    public Consumer createConsumer(RestBindingEndpoint endpoint, Processor processor) throws Exception {
        // just use a seda endpoint for testing purpose
        String id = ActiveMQUuidGenerator.generateSanitizedId(endpoint.getPath());
        // remove leading dash as we add that ourselves
        if (id.startsWith("-")) {
            id = id.substring(1);
        }
        SedaEndpoint seda = getCamelContext().getEndpoint("seda:" + endpoint.getVerb() + "-" + id, SedaEndpoint.class);
        return seda.createConsumer(processor);
    }
}
