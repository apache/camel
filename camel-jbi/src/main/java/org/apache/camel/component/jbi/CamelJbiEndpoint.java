/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE
 * file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file
 * to You under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.apache.camel.component.jbi;

import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.NormalizedMessage;
import javax.xml.namespace.QName;
import org.apache.servicemix.common.endpoints.ProviderEndpoint;

/**
 * The endpoint in the service engine
 * 
 * @version $Revision: 426415 $
 */
public class CamelJbiEndpoint extends ProviderEndpoint {

    private static final QName SERVICE_NAME=new QName("http://camel.servicemix.org","CamelEndpointComponent");
    private JbiEndpoint jbiEndpoint;
    private JbiBinding binding;

    public CamelJbiEndpoint(JbiEndpoint jbiEndpoint, JbiBinding binding){
        this.jbiEndpoint=jbiEndpoint;
        this.binding=binding;
        this.service=SERVICE_NAME;
        this.endpoint=jbiEndpoint.getEndpointUri();
    }

    protected void processInOnly(MessageExchange exchange,NormalizedMessage in) throws Exception{
        // lets use the inbound processor to handle the exchange
        JbiExchange camelExchange = new JbiExchange(jbiEndpoint.getContext(), binding, exchange);
        jbiEndpoint.getInboundProcessor().onExchange(camelExchange);
    }

    protected void processInOut(MessageExchange exchange,NormalizedMessage in,NormalizedMessage out) throws Exception{
        /*
         * ToDo
         */
    }
}
