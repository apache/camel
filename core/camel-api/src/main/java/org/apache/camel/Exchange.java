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
package org.apache.camel;

import java.util.Map;

import org.apache.camel.clock.Clock;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UnitOfWork;
import org.apache.camel.spi.annotations.ConstantProvider;

/**
 * An Exchange is the message container holding the information during the entire routing of a {@link Message} received
 * by a {@link Consumer}.
 * <p/>
 * During processing down the {@link Processor} chain, the {@link Exchange} provides access to the current (not the
 * original) request and response {@link Message} messages. The {@link Exchange} also holds meta-data during its entire
 * lifetime stored as properties accessible using the various {@link #getProperty(String)} methods. The
 * {@link #setProperty(String, Object)} is used to store a property. For example you can use this to store security, SLA
 * related data or any other information deemed useful throughout processing. If an {@link Exchange} failed during
 * routing the {@link Exception} that caused the failure is stored and accessible via the {@link #getException()}
 * method.
 * <p/>
 * An Exchange is created when a {@link Consumer} receives a request. A new {@link Message} is created, the request is
 * set as the body of the {@link Message} and depending on the {@link Consumer} other {@link Endpoint} and protocol
 * related information is added as headers on the {@link Message}. Then an Exchange is created and the newly created
 * {@link Message} is set as the in on the Exchange. Therefore an Exchange starts its life in a {@link Consumer}. The
 * Exchange is then sent down the {@link Route} for processing along a {@link Processor} chain. The {@link Processor} as
 * the name suggests is what processes the {@link Message} in the Exchange and Camel, in addition to providing
 * out-of-the-box a large number of useful processors, it also allows you to create your own. The rule Camel uses is to
 * take the out {@link Message} produced by the previous {@link Processor} and set it as the in for the next
 * {@link Processor}. If the previous {@link Processor} did not produce an out, then the in of the previous
 * {@link Processor} is sent as the next in. At the end of the processing chain, depending on the {@link ExchangePattern
 * Message Exchange Pattern} (or MEP) the last out (or in of no out available) is sent by the {@link Consumer} back to
 * the original caller.
 * <p/>
 * Camel, in addition to providing out-of-the-box a large number of useful processors, it also allows you to implement
 * and use your own. When the Exchange is passed to a {@link Processor}, it always contains an in {@link Message} and no
 * out {@link Message}. The {@link Processor} <b>may</b> produce an out, depending on the nature of the
 * {@link Processor}. The in {@link Message} can be accessed using the {@link #getIn()} method. Since the out message is
 * null when entering the {@link Processor}, the {@link #getOut()} method is actually a convenient factory method that
 * will lazily instantiate a {@link org.apache.camel.support.DefaultMessage} which you could populate. As an alternative
 * you could also instantiate your specialized {@link Message} and set it on the exchange using the
 * {@link #setOut(org.apache.camel.Message)} method. Please note that a {@link Message} contains not only the body but
 * also headers and attachments. If you are creating a new {@link Message} the headers and attachments of the in
 * {@link Message} are not automatically copied to the out by Camel and you'll have to set the headers and attachments
 * you need yourself. If your {@link Processor} is not producing a different {@link Message} but only needs to slightly
 * modify the in, you can simply update the in {@link Message} returned by {@link #getIn()}.
 * <p/>
 * See this <a href="http://camel.apache.org/using-getin-or-getout-methods-on-exchange.html">FAQ entry</a> for more
 * details.
 */
@ConstantProvider("org.apache.camel.ExchangeConstantProvider")
public interface Exchange extends VariableAware {

