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
package org.apache.camel.component.xchange;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.support.DefaultComponent;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.utils.Assert;

public class XChangeComponent extends DefaultComponent {

    private XChange exchange;
    
    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {

        // Init the configuration
        XChangeConfiguration configuration = new XChangeConfiguration(this);
        setProperties(configuration, parameters);

        // Set the required name of the exchange
        configuration.setName(remaining);

        XChange exchange = createXChange(configuration);
        XChangeEndpoint endpoint = new XChangeEndpoint(uri, this, configuration, exchange);
        
        return endpoint;
    }

    public XChange getXChange() {
        return exchange;
    }
    
    private synchronized XChange createXChange(XChangeConfiguration configuration) {
        
        if (exchange == null) {
            
            // Get the XChange implementation
            Class<? extends Exchange> exchangeClass = configuration.getXChangeClass();
            Assert.notNull(exchangeClass, "XChange not supported: " + configuration.getName());
            
            // Create the XChange and associated Endpoint
            exchange = new XChange(ExchangeFactory.INSTANCE.createExchange(exchangeClass));
        }
        
        return exchange;
    }

}