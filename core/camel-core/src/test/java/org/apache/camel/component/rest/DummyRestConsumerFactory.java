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
package org.apache.camel.component.rest;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.component.seda.SedaEndpoint;
import org.apache.camel.impl.engine.DefaultUuidGenerator;
import org.apache.camel.spi.RestApiConsumerFactory;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestConsumerFactory;
import org.apache.camel.support.CamelContextHelper;

public class DummyRestConsumerFactory implements RestConsumerFactory, RestApiConsumerFactory {

    private Object dummy;

    public Object getDummy() {
        return dummy;
    }

    public void setDummy(Object dummy) {
        this.dummy = dummy;
    }

    @Override
    public Consumer createConsumer(CamelContext camelContext, Processor processor, String verb, String basePath, String uriTemplate, String consumes, String produces,
                                   RestConfiguration configuration, Map<String, Object> parameters)
        throws Exception {
        // just use a seda endpoint for testing purpose
        String id;
        if (uriTemplate != null) {
            id = DefaultUuidGenerator.generateSanitizedId(basePath + uriTemplate);
        } else {
            id = DefaultUuidGenerator.generateSanitizedId(basePath);
        }
        // remove leading dash as we add that ourselves
        if (id.startsWith("-")) {
            id = id.substring(1);
        }

        if (configuration.getConsumerProperties() != null) {
            String ref = (String)configuration.getConsumerProperties().get("dummy");
            if (ref != null) {
                dummy = CamelContextHelper.mandatoryLookup(camelContext, ref.substring(1));
            }
        }

        SedaEndpoint seda = camelContext.getEndpoint("seda:" + verb + "-" + id, SedaEndpoint.class);
        // speedup pooling to also be able to shutdown faster
        seda.setPollTimeout(10);
        return seda.createConsumer(processor);
    }

    @Override
    public Consumer createApiConsumer(CamelContext camelContext, Processor processor, String contextPath, RestConfiguration configuration, Map<String, Object> parameters)
        throws Exception {
        // just use a seda endpoint for testing purpose
        String id = DefaultUuidGenerator.generateSanitizedId(contextPath);
        // remove leading dash as we add that ourselves
        if (id.startsWith("-")) {
            id = id.substring(1);
        }
        SedaEndpoint seda = camelContext.getEndpoint("seda:api:" + "-" + id, SedaEndpoint.class);
        // speedup pooling to also be able to shutdown faster
        seda.setPollTimeout(10);
        return seda.createConsumer(processor);
    }

}
