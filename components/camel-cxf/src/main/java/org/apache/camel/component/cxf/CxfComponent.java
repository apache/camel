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
package org.apache.camel.component.cxf;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.HeaderFilterStrategyAware;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.spi.HeaderFilterStrategy;


/**
 * Defines the <a href="http://activemq.apache.org/camel/cxf.html">CXF Component</a>

 * @version $Revision$
 */
public class CxfComponent extends DefaultComponent<CxfExchange> implements HeaderFilterStrategyAware {


    private HeaderFilterStrategy headerFilterStrategy = new CxfHeaderFilterStrategy();

    public CxfComponent() {
    }

    public CxfComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected Endpoint<CxfExchange> createEndpoint(String uri, String remaining, Map parameters) throws Exception {
        // Now we need to add the address, endpoint name, WSDL url or the SEI to build up an endpoint
        CxfEndpoint result = new CxfEndpoint(uri, remaining, this);
        setProperties(result, parameters);
        // We can check the endpoint integration here
        return result;
    }

    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    public void setHeaderFilterStrategy(HeaderFilterStrategy strategy) {
        headerFilterStrategy = strategy;
    }


}
