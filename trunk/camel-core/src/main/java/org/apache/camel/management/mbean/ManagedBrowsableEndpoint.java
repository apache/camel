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

import org.apache.camel.Exchange;
import org.apache.camel.spi.BrowsableEndpoint;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;

/**
 * @version 
 */
@ManagedResource(description = "Managed BrowsableEndpoint")
public class ManagedBrowsableEndpoint extends ManagedEndpoint {

    private BrowsableEndpoint endpoint;

    public ManagedBrowsableEndpoint(BrowsableEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    public BrowsableEndpoint getEndpoint() {
        return endpoint;
    }

    @ManagedOperation(description = "Current number of Exchanges in Queue")
    public long queueSize() {
        return endpoint.getExchanges().size();
    }

    @ManagedOperation(description = "Get Exchange from queue by index")
    public String browseExchange(Integer index) {
        if (index >= endpoint.getExchanges().size()) {
            return null;
        }
        Exchange exchange = endpoint.getExchanges().get(index);
        if (exchange == null) {
            return null;
        }
        // must use java type with JMX such as java.lang.String
        return exchange.toString();
    }

}
