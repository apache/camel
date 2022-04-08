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

import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.processor.errorhandler.RedeliveryPolicy;
import org.apache.camel.spi.CamelLogger;

/**
 * Legacy error handler for XML DSL in camel-spring-xml/camel-blueprint
 */
@Deprecated
public interface DefaultErrorHandlerProperties extends ErrorHandlerFactory {

    boolean hasLogger();

    CamelLogger getLogger();

    void setLogger(CamelLogger logger);

    boolean hasRedeliveryPolicy();

    RedeliveryPolicy getRedeliveryPolicy();

    RedeliveryPolicy getDefaultRedeliveryPolicy();

    void setRedeliveryPolicy(RedeliveryPolicy redeliveryPolicy);

    Processor getOnRedelivery();

    void setOnRedelivery(Processor onRedelivery);

    String getOnRedeliveryRef();

    void setOnRedeliveryRef(String onRedeliveryRef);

    Predicate getRetryWhile();

    void setRetryWhile(Predicate retryWhile);

    String getRetryWhileRef();

    void setRetryWhileRef(String retryWhileRef);

    String getDeadLetterUri();

    void setDeadLetterUri(String deadLetterUri);

    boolean isDeadLetterHandleNewException();

    void setDeadLetterHandleNewException(boolean deadLetterHandleNewException);

    boolean isUseOriginalMessage();

    void setUseOriginalMessage(boolean useOriginalMessage);

    boolean isUseOriginalBody();

    void setUseOriginalBody(boolean useOriginalBody);

    boolean isAsyncDelayedRedelivery();

    void setAsyncDelayedRedelivery(boolean asyncDelayedRedelivery);

    ScheduledExecutorService getExecutorService();

    void setExecutorService(ScheduledExecutorService executorService);

    String getExecutorServiceRef();

    void setExecutorServiceRef(String executorServiceRef);

    Processor getOnPrepareFailure();

    void setOnPrepareFailure(Processor onPrepareFailure);

    String getOnPrepareFailureRef();

    void setOnPrepareFailureRef(String onPrepareFailureRef);

    Processor getOnExceptionOccurred();

    void setOnExceptionOccurred(Processor onExceptionOccurred);

    String getOnExceptionOccurredRef();

    void setOnExceptionOccurredRef(String onExceptionOccurredRef);
}
