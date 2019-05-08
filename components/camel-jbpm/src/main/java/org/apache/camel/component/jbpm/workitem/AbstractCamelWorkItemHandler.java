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

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.jbpm.JBPMConstants;
import org.jbpm.bpmn2.handler.WorkItemHandlerRuntimeException;
import org.jbpm.process.workitem.core.AbstractLogOrThrowWorkItemHandler;
import org.jbpm.services.api.service.ServiceRegistry;
import org.kie.api.runtime.manager.RuntimeManager;
import org.kie.api.runtime.process.WorkItem;
import org.kie.api.runtime.process.WorkItemHandler;
import org.kie.api.runtime.process.WorkItemManager;
import org.kie.internal.runtime.Cacheable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Camel jBPM {@link WorkItemHandler} which allows to call Camel routes with a <code>direct</code> endpoint.
 * <p/>
 * The handler passes the {@WorkItem} to the route that has a consumer on the endpoint-id that can be passed with the
 * <code>CamelEndpointId</code>{@link WorkItem} parameter. E.g. when a the value "myCamelEndpoint" is passed to the {link WorkItem} via the
 * <code>CamelEndpointId</code> parameter, this command will send the {@link WorkItem} to the Camel URI
 * <code>direct:myCamelEndpoint</code>.
 * <p/>
 * The body of the result {@link Message} of the invocation is returned via the <code>Response</code> parameter. Access to the raw response
 * {@link Message} is provided via the <code>Message</code> parameter. This gives the user access to more advanced fields like message
 * headers and attachments.
 * <p/>
 * The handler can be configured to always wrap exceptions coming from Camel in a {@link WorkItemHandlerRuntimeException}. This is the default behaviour, but
 * can also be explicitly configured by setting the <code>HandleExceptions</code> workitem parameter to <code>true</code>/ When
 * the <code>HandleExceptions</code> workitem parameter is set to <code>false</code>, any exceptions coming from the Camel route will simply be
 * re-thrown. This makes the Camel route's exception handling logic responsible for correctly handling any exceptions.
 * <p/>
 * This handler can be constructed in multiple ways. When you don't pass a {@link RuntimeManager} to the constructor, the handler will try
 * to find the global KIE {@link CamelContext} from the <code>jBPM</code> {@link ServiceRegistry}. When the {@link RuntimeManager} is passed
 * to the constructor, the handler will retrieve and use the {@link CamelContext} bound to the {@link RuntimeManage} from the
 * {@link ServiceRegistry}. When a <code>CamelEndpointId</code> is passed to the constructor, the handler will send all requests to the
 * Camel route that is consuming from that endpoint, unless the endpoint is overridden by passing a the <code>CamelEndpointId</code> in the
 * {@link WorkItem} parameters.
 * 
 */
public abstract class AbstractCamelWorkItemHandler extends AbstractLogOrThrowWorkItemHandler implements Cacheable {

    private static Logger logger = LoggerFactory.getLogger(AbstractCamelWorkItemHandler.class);

    private ProducerTemplate producerTemplate;

    private final String camelEndpointId;

    private final String camelContextKey;

    private boolean initialized;

    /**
     * Default Constructor. This creates a {@link ProducerTemplate} for the global {@link CamelContext}.
     */
    public AbstractCamelWorkItemHandler() {
        this("");
    }

    public AbstractCamelWorkItemHandler(String camelEndointId) {
        this.camelEndpointId = camelEndointId;
        this.camelContextKey = JBPMConstants.GLOBAL_CAMEL_CONTEXT_SERVICE_KEY;
        this.producerTemplate = buildProducerTemplate(camelContextKey);
        this.initialized = true;
    }

    /**
     * Constructor which accepts {@link RuntimeManager}. This causes this WorkItemHanlder to create a {@link ProducerTemplate} for the
     * runtime specific {@link CamelContext}.
     */
    public AbstractCamelWorkItemHandler(RuntimeManager runtimeManager) {
        this(runtimeManager, "");
    }

    public AbstractCamelWorkItemHandler(RuntimeManager runtimeManager, String camelEndpointId) {
        this.camelEndpointId = camelEndpointId;
        this.camelContextKey = runtimeManager.getIdentifier() + JBPMConstants.DEPLOYMENT_CAMEL_CONTEXT_SERVICE_KEY_POSTFIX;
        /*
         * Depending on the order of session creation and CamelContext creation and registration, the CamelContext might not yet be
         * available. Hence, when we deal with a Deployment scoped CamelContext, we can lazy-init when the context is not yet available.
         */
        try {
            this.producerTemplate = buildProducerTemplate(camelContextKey);
            this.initialized = true;
        } catch (IllegalArgumentException iae) {
            String message = "CamelContext with identifier '" + camelContextKey
                    + "' not found in ServiceRegistry. This can be caused by the order in which the platform extensions are initialized. " 
                    + "Deferring Camel ProducerTemplate creation until the first WorkItemHandler call.";
            logger.info(message, iae);
        }
    }

