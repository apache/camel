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
package org.apache.camel.blueprint;

import java.util.Set;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.CamelContext;
import org.apache.camel.Processor;
import org.apache.camel.builder.DefaultErrorHandlerBuilder;
import org.apache.camel.builder.ErrorHandlerBuilder;
import org.apache.camel.core.xml.AbstractCamelFactoryBean;
import org.apache.camel.model.RedeliveryPolicyDefinition;
import org.apache.camel.processor.errorhandler.RedeliveryPolicy;
import org.apache.camel.reifier.errorhandler.ErrorHandlerReifier;
import org.osgi.service.blueprint.container.BlueprintContainer;

@XmlRootElement(name = "errorHandler")
@XmlAccessorType(XmlAccessType.FIELD)
public class CamelErrorHandlerFactoryBean extends AbstractCamelFactoryBean<ErrorHandlerBuilder> {

    @XmlAttribute
    private ErrorHandlerType type = ErrorHandlerType.DefaultErrorHandler;
    @XmlAttribute
    private String deadLetterUri;
    @XmlAttribute
    private Boolean deadLetterHandleNewException;
    @XmlAttribute
    private Boolean useOriginalMessage;
    @XmlAttribute
    private Boolean useOriginalBody;
    @XmlAttribute
    private String onRedeliveryRef;
    @XmlAttribute
    private String onPrepareFailureRef;
    @XmlAttribute
    private String onExceptionOccurredRef;
    @XmlAttribute
    private String retryWhileRef;
    @XmlAttribute
    private String executorServiceRef;
    @XmlAttribute
    private String redeliveryPolicyRef;
    @XmlElement
    private RedeliveryPolicyDefinition redeliveryPolicy;
    @XmlTransient
    private BlueprintContainer blueprintContainer;

    @Override
    public ErrorHandlerBuilder getObject() throws Exception {
        ErrorHandlerBuilder errorHandler = getObjectType().newInstance();
        if (errorHandler instanceof DefaultErrorHandlerBuilder) {
            DefaultErrorHandlerBuilder handler = (DefaultErrorHandlerBuilder) errorHandler;
            if (deadLetterUri != null) {
                handler.setDeadLetterUri(deadLetterUri);
            }
            if (deadLetterHandleNewException != null) {
                handler.setDeadLetterHandleNewException(deadLetterHandleNewException);
            }
            if (useOriginalMessage != null) {
                handler.setUseOriginalMessage(useOriginalMessage);
            }
            if (useOriginalBody != null) {
                handler.setUseOriginalBody(useOriginalBody);
            }
            if (redeliveryPolicy != null) {
                handler.setRedeliveryPolicy(ErrorHandlerReifier.createRedeliveryPolicy(redeliveryPolicy, getCamelContext(), null));
            }
            if (redeliveryPolicyRef != null) {
                handler.setRedeliveryPolicy(lookup(redeliveryPolicyRef, RedeliveryPolicy.class));
            }
            if (onRedeliveryRef != null) {
                handler.setOnRedelivery(lookup(onRedeliveryRef, Processor.class));
            }
            if (onPrepareFailureRef != null) {
                handler.setOnPrepareFailure(lookup(onPrepareFailureRef, Processor.class));
            }
            if (onExceptionOccurredRef != null) {
                handler.setOnExceptionOccurred(lookup(onExceptionOccurredRef, Processor.class));
            }
            if (retryWhileRef != null) {
                handler.setRetryWhileRef(retryWhileRef);
            }
            if (executorServiceRef != null) {
                handler.setExecutorServiceRef(executorServiceRef);
            }
        }
        return errorHandler;
    }

    @Override
    public Class<? extends ErrorHandlerBuilder> getObjectType() {
        return type.getTypeAsClass();
    }

    public void setBlueprintContainer(BlueprintContainer blueprintContainer) {
        this.blueprintContainer = blueprintContainer;
    }

    @Override
    protected CamelContext getCamelContextWithId(String camelContextId) {
        if (blueprintContainer != null) {
            return (CamelContext) blueprintContainer.getComponentInstance(camelContextId);
        }
        return null;
    }

    @Override
    protected CamelContext discoverDefaultCamelContext() {
        if (blueprintContainer != null) {
            Set<String> ids = BlueprintCamelContextLookupHelper.lookupBlueprintCamelContext(blueprintContainer);
            if (ids.size() == 1) {
                // there is only 1 id for a BlueprintCamelContext so fallback and use this
                return getCamelContextWithId(ids.iterator().next());
            }
        }
        return null;
    }

    protected <T> T lookup(String name, Class<T> type) {
        return type.cast(blueprintContainer.getComponentInstance(name));
    }

}
