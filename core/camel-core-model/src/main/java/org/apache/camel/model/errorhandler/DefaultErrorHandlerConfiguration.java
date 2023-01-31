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
package org.apache.camel.model.errorhandler;

import java.util.concurrent.ScheduledExecutorService;

import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.processor.errorhandler.RedeliveryPolicy;
import org.apache.camel.spi.CamelLogger;

/**
 * Legacy error handler for XML DSL in camel-spring-xml/camel-blueprint
 */
@XmlTransient
@Deprecated
public class DefaultErrorHandlerConfiguration implements DefaultErrorHandlerProperties {

    private CamelLogger logger;
    private RedeliveryPolicy redeliveryPolicy;
    private Processor onRedelivery;
    private String onRedeliveryRef;
    private Predicate retryWhile;
    private String retryWhileRef;
    private String deadLetterUri;
    private boolean deadLetterHandleNewException = true;
    private boolean useOriginalMessage;
    private boolean useOriginalBody;
    private boolean asyncDelayedRedelivery;
    private ScheduledExecutorService executorService;
    private String executorServiceRef;
    private Processor onPrepareFailure;
    private String onPrepareFailureRef;
    private Processor onExceptionOccurred;
    private String onExceptionOccurredRef;

    @Override
    public boolean hasLogger() {
        return logger != null;
    }

    public CamelLogger getLogger() {
        return logger;
    }

    public void setLogger(CamelLogger logger) {
        this.logger = logger;
    }

    @Override
    public RedeliveryPolicy getDefaultRedeliveryPolicy() {
        return RedeliveryPolicy.DEFAULT_POLICY;
    }

    @Override
    public boolean hasRedeliveryPolicy() {
        return redeliveryPolicy != null;
    }

    public RedeliveryPolicy getRedeliveryPolicy() {
        return redeliveryPolicy;
    }

    public void setRedeliveryPolicy(RedeliveryPolicy redeliveryPolicy) {
        this.redeliveryPolicy = redeliveryPolicy;
    }

    public Processor getOnRedelivery() {
        return onRedelivery;
    }

    public void setOnRedelivery(Processor onRedelivery) {
        this.onRedelivery = onRedelivery;
    }

    public String getOnRedeliveryRef() {
        return onRedeliveryRef;
    }

    public void setOnRedeliveryRef(String onRedeliveryRef) {
        this.onRedeliveryRef = onRedeliveryRef;
    }

    public Predicate getRetryWhile() {
        return retryWhile;
    }

    public void setRetryWhile(Predicate retryWhile) {
        this.retryWhile = retryWhile;
    }

    public String getRetryWhileRef() {
        return retryWhileRef;
    }

    public void setRetryWhileRef(String retryWhileRef) {
        this.retryWhileRef = retryWhileRef;
    }

    public String getDeadLetterUri() {
        return deadLetterUri;
    }

    public void setDeadLetterUri(String deadLetterUri) {
        this.deadLetterUri = deadLetterUri;
    }

    public boolean isDeadLetterHandleNewException() {
        return deadLetterHandleNewException;
    }

    public void setDeadLetterHandleNewException(boolean deadLetterHandleNewException) {
        this.deadLetterHandleNewException = deadLetterHandleNewException;
    }

    public boolean isUseOriginalMessage() {
        return useOriginalMessage;
    }

    public void setUseOriginalMessage(boolean useOriginalMessage) {
        this.useOriginalMessage = useOriginalMessage;
    }

    public boolean isUseOriginalBody() {
        return useOriginalBody;
    }

    public void setUseOriginalBody(boolean useOriginalBody) {
        this.useOriginalBody = useOriginalBody;
    }

    public boolean isAsyncDelayedRedelivery() {
        return asyncDelayedRedelivery;
    }

    public void setAsyncDelayedRedelivery(boolean asyncDelayedRedelivery) {
        this.asyncDelayedRedelivery = asyncDelayedRedelivery;
    }

    public ScheduledExecutorService getExecutorService() {
        return executorService;
    }

    public void setExecutorService(ScheduledExecutorService executorService) {
        this.executorService = executorService;
    }

    public String getExecutorServiceRef() {
        return executorServiceRef;
    }

    public void setExecutorServiceRef(String executorServiceRef) {
        this.executorServiceRef = executorServiceRef;
    }

    public Processor getOnPrepareFailure() {
        return onPrepareFailure;
    }

    public void setOnPrepareFailure(Processor onPrepareFailure) {
        this.onPrepareFailure = onPrepareFailure;
    }

    public String getOnPrepareFailureRef() {
        return onPrepareFailureRef;
    }

    public void setOnPrepareFailureRef(String onPrepareFailureRef) {
        this.onPrepareFailureRef = onPrepareFailureRef;
    }

    public Processor getOnExceptionOccurred() {
        return onExceptionOccurred;
    }

    public void setOnExceptionOccurred(Processor onExceptionOccurred) {
        this.onExceptionOccurred = onExceptionOccurred;
    }

    public String getOnExceptionOccurredRef() {
        return onExceptionOccurredRef;
    }

    public void setOnExceptionOccurredRef(String onExceptionOccurredRef) {
        this.onExceptionOccurredRef = onExceptionOccurredRef;
    }

    @Override
    public boolean supportTransacted() {
        return false;
    }

    @Override
    public ErrorHandlerFactory cloneBuilder() {
        return null;
    }
}
