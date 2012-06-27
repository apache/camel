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
package org.apache.camel.dynamicep;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;

/**
 * Dynamic Endpoint 
 * 
 * Allows to create a list of consumers that feed into the same route.
 * Example: from("dynamicep:jms:myQueue,file:myDir").to("log:test)
 * 
 * As it is possible to use properties in the endpoint definition this
 * allows to define a dynamical list of endpoints a route should listen on
 */
public class DynamicEndpoint extends DefaultEndpoint {

    public DynamicEndpoint() {
    }

    public DynamicEndpoint(String uri, DynamicComponent component) {
        super(uri, component);
    }

    @SuppressWarnings("deprecation")
    public DynamicEndpoint(String endpointUri) {
        super(endpointUri);
    }

    public Producer createProducer() throws Exception {
        throw new IllegalArgumentException("This component only supports consumers");
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        return new DynamicConsumer(this, processor);
    }

    public boolean isSingleton() {
        return true;
    }
 
}
