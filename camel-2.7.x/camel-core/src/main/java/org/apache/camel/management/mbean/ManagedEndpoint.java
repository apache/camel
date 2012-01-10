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
package org.apache.camel.management.mbean;

import org.apache.camel.Endpoint;
import org.apache.camel.ServiceStatus;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.spi.ManagementStrategy;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedResource;

@ManagedResource(description = "Managed Endpoint")
public class ManagedEndpoint implements ManagedInstance {
    private final Endpoint endpoint;

    public ManagedEndpoint(Endpoint endpoint) {
        this.endpoint = endpoint;
    }

    public void init(ManagementStrategy strategy) {
        // do nothing
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }

    @ManagedAttribute(description = "Camel id")
    public String getCamelId() {
        return endpoint.getCamelContext().getName();
    }

    @ManagedAttribute(description = "Endpoint Uri")
    public String getEndpointUri() {
        return endpoint.getEndpointUri();
    }

    @ManagedAttribute(description = "Singleton")
    public boolean isSingleton() {
        return endpoint.isSingleton();
    }

    @ManagedAttribute(description = "Service State")
    public String getState() {
        // must use String type to be sure remote JMX can read the attribute without requiring Camel classes.
        if (endpoint instanceof ServiceSupport) {
            ServiceStatus status = ((ServiceSupport) endpoint).getStatus();
            // if no status exists then its stopped
            if (status == null) {
                status = ServiceStatus.Stopped;
            }
            return status.name();
        }

        // assume started if not a ServiceSupport instance
        return ServiceStatus.Started.name();
    }

    public Object getInstance() {
        return endpoint;
    }
}