    String AUTHENTICATION = "CamelAuthentication";
    String AUTHENTICATION_FAILURE_POLICY_ID = "CamelAuthenticationFailurePolicyId";
    @Deprecated
    String ACCEPT_CONTENT_TYPE = "CamelAcceptContentType";
    @Metadata(label = "aggregate", description = "Number of exchanges that was grouped together.", javaType = "int")
    String AGGREGATED_SIZE = "CamelAggregatedSize";
    @Metadata(label = "aggregate", description = "The time in millis this group will timeout", javaType = "long")
    String AGGREGATED_TIMEOUT = "CamelAggregatedTimeout";
    @Metadata(label = "aggregate", description = "Enum that tell how this group was completed",
              enums = "consumer,force,interval,predicate,size,strategy,timeout", javaType = "String")
    String AGGREGATED_COMPLETED_BY = "CamelAggregatedCompletedBy";
    @Metadata(label = "aggregate", description = "The correlation key for this aggregation group", javaType = "String")
    String AGGREGATED_CORRELATION_KEY = "CamelAggregatedCorrelationKey";
    String AGGREGATED_COLLECTION_GUARD = "CamelAggregatedCollectionGuard";
    String AGGREGATION_STRATEGY = "CamelAggregationStrategy";
    @Metadata(label = "consumer,aggregate",
              description = "Input property. Set to true to force completing the current group. This allows to overrule any existing completion predicates, sizes, timeouts etc, and complete the group.",
              javaType = "boolean")
    String AGGREGATION_COMPLETE_CURRENT_GROUP = "CamelAggregationCompleteCurrentGroup";
    @Metadata(label = "consumer,aggregate",
              description = "Input property. Set to true to force completing all the groups (excluding this message). This allows to overrule any existing completion predicates, sizes, timeouts etc, and complete the group."
                            + " This message is considered a signal message only, the message headers/contents will not be processed otherwise. Instead use CamelAggregationCompleteAllGroupsInclusive if this message should be included in the aggregator.",
              javaType = "boolean")
    String AGGREGATION_COMPLETE_ALL_GROUPS = "CamelAggregationCompleteAllGroups";
    @Metadata(label = "consumer,aggregate",
              description = "Input property. Set to true to force completing all the groups (including this message). This allows to overrule any existing completion predicates, sizes, timeouts etc, and complete the group.",
              javaType = "boolean")
    String AGGREGATION_COMPLETE_ALL_GROUPS_INCLUSIVE = "CamelAggregationCompleteAllGroupsInclusive";
    String ASYNC_WAIT = "CamelAsyncWait";

    String BATCH_INDEX = "CamelBatchIndex";
    String BATCH_SIZE = "CamelBatchSize";
    String BATCH_COMPLETE = "CamelBatchComplete";
    String BEAN_METHOD_NAME = "CamelBeanMethodName";
    String BINDING = "CamelBinding";
    // do not prefix with Camel and use lower-case starting letter as its a shared key
    // used across other Apache products such as AMQ, SMX etc.
    String BREADCRUMB_ID = "breadcrumbId";

    String CHARSET_NAME = "CamelCharsetName";
    @Deprecated
    String CIRCUIT_BREAKER_STATE = "CamelCircuitBreakerState";
    @Deprecated
    String CREATED_TIMESTAMP = "CamelCreatedTimestamp";
    String CLAIM_CHECK_REPOSITORY = "CamelClaimCheckRepository";
    String CONTENT_ENCODING = "Content-Encoding";
    String CONTENT_LENGTH = "Content-Length";
    String CONTENT_TYPE = "Content-Type";
    String COOKIE_HANDLER = "CamelCookieHandler";
    String CORRELATION_ID = "CamelCorrelationId";

    // The schema of the message payload
    String CONTENT_SCHEMA = "CamelContentSchema";
    // The schema type of the message payload (json schema, avro, etc)
    String CONTENT_SCHEMA_TYPE = "CamelContentSchemaType";

    String DATASET_INDEX = "CamelDataSetIndex";
    String DEFAULT_CHARSET_PROPERTY = "org.apache.camel.default.charset";
    String DESTINATION_OVERRIDE_URL = "CamelDestinationOverrideUrl";
    String DISABLE_HTTP_STREAM_CACHE = "CamelDisableHttpStreamCache";
    @Metadata(label = "idempotentConsumer",
              description = "Whether this exchange is a duplicate detected by the Idempotent Consumer EIP",
              javaType = "boolean")
    String DUPLICATE_MESSAGE = "CamelDuplicateMessage";

    String DOCUMENT_BUILDER_FACTORY = "CamelDocumentBuilderFactory";

