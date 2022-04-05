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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.LoggingLevel;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.builder.ErrorHandlerBuilder;
import org.apache.camel.model.RedeliveryPolicyDefinition;
import org.apache.camel.processor.errorhandler.RedeliveryPolicy;
import org.apache.camel.spi.CamelLogger;
import org.apache.camel.spi.Metadata;

/**
 * Default error handler.
 */
@Metadata(label = "configuration,error")
@XmlRootElement(name = "defaultErrorHandler")
@XmlAccessorType(XmlAccessType.FIELD)
public class DefaultErrorHandlerDefinition extends BaseErrorHandlerDefinition implements ErrorHandlerBuilder {

    // TODO: fluent builders
    // TODO: label, java type, ref

    @XmlTransient
    private CamelLogger loggerBean;
    @XmlTransient
    private Processor onRedeliveryProcessor;
    @XmlTransient
    private Processor onPrepareFailureProcessor;
    @XmlTransient
    private Processor onExceptionOccurredProcessor;
    @XmlTransient
    private ScheduledExecutorService executorServiceBean;
    @XmlTransient
    private Predicate retryWhilePredicate;

    @XmlAttribute
    private String loggerRef;
    @XmlAttribute
    @Metadata(defaultValue = "ERROR")
    private LoggingLevel level;
    @XmlAttribute
    @Metadata(defaultValue = "WARN")
    private LoggingLevel rollbackLoggingLevel;
    @XmlAttribute
    private String logName;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String useOriginalMessage;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String useOriginalBody;
    @XmlAttribute
    private String onRedeliveryRef;
    @XmlAttribute
    private String onExceptionOccurredRef;
    @XmlAttribute
    private String onPrepareFailureRef;
    @XmlAttribute
    private String retryWhileRef;
    @XmlAttribute
    private String redeliveryPolicyRef;
    @XmlAttribute
    private String executorServiceRef;
    @XmlElement
    private RedeliveryPolicyDefinition redeliveryPolicy;

    @Override
    public boolean supportTransacted() {
        return false;
    }

    @Override
    public ErrorHandlerBuilder cloneBuilder() {
        DefaultErrorHandlerDefinition answer = new DefaultErrorHandlerDefinition();
        cloneBuilder(answer);
        return answer;
    }

    protected void cloneBuilder(DefaultErrorHandlerDefinition other) {
        other.setExecutorServiceBean(getExecutorServiceBean());
        other.setExecutorServiceRef(getExecutorServiceRef());
        other.setLevel(getLevel());
        other.setLogName(getLogName());
        other.setLoggerBean(getLoggerBean());
        other.setLoggerRef(getLoggerRef());
        other.setOnExceptionOccurredProcessor(getOnExceptionOccurredProcessor());
        other.setOnExceptionOccurredRef(getOnExceptionOccurredRef());
        other.setOnPrepareFailureProcessor(getOnPrepareFailureProcessor());
        other.setOnPrepareFailureRef(getOnPrepareFailureRef());
        other.setOnRedeliveryProcessor(getOnRedeliveryProcessor());
        other.setOnRedeliveryRef(getOnRedeliveryRef());
        other.setRedeliveryPolicyRef(getRedeliveryPolicyRef());
        other.setRetryWhilePredicate(getRetryWhilePredicate());
        other.setRetryWhileRef(getRetryWhileRef());
        other.setRollbackLoggingLevel(getRollbackLoggingLevel());
        other.setUseOriginalBody(getUseOriginalBody());
        other.setUseOriginalMessage(getUseOriginalMessage());
        if (getRedeliveryPolicy() != null) {
            other.setRedeliveryPolicy(getRedeliveryPolicy().copy());
        }
    }

    public String getLoggerRef() {
        return loggerRef;
    }

    /**
     * References to a logger to use as logger for the error handler
     */
    public void setLoggerRef(String loggerRef) {
        this.loggerRef = loggerRef;
    }

    public CamelLogger getLoggerBean() {
        return loggerBean;
    }

    public void setLoggerBean(CamelLogger loggerBean) {
        this.loggerBean = loggerBean;
    }

    public LoggingLevel getLevel() {
        return level;
    }

    /**
     * Logging level to use when using the logging error handler type.
     */
    public void setLevel(LoggingLevel level) {
        this.level = level;
    }

    public LoggingLevel getRollbackLoggingLevel() {
        return rollbackLoggingLevel;
    }

    /**
     * Sets the logging level to use for logging transactional rollback.
     * <p/>
     * This option is default WARN.
     */
    public void setRollbackLoggingLevel(LoggingLevel rollbackLoggingLevel) {
        this.rollbackLoggingLevel = rollbackLoggingLevel;
    }

