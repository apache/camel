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
package org.apache.camel.swagger;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.component.seda.SedaEndpoint;
import org.apache.camel.impl.ActiveMQUuidGenerator;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestConsumerFactory;

public class DummyRestConsumerFactory implements RestConsumerFactory {

    @Override
    public Consumer createConsumer(CamelContext camelContext, Processor processor, String verb, String basePath, String uriTemplate,
                                   String consumes, String produces, RestConfiguration configuration, Map<String, Object> parameters) throws Exception {
        // just use a seda endpoint for testing purpose
        String id;
        if (uriTemplate != null) {
            id = ActiveMQUuidGenerator.generateSanitizedId(basePath + uriTemplate);
        } else {
            id = ActiveMQUuidGenerator.generateSanitizedId(basePath);
        }
        // remove leading dash as we add that ourselves
        if (id.startsWith("-")) {
            id = id.substring(1);
        }
        SedaEndpoint seda = camelContext.getEndpoint("seda:" + verb + "-" + id, SedaEndpoint.class);
        return seda.createConsumer(processor);
    }

}