    @Metadata(label = "doCatch,doFinally,errorHandler,onException",
              description = "Stores the caught exception due to a processing error of the current Exchange",
              javaType = "java.lang.Exception")
    String EXCEPTION_CAUGHT = "CamelExceptionCaught";
    String EXCEPTION_HANDLED = "CamelExceptionHandled";
    String EVALUATE_EXPRESSION_RESULT = "CamelEvaluateExpressionResult";
    String ERRORHANDLER_BRIDGE = "CamelErrorHandlerBridge";
    String ERRORHANDLER_CIRCUIT_DETECTED = "CamelErrorHandlerCircuitDetected";
    @Deprecated
    String ERRORHANDLER_HANDLED = "CamelErrorHandlerHandled";
    @Deprecated
    String EXTERNAL_REDELIVERED = "CamelExternalRedelivered";

    @Deprecated
    String FAILURE_HANDLED = "CamelFailureHandled";

    @Metadata(label = "doCatch,doFinally,errorHandler,onException",
              description = "Endpoint URI where the Exchange failed during processing",
              javaType = "String")
    String FAILURE_ENDPOINT = "CamelFailureEndpoint";
    @Metadata(label = "doCatch,doFinally,errorHandler,onException",
              description = "Route ID where the Exchange failed during processing",
              javaType = "String")
    String FAILURE_ROUTE_ID = "CamelFailureRouteId";
    String FATAL_FALLBACK_ERROR_HANDLER = "CamelFatalFallbackErrorHandler";
    String FILE_CONTENT_TYPE = "CamelFileContentType";
    String FILE_LOCAL_WORK_PATH = "CamelFileLocalWorkPath";
    String FILE_NAME = "CamelFileName";
    String FILE_NAME_ONLY = "CamelFileNameOnly";
    String FILE_NAME_PRODUCED = "CamelFileNameProduced";
    String FILE_NAME_CONSUMED = "CamelFileNameConsumed";
    String FILE_PATH = "CamelFilePath";
    String FILE_PARENT = "CamelFileParent";
    String FILE_LAST_MODIFIED = "CamelFileLastModified";
    String FILE_LENGTH = "CamelFileLength";
    String FILE_LOCK_FILE_ACQUIRED = "CamelFileLockFileAcquired";
    String FILE_LOCK_FILE_NAME = "CamelFileLockFileName";
    String FILE_LOCK_EXCLUSIVE_LOCK = "CamelFileLockExclusiveLock";
    String FILE_LOCK_RANDOM_ACCESS_FILE = "CamelFileLockRandomAccessFile";
    String FILE_LOCK_CHANNEL_FILE = "CamelFileLockChannelFile";
    @Deprecated
    String FILTER_MATCHED = "CamelFilterMatched";
    String FILTER_NON_XML_CHARS = "CamelFilterNonXmlChars";

    String GROUPED_EXCHANGE = "CamelGroupedExchange";

    String HTTP_SCHEME = "CamelHttpScheme";
    String HTTP_HOST = "CamelHttpHost";
    String HTTP_PORT = "CamelHttpPort";
    String HTTP_BASE_URI = "CamelHttpBaseUri";
    String HTTP_CHARACTER_ENCODING = "CamelHttpCharacterEncoding";
    String HTTP_METHOD = "CamelHttpMethod";
    String HTTP_PATH = "CamelHttpPath";
    String HTTP_PROTOCOL_VERSION = "CamelHttpProtocolVersion";
    String HTTP_QUERY = "CamelHttpQuery";
    String HTTP_RAW_QUERY = "CamelHttpRawQuery";
    String HTTP_RESPONSE_CODE = "CamelHttpResponseCode";
    String HTTP_RESPONSE_TEXT = "CamelHttpResponseText";
    String HTTP_URI = "CamelHttpUri";
    String HTTP_URL = "CamelHttpUrl";
    String HTTP_CHUNKED = "CamelHttpChunked";
    String HTTP_SERVLET_REQUEST = "CamelHttpServletRequest";
    String HTTP_SERVLET_RESPONSE = "CamelHttpServletResponse";

    @Metadata(label = "interceptFrom,interceptSendToEndpoint", description = "The endpoint URI that was intercepted",
              javaType = "String")
    String INTERCEPTED_ENDPOINT = "CamelInterceptedEndpoint";
    String INTERCEPT_SEND_TO_ENDPOINT_WHEN_MATCHED = "CamelInterceptSendToEndpointWhenMatched";
    @Deprecated
    String INTERRUPTED = "CamelInterrupted";

