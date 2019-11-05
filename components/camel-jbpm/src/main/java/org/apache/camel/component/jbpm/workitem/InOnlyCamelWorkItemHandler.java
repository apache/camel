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
 * Camel jBPM {@link WorkItemHandler} that sends {@link Exchange Exchanges} with an <code>InOnly</code> Message Exchange Pattern.
 * <p/>
 * This handler does <b>NOT<b/> complete the {@link WorkItem}, and will not parse any response from the Camel route, other than possible exceptions.
 * The use-case for this handler is asynchronous, one-way, communication, where an external party is responsible for completing the
 * {@link WorkItem} at a later point in time.
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
        widfile = "InOnlyCamelConnector.wid",
        name = "InOnlyCamelConnector",
        displayName = "InOnlyCamelConnector",
        category = "Camel",
        defaultHandler = "mvel: new org.apache.camel.component.jbpm.workitem.InOnlyCamelWorkItemHandler()",
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
        icon = "InOnlyCamelConnector.png"  
    )
public class InOnlyCamelWorkItemHandler extends AbstractCamelWorkItemHandler {

    public InOnlyCamelWorkItemHandler() {
    }

    public InOnlyCamelWorkItemHandler(String camelEndpointId) {
        super(camelEndpointId);
    }

    public InOnlyCamelWorkItemHandler(RuntimeManager runtimeManager) {
        super(runtimeManager);
    }

    public InOnlyCamelWorkItemHandler(RuntimeManager runtimeManager, String camelEndpointId) {
        super(runtimeManager, camelEndpointId);
    }

    @Override
    protected void handleResponse(Exchange responseExchange, WorkItem workItem, WorkItemManager manager) {
        // no-op. There is no response for InOnly, so need to handle anything
    }

    @Override
    protected Exchange buildExchange(ProducerTemplate template, WorkItem workItem) {
        return ExchangeBuilder.anExchange(template.getCamelContext())
                .withPattern(ExchangePattern.InOnly)
                .withHeader("deploymentId", ((WorkItemImpl) workItem).getDeploymentId())
                .withHeader("processInstanceId", workItem.getProcessInstanceId())
                .withHeader("workItemId", workItem.getId())
                .withBody(workItem).build();
    }

}
