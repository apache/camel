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

import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.FailedToCreateProducerException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.servicemix.common.ServiceUnit;
import org.apache.servicemix.common.endpoints.ProviderEndpoint;

import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.NormalizedMessage;
import javax.xml.namespace.QName;

/**
 * A JBI endpoint which when invoked will delegate to a Camel endpoint
 *
 * @version $Revision: 426415 $
 */
public class CamelJbiEndpoint extends ProviderEndpoint {
    public static final QName SERVICE_NAME = new QName("http://activemq.apache.org/camel/schema/jbi", "endpoint");

    private static final transient Log log = LogFactory.getLog(CamelJbiEndpoint.class);
    private Endpoint camelEndpoint;
    private JbiBinding binding;
    private Processor camelProcessor;

    public CamelJbiEndpoint(ServiceUnit serviceUnit, QName service, String endpoint, Endpoint camelEndpoint, JbiBinding binding, Processor camelProcessor) {
        super(serviceUnit, service, endpoint);
        this.camelProcessor = camelProcessor;
        this.camelEndpoint = camelEndpoint;
        this.binding = binding;
    }

    public CamelJbiEndpoint(ServiceUnit serviceUnit, Endpoint camelEndpoint, JbiBinding binding, Processor camelProcesso) {
        this(serviceUnit, SERVICE_NAME, camelEndpoint.getEndpointUri(), camelEndpoint, binding, camelProcesso);
    }

    protected void processInOnly(MessageExchange exchange, NormalizedMessage in) throws Exception {
        if (log.isDebugEnabled()) {
            log.debug("Received exchange: " + exchange);
        }
        JbiExchange camelExchange = new JbiExchange(camelEndpoint.getContext(), binding, exchange);
        camelProcessor.process(camelExchange);
    }

    protected void processInOut(MessageExchange exchange, NormalizedMessage in, NormalizedMessage out) throws Exception {
        if (log.isDebugEnabled()) {
            log.debug("Received exchange: " + exchange);
        }
        /*
         * ToDo
         */
    }
}
