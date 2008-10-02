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
package org.apache.camel.management;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedResource;

@ManagedResource(description = "Managed Endpoint", currencyTimeLimit = 15)
public class ManagedEndpoint {

    private Endpoint<? extends Exchange> endpoint;

    public ManagedEndpoint(Endpoint<? extends Exchange> endpoint) {
        this.endpoint = endpoint;
    }

    public Endpoint<? extends Exchange> getEndpoint() {
        return endpoint;
    }

    @ManagedAttribute(description = "Endpoint Uri")
    public String getUri() throws Exception {
        return endpoint.getEndpointUri();
    }

    @ManagedAttribute(description = "Singleton")
    public boolean getSingleton() throws Exception {
        return endpoint.isSingleton();
    }
}
