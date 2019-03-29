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
package org.apache.camel.component.jbpm.workitem;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.ExchangeBuilder;
import org.apache.camel.component.jbpm.JBPMConstants;
import org.kie.api.executor.Command;
import org.kie.api.executor.CommandContext;
import org.kie.api.executor.ExecutionResults;
import org.kie.api.runtime.process.WorkItem;
import org.kie.internal.runtime.Cacheable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Camel jBPM {@link Command} which allows to call Camel routes with a <code>direct</code> endpoint.
 * <p/>
 * The command passes the {@WorkItem} retrieved from the {@link CommandContext} to the route that has a consumer on the endpoint-id 
 * that can be passed with the <code>camel-endpoint-id</code> {@link WorkItem} parameter. E.g. when a the value "myCamelEndpoint" is passed to the 
 * {link WorkItem} via the <code>camel-endpoint-id</code> parameter, this {@link Command} will send the {@link WorkItem} to 
 * the Camel URI <code>direct:myCamelEndpoint</code>.  
 * <p/>
 * The body of the result {@link Message} of the invocation is returned via the <code>Response</code> parameter. Access to the raw response 
 * {@link Message} is provided via the <code>Message</code> parameter. This gives the user access to more advanced fields like message headers 
 * and attachments.
 */
public abstract class AbstractCamelCommand implements Command, Cacheable {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractCamelCommand.class);

    public AbstractCamelCommand() {
    }
    
    @Override
    public ExecutionResults execute(CommandContext ctx) throws Exception {
        
        WorkItem workItem = (WorkItem) ctx.getData("workItem");
      
        String camelEndpointId = (String) workItem.getParameter(JBPMConstants.CAMEL_ENDPOINT_ID_WI_PARAM);

        // We only support direct. We don't need to support more, as direct simply gives us the entrypoint into the actual Camel Routes.
        String camelUri = "direct:" + camelEndpointId;
        
        ProducerTemplate producerTemplate = getProducerTemplate(ctx);
        Exchange inExchange = ExchangeBuilder.anExchange(producerTemplate.getCamelContext()).withBody(workItem).build();
        Exchange outExchange = producerTemplate.send(camelUri, inExchange);

        // producerTemplate.send does not throw exceptions, instead they are set on the returned Exchange.
        if (outExchange.getException() != null) {
            throw outExchange.getException();
        }

        Message outMessage = outExchange.getOut();

        ExecutionResults results = new ExecutionResults();
        Object response = outMessage.getBody();
        results.setData(JBPMConstants.RESPONSE_WI_PARAM, response);
        results.setData(JBPMConstants.MESSAGE_WI_PARAM, outMessage);

        return results;
    }

    protected abstract ProducerTemplate getProducerTemplate(CommandContext ctx);

}