    String LANGUAGE_SCRIPT = "CamelLanguageScript";
    String LOG_DEBUG_BODY_MAX_CHARS = "CamelLogDebugBodyMaxChars";
    String LOG_DEBUG_BODY_STREAMS = "CamelLogDebugStreams";
    String LOG_EIP_NAME = "CamelLogEipName";
    @Metadata(label = "loop", description = "Index of the current iteration (0 based).", javaType = "int")
    String LOOP_INDEX = "CamelLoopIndex";
    @Metadata(label = "loop",
              description = "Total number of loops. This is not available if running the loop in while loop mode.",
              javaType = "int")
    String LOOP_SIZE = "CamelLoopSize";

    // Long running action (saga): using "Long-Running-Action" as header value allows sagas
    // to be propagated to any remote system supporting the LRA framework
    String SAGA_LONG_RUNNING_ACTION = "Long-Running-Action";

    String MAXIMUM_CACHE_POOL_SIZE = "CamelMaximumCachePoolSize";
    String MAXIMUM_ENDPOINT_CACHE_SIZE = "CamelMaximumEndpointCacheSize";
    String MAXIMUM_SIMPLE_CACHE_SIZE = "CamelMaximumSimpleCacheSize";
    String MAXIMUM_TRANSFORMER_CACHE_SIZE = "CamelMaximumTransformerCacheSize";
    String MAXIMUM_VALIDATOR_CACHE_SIZE = "CamelMaximumValidatorCacheSize";
    String MESSAGE_HISTORY = "CamelMessageHistory";
    String MESSAGE_HISTORY_HEADER_FORMAT = "CamelMessageHistoryHeaderFormat";
    String MESSAGE_HISTORY_OUTPUT_FORMAT = "CamelMessageHistoryOutputFormat";
    String MESSAGE_TIMESTAMP = "CamelMessageTimestamp";
    @Metadata(label = "multicast",
              description = "An index counter that increases for each Exchange being multicasted. The counter starts from 0.",
              javaType = "int")
    String MULTICAST_INDEX = "CamelMulticastIndex";
    @Metadata(label = "multicast", description = "Whether this Exchange is the last.", javaType = "boolean")
    String MULTICAST_COMPLETE = "CamelMulticastComplete";

    @Deprecated
    String NOTIFY_EVENT = "CamelNotifyEvent";

    @Metadata(label = "onCompletion",
              description = "Flag to mark that this exchange is currently being executed as onCompletion", javaType = "boolean")
    String ON_COMPLETION = "CamelOnCompletion";
    String ON_COMPLETION_ROUTE_IDS = "CamelOnCompletionRouteIds";
    String OFFSET = "CamelOffset";
    String OVERRULE_FILE_NAME = "CamelOverruleFileName";

    String PARENT_UNIT_OF_WORK = "CamelParentUnitOfWork";
    String STREAM_CACHE_UNIT_OF_WORK = "CamelStreamCacheUnitOfWork";

    @Metadata(label = "recipientList", description = "The endpoint uri of this recipient list", javaType = "String")
    String RECIPIENT_LIST_ENDPOINT = "CamelRecipientListEndpoint";
    String RECEIVED_TIMESTAMP = "CamelReceivedTimestamp";
    String REDELIVERED = "CamelRedelivered";
    String REDELIVERY_COUNTER = "CamelRedeliveryCounter";
    String REDELIVERY_MAX_COUNTER = "CamelRedeliveryMaxCounter";
    @Deprecated
    String REDELIVERY_EXHAUSTED = "CamelRedeliveryExhausted";
    String REDELIVERY_DELAY = "CamelRedeliveryDelay";
    String REST_HTTP_URI = "CamelRestHttpUri";
    String REST_HTTP_QUERY = "CamelRestHttpQuery";
    @Deprecated
    String ROLLBACK_ONLY = "CamelRollbackOnly";
    @Deprecated
    String ROLLBACK_ONLY_LAST = "CamelRollbackOnlyLast";
    @Deprecated
    String ROUTE_STOP = "CamelRouteStop";