    public String getLogName() {
        return logName;
    }

    /**
     * Name of the logger to use for the logging error handler
     */
    public void setLogName(String logName) {
        this.logName = logName;
    }

    public String getUseOriginalMessage() {
        return useOriginalMessage;
    }

    /**
     * Will use the original input {@link org.apache.camel.Message} (original body and headers) when an
     * {@link org.apache.camel.Exchange} is moved to the dead letter queue.
     * <p/>
     * <b>Notice:</b> this only applies when all redeliveries attempt have failed and the
     * {@link org.apache.camel.Exchange} is doomed for failure. <br/>
     * Instead of using the current inprogress {@link org.apache.camel.Exchange} IN message we use the original IN
     * message instead. This allows you to store the original input in the dead letter queue instead of the inprogress
     * snapshot of the IN message. For instance if you route transform the IN body during routing and then failed. With
     * the original exchange store in the dead letter queue it might be easier to manually re submit the
     * {@link org.apache.camel.Exchange} again as the IN message is the same as when Camel received it. So you should be
     * able to send the {@link org.apache.camel.Exchange} to the same input.
     * <p/>
     * The difference between useOriginalMessage and useOriginalBody is that the former includes both the original body
     * and headers, where as the latter only includes the original body. You can use the latter to enrich the message
     * with custom headers and include the original message body. The former wont let you do this, as its using the
     * original message body and headers as they are. You cannot enable both useOriginalMessage and useOriginalBody.
     * <p/>
     * <b>Important:</b> The original input means the input message that are bounded by the current
     * {@link org.apache.camel.spi.UnitOfWork}. An unit of work typically spans one route, or multiple routes if they
     * are connected using internal endpoints such as direct or seda. When messages is passed via external endpoints
     * such as JMS or HTTP then the consumer will create a new unit of work, with the message it received as input as
     * the original input. Also some EIP patterns such as splitter, multicast, will create a new unit of work boundary
     * for the messages in their sub-route (eg the splitted message); however these EIPs have an option named
     * <tt>shareUnitOfWork</tt> which allows to combine with the parent unit of work in regard to error handling and
     * therefore use the parent original message.
     * <p/>
     * By default this feature is off.
     */
    public void setUseOriginalMessage(String useOriginalMessage) {
        this.useOriginalMessage = useOriginalMessage;
    }

    public String getUseOriginalBody() {
        return useOriginalBody;
    }

    /**
     * Will use the original input {@link org.apache.camel.Message} body (original body only) when an
     * {@link org.apache.camel.Exchange} is moved to the dead letter queue.
     * <p/>
     * <b>Notice:</b> this only applies when all redeliveries attempt have failed and the
     * {@link org.apache.camel.Exchange} is doomed for failure. <br/>
     * Instead of using the current inprogress {@link org.apache.camel.Exchange} IN message we use the original IN
     * message instead. This allows you to store the original input in the dead letter queue instead of the inprogress
     * snapshot of the IN message. For instance if you route transform the IN body during routing and then failed. With
     * the original exchange store in the dead letter queue it might be easier to manually re submit the
     * {@link org.apache.camel.Exchange} again as the IN message is the same as when Camel received it. So you should be
     * able to send the {@link org.apache.camel.Exchange} to the same input.
     * <p/>
     * The difference between useOriginalMessage and useOriginalBody is that the former includes both the original body
     * and headers, where as the latter only includes the original body. You can use the latter to enrich the message
     * with custom headers and include the original message body. The former wont let you do this, as its using the
     * original message body and headers as they are. You cannot enable both useOriginalMessage and useOriginalBody.
     * <p/>
     * <b>Important:</b> The original input means the input message that are bounded by the current
     * {@link org.apache.camel.spi.UnitOfWork}. An unit of work typically spans one route, or multiple routes if they
     * are connected using internal endpoints such as direct or seda. When messages is passed via external endpoints
     * such as JMS or HTTP then the consumer will create a new unit of work, with the message it received as input as
     * the original input. Also some EIP patterns such as splitter, multicast, will create a new unit of work boundary
     * for the messages in their sub-route (eg the splitted message); however these EIPs have an option named
     * <tt>shareUnitOfWork</tt> which allows to combine with the parent unit of work in regard to error handling and
     * therefore use the parent original message.
     * <p/>
     * By default this feature is off.
     */
    public void setUseOriginalBody(String useOriginalBody) {
        this.useOriginalBody = useOriginalBody;
    }

    public String getOnRedeliveryRef() {
        return onRedeliveryRef;
    }

