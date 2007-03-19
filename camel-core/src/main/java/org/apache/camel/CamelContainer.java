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
package org.apache.camel;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.impl.DefaultEndpointResolver;
import org.apache.camel.impl.DefaultExchangeConverter;

/**
 * Represents the container used to configure routes and the policies to use.
 *
 * @version $Revision$
 */
public class CamelContainer<E> {
	
    private EndpointResolver<E> endpointResolver;
    private ExchangeConverter exchangeConverter;
    private Map<String, Object> components = (Map<String, Object>)Collections.synchronizedMap(new HashMap<String, Object>());

    public EndpointResolver<E> getEndpointResolver() {
        if (endpointResolver == null) {
            endpointResolver = createEndpointResolver();
        }
        return endpointResolver;
    }

    public void setEndpointResolver(EndpointResolver<E> endpointResolver) {
        this.endpointResolver = endpointResolver;
    }

    public ExchangeConverter getExchangeConverter() {
        if (exchangeConverter == null) {
            exchangeConverter = createExchangeConverter();
        }
        return exchangeConverter;
    }

    public void setExchangeConverter(ExchangeConverter exchangeConverter) {
        this.exchangeConverter = exchangeConverter;
    }

    // Implementation methods
    //-----------------------------------------------------------------------
    protected EndpointResolver<E> createEndpointResolver() {
        return new DefaultEndpointResolver<E>();
    }

    /**
     * Lazily create a default exchange converter implementation
     */
    protected ExchangeConverter createExchangeConverter() {
        return new DefaultExchangeConverter();
    }

	public Map<String, Object> getComponents() {
		return components;
	}
	public void setComponents(Map<String, Object> components) {
		this.components = Collections.synchronizedMap(components);
	}
}