    String REUSE_SCRIPT_ENGINE = "CamelReuseScripteEngine";
    String COMPILE_SCRIPT = "CamelCompileScript";

    @Deprecated
    String SAXPARSER_FACTORY = "CamelSAXParserFactory";

    String SCHEDULER_POLLED_MESSAGES = "CamelSchedulerPolledMessages";
    @Deprecated
    String SOAP_ACTION = "CamelSoapAction";
    String SKIP_GZIP_ENCODING = "CamelSkipGzipEncoding";
    String SKIP_WWW_FORM_URLENCODED = "CamelSkipWwwFormUrlEncoding";
    @Metadata(label = "routingSlip", description = "The endpoint uri of this routing slip", javaType = "String")
    String SLIP_ENDPOINT = "CamelSlipEndpoint";
    String SLIP_PRODUCER = "CamelSlipProducer";
    @Metadata(label = "split",
              description = "A split counter that increases for each Exchange being split. The counter starts from 0.",
              javaType = "int")
    String SPLIT_INDEX = "CamelSplitIndex";
    @Metadata(label = "split", description = "Whether this Exchange is the last.", javaType = "boolean")
    String SPLIT_COMPLETE = "CamelSplitComplete";
    @Metadata(label = "split",
              description = "The total number of Exchanges that was split. This property is not applied for stream based splitting, except for the very last message because then Camel knows the total size.",
              javaType = "int")
    String SPLIT_SIZE = "CamelSplitSize";
    @Metadata(label = "step", description = "The id of the Step EIP", javaType = "String")
    String STEP_ID = "CamelStepId";

    String TIMER_COUNTER = "CamelTimerCounter";
    String TIMER_FIRED_TIME = "CamelTimerFiredTime";
    String TIMER_NAME = "CamelTimerName";
    String TIMER_PERIOD = "CamelTimerPeriod";
    String TIMER_TIME = "CamelTimerTime";

    @Metadata(label = "enrich,multicast,pollEnrich,recipientList,routingSlip,toD,to,wireTap",
              description = "Endpoint URI where this Exchange is being sent to", javaType = "String")
    String TO_ENDPOINT = "CamelToEndpoint";
    @Deprecated
    String TRACE_EVENT = "CamelTraceEvent";
    @Deprecated
    String TRACE_EVENT_NODE_ID = "CamelTraceEventNodeId";
    @Deprecated
    String TRACE_EVENT_TIMESTAMP = "CamelTraceEventTimestamp";
    @Deprecated
    String TRACE_EVENT_EXCHANGE = "CamelTraceEventExchange";
    @Deprecated
    String TRACING_HEADER_FORMAT = "CamelTracingHeaderFormat";
    @Deprecated
    String TRACING_OUTPUT_FORMAT = "CamelTracingOutputFormat";
    String TRANSACTION_CONTEXT_DATA = "CamelTransactionContextData";
    String TRY_ROUTE_BLOCK = "TryRouteBlock";
    String TRANSFER_ENCODING = "Transfer-Encoding";

    String UNIT_OF_WORK_EXHAUSTED = "CamelUnitOfWorkExhausted";

    String XSLT_FILE_NAME = "CamelXsltFileName";
    String XSLT_ERROR = "CamelXsltError";
    String XSLT_FATAL_ERROR = "CamelXsltFatalError";
    String XSLT_WARNING = "CamelXsltWarning";

    /**
     * Returns the {@link ExchangePattern} (MEP) of this exchange.
     *
     * @return the message exchange pattern of this exchange
     */
    ExchangePattern getPattern();

    /**
     * Allows the {@link ExchangePattern} (MEP) of this exchange to be customized.
     *
     * This typically won't be required as an exchange can be created with a specific MEP by calling
     * {@link Endpoint#createExchange(ExchangePattern)} but it is here just in case it is needed.
     *
     * @param pattern the pattern
     */
    void setPattern(ExchangePattern pattern);

    /**
     * Returns a property associated with this exchange by the key
     *
     * @param  key the exchange key
     * @return     the value of the given property or <tt>null</tt> if there is no property for the given key
     */
    Object getProperty(ExchangePropertyKey key);