    /**
     * Sets a reference to a processor that should be processed <b>before</b> a redelivery attempt.
     * <p/>
     * Can be used to change the {@link org.apache.camel.Exchange} <b>before</b> its being redelivered.
     */
    public void setOnRedeliveryRef(String onRedeliveryRef) {
        this.onRedeliveryRef = onRedeliveryRef;
    }

    public Processor getOnRedeliveryProcessor() {
        return onRedeliveryProcessor;
    }

    /**
     * Sets a processor that should be processed <b>before</b> a redelivery attempt.
     * <p/>
     * Can be used to change the {@link org.apache.camel.Exchange} <b>before</b> its being redelivered.
     */
    public void setOnRedeliveryProcessor(Processor onRedeliveryProcessor) {
        this.onRedeliveryProcessor = onRedeliveryProcessor;
    }

    public String getOnExceptionOccurredRef() {
        return onExceptionOccurredRef;
    }

    /**
     * Sets a reference to a processor that should be processed <b>just after</b> an exception occurred. Can be used to
     * perform custom logging about the occurred exception at the exact time it happened.
     * <p/>
     * Important: Any exception thrown from this processor will be ignored.
     */
    public void setOnExceptionOccurredRef(String onExceptionOccurredRef) {
        this.onExceptionOccurredRef = onExceptionOccurredRef;
    }

    public Processor getOnExceptionOccurredProcessor() {
        return onExceptionOccurredProcessor;
    }

    /**
     * Sets a processor that should be processed <b>just after</b> an exception occurred. Can be used to perform custom
     * logging about the occurred exception at the exact time it happened.
     * <p/>
     * Important: Any exception thrown from this processor will be ignored.
     */
    public void setOnExceptionOccurredProcessor(Processor onExceptionOccurredProcessor) {
        this.onExceptionOccurredProcessor = onExceptionOccurredProcessor;
    }

    public String getOnPrepareFailureRef() {
        return onPrepareFailureRef;
    }

    /**
     * Sets a reference to a processor to prepare the {@link org.apache.camel.Exchange} before handled by the failure
     * processor / dead letter channel. This allows for example to enrich the message before sending to a dead letter
     * queue.
     */
    public void setOnPrepareFailureRef(String onPrepareFailureRef) {
        this.onPrepareFailureRef = onPrepareFailureRef;
    }

    public Processor getOnPrepareFailureProcessor() {
        return onPrepareFailureProcessor;
    }

    /**
     * Sets a processor to prepare the {@link org.apache.camel.Exchange} before handled by the failure processor / dead
     * letter channel. This allows for example to enrich the message before sending to a dead letter queue.
     */
    public void setOnPrepareFailureProcessor(Processor onPrepareFailureProcessor) {
        this.onPrepareFailureProcessor = onPrepareFailureProcessor;
    }

    public String getRetryWhileRef() {
        return retryWhileRef;
    }

    /**
     * Sets a retry while predicate.
     *
     * Will continue retrying until the predicate evaluates to false.
     */
    public void setRetryWhileRef(String retryWhileRef) {
        this.retryWhileRef = retryWhileRef;
    }

    public String getRedeliveryPolicyRef() {
        return redeliveryPolicyRef;
    }

    /**
     * Sets a reference to a {@link RedeliveryPolicy} to be used for redelivery settings.
     */
    public void setRedeliveryPolicyRef(String redeliveryPolicyRef) {
        this.redeliveryPolicyRef = redeliveryPolicyRef;
    }

    public String getExecutorServiceRef() {
        return executorServiceRef;
    }

    /**
     * Sets a reference to a thread pool to be used by the error handler
     */
    public void setExecutorServiceRef(String executorServiceRef) {
        this.executorServiceRef = executorServiceRef;
    }

    public ScheduledExecutorService getExecutorServiceBean() {
        return executorServiceBean;
    }

    /**
     * Sets a thread pool to be used by the error handler
     */
    public void setExecutorServiceBean(ScheduledExecutorService executorServiceBean) {
        this.executorServiceBean = executorServiceBean;
    }

    public Predicate getRetryWhilePredicate() {
        return retryWhilePredicate;
    }

    /**
     * Sets a retry while predicate.
     *
     * Will continue retrying until the predicate evaluates to false.
     */
    public void setRetryWhilePredicate(Predicate retryWhilePredicate) {
        this.retryWhilePredicate = retryWhilePredicate;
    }

    public RedeliveryPolicyDefinition getRedeliveryPolicy() {
        return redeliveryPolicy;
    }

    /**
     * Sets the redelivery settings
     */
    public void setRedeliveryPolicy(RedeliveryPolicyDefinition redeliveryPolicy) {
        this.redeliveryPolicy = redeliveryPolicy;
    }
}
