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

package org.apache.camel.component.jbpm.workitem;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.ExchangeBuilder;
import org.jbpm.process.workitem.core.AbstractLogOrThrowWorkItemHandler;
import org.jbpm.process.workitem.core.util.Wid;
import org.jbpm.process.workitem.core.util.WidMavenDepends;
import org.jbpm.process.workitem.core.util.WidParameter;
import org.jbpm.process.workitem.core.util.WidResult;
import org.jbpm.process.workitem.core.util.service.WidAction;
import org.jbpm.process.workitem.core.util.service.WidService;
import org.jbpm.services.api.service.ServiceRegistry;
import org.kie.api.runtime.manager.RuntimeManager;
import org.kie.api.runtime.process.WorkItem;
import org.kie.api.runtime.process.WorkItemManager;
import org.kie.internal.runtime.Cacheable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Camel jBPM {@link WorkItemHandler} which allows to call Camel routes with a <code>direct</code> endpoint.
 * <p/>
 * The handler passes the {@WorkItem} to the route that has a consumer on the endpoint-id that can be passed with the
 * <code>camel-endpoint-id</code>{@link WorkItem} parameter. E.g. when a the value "myCamelEndpoint" is passed to the 
 * {link WorkItem} via the <code>camel-endpoint-id</code> parameter, this command will send the {@link WorkItem} to the Camel URI
 * <code>direct://myCamelEndpoint</code>.
 * <p/>
 * The body of the result {@link Message} of the invocation is returned via the <code>response</code> parameter. Access to the raw response
 * {@link Message} is provided via the <code>message</code> parameter. This gives the user access to more advanced fields like message
 * headers and attachments.
 * <p/>
 * This handler can be constructed in 2 ways. When using the default constructor, the handler will try to find the global KIE
 * {@link CamelContext} from the <code>jBPM</code> {@link ServiceRegistry}. When the {@link RuntimeManager} is passed to the constructor,
 * the handler will retrieve and use the {@link CamelContext} bound to the {@link RuntimeManage} from the {@link ServiceRegistry}
 * 
 */
@Wid(widfile = "CamelConnector.wid", name = "CamelConnector", displayName = "CamelConnector", defaultHandler = "mvel: new org.apache.camel.component.jbpm.workitem.CamelWorkitemHandler()", documentation = "${artifactId}/index.html", parameters = {
		@WidParameter(name = "camel-endpoint-id") }, results = { @WidResult(name = "response"),
				@WidResult(name = "message") }, mavenDepends = {
						@WidMavenDepends(group = "${groupId}", artifact = "${artifactId}", version = "${version}") }, serviceInfo = @WidService(category = "${name}", description = "${description}", keywords = "apache,camel,payload,route,connector", action = @WidAction(title = "Send payload to a Camel endpoint")))
public class CamelWorkItemHandler extends AbstractLogOrThrowWorkItemHandler implements Cacheable {

	private static final String GLOBAL_CAMEL_CONTEXT_SERVICE_KEY = "GlobalCamelService";
	private static final String RUNTIME_CAMEL_CONTEXT_SERVICE_POSTFIX = "_CamelService";

	private static final String CAMEL_ENDPOINT_ID_PARAM = "camel-endpoint-id";
	private static final String RESPONSE_PARAM = "response";
	private static final String MESSAGE_PARAM = "message";

	private static Logger logger = LoggerFactory.getLogger(CamelWorkItemHandler.class);

	private final ProducerTemplate producerTemplate;

	/**
	 * Default Constructor. This creates a {@link ProducerTemplate} for the global {@link CamelContext}.
	 */
	public CamelWorkItemHandler() {
		CamelContext globalCamelContext = (CamelContext) ServiceRegistry.get().service(GLOBAL_CAMEL_CONTEXT_SERVICE_KEY);
		// TODO: Should we allow to set the maximumCacheSize on the producer?
		this.producerTemplate = globalCamelContext.createProducerTemplate();
	}

	/**
	 * Constructor which accepts {@link RuntimeManager}. This causes this WorkItemHanlder to create a {@link ProducerTemplate} for the
	 * runtime specific {@link CamelContext}.
	 */
	public CamelWorkItemHandler(RuntimeManager runtimeManager) {
		String runtimeCamelContextKey = runtimeManager.getIdentifier() + RUNTIME_CAMEL_CONTEXT_SERVICE_POSTFIX;
		CamelContext runtimeCamelContext = (CamelContext) ServiceRegistry.get().service(runtimeCamelContextKey);
		// TODO: Should we allow to set the maximumCacheSize on the producer?
		this.producerTemplate = runtimeCamelContext.createProducerTemplate();
	}

	public void executeWorkItem(WorkItem workItem, final WorkItemManager manager) {

		String camelEndpointId = (String) workItem.getParameter(CAMEL_ENDPOINT_ID_PARAM);

		// We only support direct. We don't need to support more, as direct simply gives us the entrypoint into the actual Camel Routes.
		String camelUri = "direct://" + camelEndpointId;
		try {
			Exchange inExchange = ExchangeBuilder.anExchange(producerTemplate.getCamelContext()).withBody(workItem).build();
			Exchange outExchange = producerTemplate.send(camelUri, inExchange);
			// producerTemplate.send does not throw exceptions, instead they are set on the returned Exchange.
			if (outExchange.getException() != null) {
				throw outExchange.getException();
			}
			Message outMessage = outExchange.getOut();

			Map<String, Object> result = new HashMap<>();
			Object response = outMessage.getBody();
			result.put(RESPONSE_PARAM, response);
			result.put(MESSAGE_PARAM, outMessage);

			manager.completeWorkItem(workItem.getId(), result);
		} catch (Exception e) {
			handleException(e);
		}
	}

	public void abortWorkItem(WorkItem workItem, WorkItemManager manager) {
		// Do nothing, cannot be aborted
	}

	@Override
	public void close() {
		try {
			this.producerTemplate.stop();
		} catch (Exception e) {
			logger.warn("Error encountered while closing the Camel Producer Template.", e);
			// Not much we can do here, so swallowing exception.
		}
	}

}