    /**
     * Returns a property associated with this exchange by the key and specifying the type required
     *
     * @param  key  the exchange key
     * @param  type the type of the property
     * @return      the value of the given property or <tt>null</tt> if there is no property for the given name or
     *              <tt>null</tt> if it cannot be converted to the given type
     */
    <T> T getProperty(ExchangePropertyKey key, Class<T> type);

    /**
     * Returns a property associated with this exchange by name and specifying the type required
     *
     * @param  key          the exchange key
     * @param  defaultValue the default value to return if property was absent
     * @param  type         the type of the property
     * @return              the value of the given property or <tt>defaultValue</tt> if there is no property for the
     *                      given name or <tt>null</tt> if it cannot be converted to the given type
     */
    <T> T getProperty(ExchangePropertyKey key, Object defaultValue, Class<T> type);

    /**
     * Sets a property on the exchange
     *
     * @param key   the exchange key
     * @param value to associate with the name
     */
    void setProperty(ExchangePropertyKey key, Object value);

    /**
     * Removes the given property on the exchange
     *
     * @param  key the exchange key
     * @return     the old value of the property
     */
    Object removeProperty(ExchangePropertyKey key);

    /**
     * Returns a property associated with this exchange by name
     *
     * @param  name the name of the property
     * @return      the value of the given property or <tt>null</tt> if there is no property for the given name
     */
    Object getProperty(String name);

    /**
     * Returns a property associated with this exchange by name and specifying the type required
     *
     * @param  name the name of the property
     * @param  type the type of the property
     * @return      the value of the given property or <tt>null</tt> if there is no property for the given name or
     *              <tt>null</tt> if it cannot be converted to the given type
     */
    <T> T getProperty(String name, Class<T> type);

    /**
     * Returns a property associated with this exchange by name and specifying the type required
     *
     * @param  name         the name of the property
     * @param  defaultValue the default value to return if property was absent
     * @param  type         the type of the property
     * @return              the value of the given property or <tt>defaultValue</tt> if there is no property for the
     *                      given name or <tt>null</tt> if it cannot be converted to the given type
     */
    <T> T getProperty(String name, Object defaultValue, Class<T> type);

    /**
     * Sets a property on the exchange
     *
     * @param name  of the property
     * @param value to associate with the name
     */
    void setProperty(String name, Object value);

    /**
     * Removes the given property on the exchange
     *
     * @param  name of the property
     * @return      the old value of the property
     */
    Object removeProperty(String name);

    /**
     * Remove all the properties associated with the exchange matching a specific pattern
     *
     * @param  pattern pattern of names
     * @return         boolean whether any properties matched
     */
    boolean removeProperties(String pattern);

    /**
     * Removes the properties from this exchange that match the given <tt>pattern</tt>, except for the ones matching one
     * or more <tt>excludePatterns</tt>
     *
     * @param  pattern         pattern of names that should be removed
     * @param  excludePatterns one or more pattern of properties names that should be excluded (= preserved)
     * @return                 boolean whether any properties matched
     */
    boolean removeProperties(String pattern, String... excludePatterns);

    /**
     * Returns the properties associated with the exchange
     *
     * @return the properties in a Map
     * @see    #getAllProperties()
     */
    Map<String, Object> getProperties();

    /**
     * Returns all (both internal and custom) properties associated with the exchange
     *
     * @return all (both internal and custom) properties in a Map
     * @see    #getProperties()
     */
    Map<String, Object> getAllProperties();

    /**
     * Returns whether any properties have been set
     *
     * @return <tt>true</tt> if any properties has been set
     */
    boolean hasProperties();

    /**
     * Returns a variable by name
     *
     * @param  name the name of the variable
     * @return      the value of the given variable or <tt>null</tt> if there is no variable for the given name
     */
    Object getVariable(String name);

    /**
     * Returns a variable by name and specifying the type required
     *
     * @param  name the name of the variable
     * @param  type the type of the variable
     * @return      the value of the given variable or <tt>null</tt> if there is no variable for the given name or
     *              <tt>null</tt> if it cannot be converted to the given type
     */
    <T> T getVariable(String name, Class<T> type);

