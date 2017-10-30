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
package org.apache.camel.component.cmis;

import java.io.InputStream;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.ScheduledPollConsumer;
import org.apache.chemistry.opencmis.client.api.OperationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The CMIS consumer.
 */
public class CMISConsumer extends ScheduledPollConsumer {
    private static final Logger LOG = LoggerFactory.getLogger(CMISConsumer.class);
    private final CMISSessionFacadeFactory sessionFacadeFactory;
    private CMISSessionFacade sessionFacade;

    public CMISConsumer(CMISEndpoint cmisEndpoint, Processor processor, CMISSessionFacadeFactory sessionFacadeFactory) {
        super(cmisEndpoint, processor);
        this.sessionFacadeFactory = sessionFacadeFactory;
        this.sessionFacade = null;
    }

    @Override
    public CMISEndpoint getEndpoint() {
        return (CMISEndpoint) super.getEndpoint();
    }

    @Override
    protected int poll() throws Exception {
        return getSessionFacade().poll(this);
    }
    
    public OperationContext createOperationContext() throws Exception {
        return getSessionFacade().createOperationContext();
    }

    int sendExchangeWithPropsAndBody(Map<String, Object> properties, InputStream inputStream)
        throws Exception {
        Exchange exchange = getEndpoint().createExchange();
        exchange.getIn().setHeaders(properties);
        exchange.getIn().setBody(inputStream);
        LOG.debug("Polling node : {}", properties.get("cmis:name"));
        getProcessor().process(exchange);
        return 1;
    }

    private CMISSessionFacade getSessionFacade() throws Exception {
        if (sessionFacade == null) {
            sessionFacade = sessionFacadeFactory.create(getEndpoint());
            sessionFacade.initSession();
        }

        return sessionFacade;
    }

}
