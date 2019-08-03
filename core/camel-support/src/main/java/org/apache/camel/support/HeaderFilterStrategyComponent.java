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
package org.apache.camel.support;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.spi.Metadata;

/**
 * Base class for components to support configuring a {@link org.apache.camel.spi.HeaderFilterStrategy}.
 */
public abstract class HeaderFilterStrategyComponent extends DefaultComponent implements HeaderFilterStrategyAware {

    @Metadata(label = "filter", description = "To use a custom org.apache.camel.spi.HeaderFilterStrategy to filter header to and from Camel message.")
    private HeaderFilterStrategy headerFilterStrategy;
    
    public HeaderFilterStrategyComponent() {
    }

    public HeaderFilterStrategyComponent(CamelContext context) {
        super(context);
    }
    
    @Override
    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    /**
     * To use a custom {@link org.apache.camel.spi.HeaderFilterStrategy} to filter header to and from Camel message.
     */
    @Override
    public void setHeaderFilterStrategy(HeaderFilterStrategy strategy) {
        headerFilterStrategy = strategy;
    }

    /**
     * Sets the header filter strategy to use from the given endpoint if the endpoint is a {@link HeaderFilterStrategyAware} type.
     */
    public void setEndpointHeaderFilterStrategy(Endpoint endpoint) {
        if (headerFilterStrategy != null && endpoint instanceof HeaderFilterStrategyAware) {
            ((HeaderFilterStrategyAware)endpoint).setHeaderFilterStrategy(headerFilterStrategy);
        }
    }
}