    /**
     * Returns a variable by name and specifying the type required
     *
     * @param  name         the name of the variable
     * @param  defaultValue the default value to return if variable was absent
     * @param  type         the type of the variable
     * @return              the value of the given variable or <tt>defaultValue</tt> if there is no variable for the
     *                      given name or <tt>null</tt> if it cannot be converted to the given type
     */
    <T> T getVariable(String name, Object defaultValue, Class<T> type);

    /**
     * Sets a variable on the exchange
     *
     * @param name  of the variable
     * @param value the value of the variable
     */
    void setVariable(String name, Object value);

    /**
     * Removes the given variable
     *
     * @param  name of the variable, or use * to remove all variables
     * @return      the old value of the variable, or <tt>null</tt> if there was no variable for the given name
     */
    Object removeVariable(String name);

    /**
     * Returns the variables
     *
     * @return the variables in a Map.
     */
    Map<String, Object> getVariables();

    /**
     * Returns whether any variables have been set
     *
     * @return <tt>true</tt> if any variables has been set
     */
    boolean hasVariables();

    /**
     * Returns the inbound request message
     *
     * @return the message
     */
    Message getIn();

    /**
     * Returns the current message
     *
     * @return the current message
     */
    Message getMessage();

    /**
     * Returns the current message as the given type
     *
     * @param  type the given type
     * @return      the message as the given type or <tt>null</tt> if not possible to covert to given type
     */
    <T> T getMessage(Class<T> type);

    /**
     * Replace the current message instance.
     *
     * @param message the new message
     */
    void setMessage(Message message);

    /**
     * Returns the inbound request message as the given type
     *
     * @param  type the given type
     * @return      the message as the given type or <tt>null</tt> if not possible to covert to given type
     */
    <T> T getIn(Class<T> type);

    /**
     * Sets the inbound message instance
     *
     * @param in the inbound message
     */
    void setIn(Message in);

    /**
     * Returns the outbound message, lazily creating one if one has not already been associated with this exchange.
     * <p/>
     * <br/>
     * <b>Important:</b> If you want to change the current message, then use {@link #getIn()} instead as it will ensure
     * headers etc. is kept and propagated when routing continues. Bottom line end users should rarely use this method.
     * <p/>
     * <br/>
     * If you want to test whether an OUT message have been set or not, use the {@link #hasOut()} method.
     * <p/>
     * See also the class java doc for this {@link Exchange} for more details and this
     * <a href="http://camel.apache.org/using-getin-or-getout-methods-on-exchange.html">FAQ entry</a>.
     *
     * @return     the response
     * @see        #getIn()
     * @deprecated use {@link #getMessage()}
     */
    @Deprecated
    Message getOut();

    /**
     * Returns the outbound request message as the given type
     * <p/>
     * <br/>
     * <b>Important:</b> If you want to change the current message, then use {@link #getIn()} instead as it will ensure
     * headers etc. is kept and propagated when routing continues. Bottom line end users should rarely use this method.
     * <p/>
     * <br/>
     * If you want to test whether an OUT message have been set or not, use the {@link #hasOut()} method.
     * <p/>
     * See also the class java doc for this {@link Exchange} for more details and this
     * <a href="http://camel.apache.org/using-getin-or-getout-methods-on-exchange.html">FAQ entry</a>.
     *
     * @param      type the given type
     * @return          the message as the given type or <tt>null</tt> if not possible to covert to given type
     * @see             #getIn(Class)
     * @deprecated      use {@link #getMessage(Class)}
     */
    @Deprecated
    <T> T getOut(Class<T> type);

    /**
     * Returns whether an OUT message has been set or not.
     *
     * @return     <tt>true</tt> if an OUT message exists, <tt>false</tt> otherwise.
     * @deprecated use {@link #getMessage()}
     */
    @Deprecated
    boolean hasOut();

    /**
     * Sets the outbound message
     *
     * @param      out the outbound message
     * @deprecated     use {@link #setMessage(Message)}
     */
    @Deprecated
    void setOut(Message out);

    /**
     * Returns the exception associated with this exchange
     *
     * @return the exception (or null if no faults)
     */
    Exception getException();

