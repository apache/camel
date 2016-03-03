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
package org.apache.camel.component.kafka;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.EndpointHelper;

public class KafkaComponent extends UriEndpointComponent {

    public KafkaComponent() {
        super(KafkaEndpoint.class);
    }

    public KafkaComponent(CamelContext context) {
        super(context, KafkaEndpoint.class);
    }

    @Override
    protected KafkaEndpoint createEndpoint(String uri,
                                           String remaining,
                                           Map<String, Object> params) throws Exception {

        KafkaEndpoint endpoint = new KafkaEndpoint(uri, this);
        String brokers = remaining.split("\\?")[0];
        Object confparam = params.get("configuration");
        if (confparam != null) {
            // need a special handling to resolve the reference before other parameters are set/merged into the config
            KafkaConfiguration confobj = null;
            if (confparam instanceof KafkaConfiguration) {
                confobj = (KafkaConfiguration)confparam;
            } else if (confparam instanceof String && EndpointHelper.isReferenceParameter((String)confparam)) { 
                confobj = (KafkaConfiguration)CamelContextHelper.lookup(getCamelContext(), ((String)confparam).substring(1));
            }
            if (confobj != null) {
                endpoint.setConfiguration(confobj.copy());
            }
            params.remove("configuration");
        }
        if (brokers != null) {
            endpoint.getConfiguration().setBrokers(brokers);
        }
        setProperties(endpoint, params);
        return endpoint;
    }
}
