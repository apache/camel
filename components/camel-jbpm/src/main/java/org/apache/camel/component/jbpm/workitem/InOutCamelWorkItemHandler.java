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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.ExchangeBuilder;
import org.apache.camel.component.jbpm.JBPMConstants;
import org.drools.core.process.instance.impl.WorkItemImpl;
import org.jbpm.process.workitem.core.util.Wid;
import org.jbpm.process.workitem.core.util.WidMavenDepends;
import org.jbpm.process.workitem.core.util.WidParameter;
import org.jbpm.process.workitem.core.util.WidResult;
import org.jbpm.process.workitem.core.util.service.WidAction;
import org.jbpm.process.workitem.core.util.service.WidService;
import org.kie.api.runtime.manager.RuntimeManager;
import org.kie.api.runtime.process.WorkItem;
import org.kie.api.runtime.process.WorkItemHandler;
import org.kie.api.runtime.process.WorkItemManager;

/**
 * Camel jBPM {@link WorkItemHandler} that sends {@link Exchange Exchanges} with an <code>InOut</code> Message Exchange Pattern.
 * <p/>
 * This handler parses the response message from the given Camel route and completes the {@link WorkItem}. The use-case for this handler is
 * synchronous, request-response style, communication.
 * <p/>
 * The handler creates a Camel Exchange and sets the {@link WorkItem} as the body of the {@link Message}. Furthermore, the following message
 * headers are set:
 * <ul>
 * <li>deploymentId</li>
 * <li>processInstanceId</li>
 * <li>workItemId</li>
 * </ul>
 */
@Wid(
        widfile = "InOutCamelConnector.wid",
        name = "InOutCamelConnector",
        displayName = "InOutCamelConnector",
        category = "Camel",
        defaultHandler = "mvel: new org.apache.camel.component.jbpm.workitem.InOutCamelWorkItemHandler()",
        documentation = "${artifactId}/index.html",
        parameters = {
                @WidParameter(name = JBPMConstants.CAMEL_ENDPOINT_ID_WI_PARAM)
        },
        results = {
                @WidResult(name = JBPMConstants.RESPONSE_WI_PARAM),
                @WidResult(name = JBPMConstants.MESSAGE_WI_PARAM) },
        mavenDepends = {
                @WidMavenDepends(group = "${groupId}",
                        artifact = "${artifactId}",
                        version = "${version}")
        },
        serviceInfo = @WidService(category = "${name}",
                description = "${description}",
                keywords = "apache,camel,payload,route,connector",
                action = @WidAction(title = "Send payload to a Camel endpoint")),
        icon = "InOutCamelConnector.png"
    )
public class InOutCamelWorkItemHandler extends AbstractCamelWorkItemHandler {

    public InOutCamelWorkItemHandler() {
    }

    public InOutCamelWorkItemHandler(String camelEndpointId) {
        super(camelEndpointId);
    }

    public InOutCamelWorkItemHandler(RuntimeManager runtimeManager) {
        super(runtimeManager);
    }

    public InOutCamelWorkItemHandler(RuntimeManager runtimeManager, String camelEndpointId) {
        super(runtimeManager, camelEndpointId);
    }

    @Override
    protected void handleResponse(Exchange responseExchange, WorkItem workItem, WorkItemManager manager) {
        Message outMessage = responseExchange.getOut();

        Map<String, Object> result = new HashMap<>();
        Object response = outMessage.getBody();
        result.put(JBPMConstants.RESPONSE_WI_PARAM, response);
        result.put(JBPMConstants.MESSAGE_WI_PARAM, outMessage);

        manager.completeWorkItem(workItem.getId(), result);
    }

    @Override
    protected Exchange buildExchange(ProducerTemplate template, WorkItem workItem) {
        return ExchangeBuilder.anExchange(template.getCamelContext())
                .withPattern(ExchangePattern.InOut)
                .withHeader("deploymentId", ((WorkItemImpl) workItem).getDeploymentId())
                .withHeader("processInstanceId", workItem.getProcessInstanceId())
                .withHeader("workItemId", workItem.getId())
                .withBody(workItem)
                .build();
    }

}