    /**
     * Returns the exception associated with this exchange.
     * <p/>
     * Is used to get the caused exception that typically have been wrapped in some sort of Camel wrapper exception
     * <p/>
     * The strategy is to look in the exception hierarchy to find the first given cause that matches the type. Will
     * start from the bottom (the real cause) and walk upwards.
     *
     * @param  type the exception type
     * @return      the exception (or <tt>null</tt> if no caused exception matched)
     */
    <T> T getException(Class<T> type);

    /**
     * Sets the exception associated with this exchange
     * <p/>
     * Camel will wrap {@link Throwable} into {@link Exception} type to accommodate for the {@link #getException()}
     * method returning a plain {@link Exception} type.
     *
     * @param t the caused exception
     */
    void setException(Throwable t);

    /**
     * Returns true if this exchange failed due to an exception
     *
     * @return true if this exchange failed due to an exception
     * @see    Exchange#getException()
     */
    boolean isFailed();

    /**
     * Returns true if this exchange is transacted
     */
    boolean isTransacted();

    /**
     * Returns true if this exchange is marked to stop and not continue routing.
     */
    boolean isRouteStop();

    /**
     * Sets whether this exchange is marked to stop and not continue routing.
     *
     * @param routeStop <tt>true</tt> to stop routing
     */
    void setRouteStop(boolean routeStop);

    /**
     * Returns true if this exchange is an external initiated redelivered message (such as a JMS broker).
     * <p/>
     * <b>Important: </b> It is not always possible to determine if the message is a redelivery or not, and therefore
     * <tt>false</tt> is returned. Such an example would be a JDBC message. However JMS brokers provides details if a
     * message is redelivered.
     *
     * @return <tt>true</tt> if redelivered, <tt>false</tt> if not or not able to determine
     */
    boolean isExternalRedelivered();

    /**
     * Returns true if this exchange is marked for rollback
     */
    boolean isRollbackOnly();

    /**
     * Sets whether to mark this exchange for rollback
     */
    void setRollbackOnly(boolean rollbackOnly);

    /**
     * Returns true if this exchange is marked for rollback (only last transaction section)
     */
    boolean isRollbackOnlyLast();

    /**
     * Sets whether to mark this exchange for rollback (only last transaction section)
     */
    void setRollbackOnlyLast(boolean rollbackOnlyLast);

    /**
     * Returns the container so that a processor can resolve endpoints from URIs
     *
     * @return the container which owns this exchange
     */
    CamelContext getContext();

    /**
     * Creates a copy of the current message exchange so that it can be forwarded to another destination
     */
    Exchange copy();

    /**
     * Returns the endpoint which originated this message exchange if a consumer on an endpoint created the message
     * exchange, otherwise his property will be <tt>null</tt>.
     *
     * Note: In case this message exchange has been cloned through another parent message exchange (which itself has
     * been created through the consumer of it's own endpoint), then if desired one could still retrieve the consumer
     * endpoint of such a parent message exchange as the following:
     *
     * <pre>
     * getContext().getRoute(getFromRouteId()).getEndpoint()
     * </pre>
     */
    Endpoint getFromEndpoint();

    /**
     * Returns the route id which originated this message exchange if a route consumer on an endpoint created the
     * message exchange, otherwise his property will be <tt>null</tt>.
     *
     * Note: In case this message exchange has been cloned through another parent message exchange then this method
     * would return the <tt>fromRouteId<tt> property of that exchange.
     */
    String getFromRouteId();

    /**
     * Returns the unit of work that this exchange belongs to; which may map to zero, one or more physical transactions
     */
    UnitOfWork getUnitOfWork();

    /**
     * Returns the exchange id (unique)
     */
    String getExchangeId();

    /**
     * Set the exchange id
     */
    void setExchangeId(String id);

    /**
     * Gets the timestamp in millis when this exchange was created.
     *
     * @see Message#getMessageTimestamp()
     */
    @Deprecated
    long getCreated();

    /**
     * Gets the {@link ExchangeExtension} that contains the extension points for internal exchange APIs. These APIs are
     * intended for internal usage within Camel and end-users should avoid using them.
     *
     * @return the {@link ExchangeExtension} point for this exchange.
     */
    ExchangeExtension getExchangeExtension();

    /**
     * Gets {@link Clock} that holds time information about the exchange
     */
    Clock getClock();

}