    private ProducerTemplate buildProducerTemplate(String key) {
        CamelContext camelContext = (CamelContext) ServiceRegistry.get().service(key);
        return this.producerTemplate = camelContext.createProducerTemplate();
    }


    public void executeWorkItem(WorkItem workItem, final WorkItemManager manager) {
        if (!initialized) {
            this.producerTemplate = buildProducerTemplate(camelContextKey);
            initialized = true;
        }

        /*
         * By default we handle exceptions via the AbstractLogOrThrow superclass.
         * However, the user can specify not to handle exceptions. This makes the Camel
         * route responsible for implementing the handler logic and passing the {@link
         * RuntimeExceotion) to be handled by the process.
         */
        Object isHandleExceptionParamValue = workItem.getParameter(JBPMConstants.HANDLE_EXCEPTION_WI_PARAM);
        boolean isHandleException;
        if (isHandleExceptionParamValue == null) {
            isHandleException = true;
        } else if (isHandleExceptionParamValue instanceof String) {
            isHandleException = Boolean.parseBoolean((String)isHandleExceptionParamValue);
        } else if (isHandleExceptionParamValue instanceof Boolean) {
            isHandleException = ((Boolean)isHandleExceptionParamValue).booleanValue();
        } else {
            throw new IllegalArgumentException("Unsupported type '" + isHandleExceptionParamValue.getClass().getCanonicalName() + "' for workitem parameter '"
                + JBPMConstants.HANDLE_EXCEPTION_WI_PARAM + "'.");
        }

        String workItemCamelEndpointId = getCamelEndpointId(workItem);

        // We only support direct. We don't need to support more, as direct simply gives us the entrypoint into the actual Camel Routes.
        String camelUri = "direct:" + workItemCamelEndpointId;

        try {
            Exchange requestExchange = buildExchange(producerTemplate, workItem);
            logger.debug("Sending Camel Exchange to: " + camelUri);
            Exchange responseExchange = producerTemplate.send(camelUri, requestExchange);
            // producerTemplate.send does not throw exceptions, instead they are set on the returned Exchange.
            if (responseExchange.getException() != null) {
                throw responseExchange.getException();
            }
            handleResponse(responseExchange, workItem, manager);
        } catch (Exception e) {
            /*
             * Handle the exception if 'HandleException' is enabled (which is the default).
             * If it's not enabled, simply throw the exception. Note that in that case, the
             * exception needs to be a RuntimeException.
             */
            if (isHandleException) {
                handleException(e);
            } else {
                throw (RuntimeException)e;
            }
        }
    }

    protected String getCamelEndpointId(WorkItem workItem) {
        String workItemCamelEndpointId = (String) workItem.getParameter(JBPMConstants.CAMEL_ENDPOINT_ID_WI_PARAM);

        if (camelEndpointId != null && !camelEndpointId.isEmpty()) {
            if (workItemCamelEndpointId != null && !workItemCamelEndpointId.isEmpty()) {
                logger.debug(
                        "The Camel Endpoint ID has been set on both the WorkItemHanlder and WorkItem. The '"
                                + JBPMConstants.CAMEL_ENDPOINT_ID_WI_PARAM
                                + "' configured on the WorkItem overrides the global configuation.");
            } else {
                workItemCamelEndpointId = camelEndpointId;
            }
        }

        if (workItemCamelEndpointId == null || workItemCamelEndpointId.isEmpty()) {
            throw new IllegalArgumentException(
                    "No Camel Endpoint ID specified. Please configure the '" + JBPMConstants.CAMEL_ENDPOINT_ID_WI_PARAM
                            + "' in either the constructor of this WorkItemHandler, or pass it via the "
                            + JBPMConstants.CAMEL_ENDPOINT_ID_WI_PARAM + "' WorkItem parameter.");
        }
        return workItemCamelEndpointId;
    }

    protected abstract void handleResponse(Exchange responseExchange, WorkItem workItem, WorkItemManager manager);

    protected abstract Exchange buildExchange(ProducerTemplate template, WorkItem workItem);

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