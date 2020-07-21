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
package org.apache.camel.component.undertow;

import io.undertow.util.HttpString;

/**
 * Copy of {@link org.apache.camel.Exchange} headers fields, to return them as {@link HttpString} for Undertow
 */
public final class ExchangeHeaders {

    public static final HttpString AUTHENTICATION = new HttpString("CamelAuthentication");
    public static final HttpString AUTHENTICATION_FAILURE_POLICY_ID = new HttpString("CamelAuthenticationFailurePolicyId");
    public static final HttpString ACCEPT_CONTENT_TYPE = new HttpString("CamelAcceptContentType");
    public static final HttpString AGGREGATED_SIZE = new HttpString("CamelAggregatedSize");
    public static final HttpString AGGREGATED_TIMEOUT = new HttpString("CamelAggregatedTimeout");
    public static final HttpString AGGREGATED_COMPLETED_BY = new HttpString("CamelAggregatedCompletedBy");
    public static final HttpString AGGREGATED_CORRELATION_KEY = new HttpString("CamelAggregatedCorrelationKey");
    public static final HttpString AGGREGATION_STRATEGY = new HttpString("CamelAggregationStrategy");
    public static final HttpString AGGREGATION_COMPLETE_ALL_GROUPS = new HttpString("CamelAggregationCompleteAllGroups");
    public static final HttpString AGGREGATION_COMPLETE_ALL_GROUPS_INCLUSIVE = new HttpString("CamelAggregationCompleteAllGroupsInclusive");
    public static final HttpString ASYNC_WAIT = new HttpString("CamelAsyncWait");

    public static final HttpString BATCH_INDEX = new HttpString("CamelBatchIndex");
    public static final HttpString BATCH_SIZE = new HttpString("CamelBatchSize");
    public static final HttpString BATCH_COMPLETE = new HttpString("CamelBatchComplete");
    public static final HttpString BEAN_METHOD_NAME = new HttpString("CamelBeanMethodName");
    public static final HttpString BEAN_MULTI_PARAMETER_ARRAY = new HttpString("CamelBeanMultiParameterArray");
    public static final HttpString BINDING = new HttpString("CamelBinding");
    // do not prefix with Camel and use lower-case starting letter as its a shared key
    // used across other Apache products such as AMQ, SMX etc.
    public static final HttpString BREADCRUMB_ID = new HttpString("breadcrumbId");

    public static final HttpString CHARSET_NAME = new HttpString("CamelCharsetName");
    public static final HttpString CREATED_TIMESTAMP = new HttpString("CamelCreatedTimestamp");
    public static final HttpString CONTENT_ENCODING = new HttpString("Content-Encoding");
    public static final HttpString CONTENT_LENGTH = new HttpString("Content-Length");
    public static final HttpString CONTENT_TYPE = new HttpString("Content-Type");
    public static final HttpString CORRELATION_ID = new HttpString("CamelCorrelationId");

    public static final HttpString DATASET_INDEX = new HttpString("CamelDataSetIndex");
    public static final HttpString DEFAULT_CHARSET_PROPERTY = new HttpString("org.apache.camel.default.charset");
    public static final HttpString DESTINATION_OVERRIDE_URL = new HttpString("CamelDestinationOverrideUrl");
    public static final HttpString DISABLE_HTTP_STREAM_CACHE = new HttpString("CamelDisableHttpStreamCache");
    public static final HttpString DUPLICATE_MESSAGE = new HttpString("CamelDuplicateMessage");

    public static final HttpString DOCUMENT_BUILDER_FACTORY = new HttpString("CamelDocumentBuilderFactory");

    public static final HttpString EXCEPTION_CAUGHT = new HttpString("CamelExceptionCaught");
    public static final HttpString EXCEPTION_HANDLED = new HttpString("CamelExceptionHandled");
    public static final HttpString EVALUATE_EXPRESSION_RESULT = new HttpString("CamelEvaluateExpressionResult");
    public static final HttpString ERRORHANDLER_HANDLED = new HttpString("CamelErrorHandlerHandled");
    @Deprecated
    public static final HttpString EXTERNAL_REDELIVERED = new HttpString("CamelExternalRedelivered");

    public static final HttpString FAILURE_HANDLED = new HttpString("CamelFailureHandled");
    public static final HttpString FAILURE_ENDPOINT = new HttpString("CamelFailureEndpoint");
    public static final HttpString FAILURE_ROUTE_ID = new HttpString("CamelFailureRouteId");
    public static final HttpString FILTER_NON_XML_CHARS = new HttpString("CamelFilterNonXmlChars");
    public static final HttpString FILE_LOCAL_WORK_PATH = new HttpString("CamelFileLocalWorkPath");
    public static final HttpString FILE_NAME = new HttpString("CamelFileName");
    public static final HttpString FILE_NAME_ONLY = new HttpString("CamelFileNameOnly");
    public static final HttpString FILE_NAME_PRODUCED = new HttpString("CamelFileNameProduced");
    public static final HttpString FILE_NAME_CONSUMED = new HttpString("CamelFileNameConsumed");
    public static final HttpString FILE_PATH = new HttpString("CamelFilePath");
    public static final HttpString FILE_PARENT = new HttpString("CamelFileParent");
    public static final HttpString FILE_LAST_MODIFIED = new HttpString("CamelFileLastModified");
    public static final HttpString FILE_LENGTH = new HttpString("CamelFileLength");
    public static final HttpString FILTER_MATCHED = new HttpString("CamelFilterMatched");
    public static final HttpString FILE_LOCK_FILE_ACQUIRED = new HttpString("CamelFileLockFileAcquired");
    public static final HttpString FILE_LOCK_FILE_NAME = new HttpString("CamelFileLockFileName");

    public static final HttpString GROUPED_EXCHANGE = new HttpString("CamelGroupedExchange");

    public static final HttpString HTTP_BASE_URI = new HttpString("CamelHttpBaseUri");
    public static final HttpString HTTP_CHARACTER_ENCODING = new HttpString("CamelHttpCharacterEncoding");
    public static final HttpString HTTP_METHOD = new HttpString("CamelHttpMethod");
    public static final HttpString HTTP_PATH = new HttpString("CamelHttpPath");
    public static final HttpString HTTP_PROTOCOL_VERSION = new HttpString("CamelHttpProtocolVersion");
    public static final HttpString HTTP_QUERY = new HttpString("CamelHttpQuery");
    public static final HttpString HTTP_RAW_QUERY = new HttpString("CamelHttpRawQuery");
    public static final HttpString HTTP_RESPONSE_CODE = new HttpString("CamelHttpResponseCode");
    public static final HttpString HTTP_URI = new HttpString("CamelHttpUri");
    public static final HttpString HTTP_URL = new HttpString("CamelHttpUrl");
    public static final HttpString HTTP_CHUNKED = new HttpString("CamelHttpChunked");
    public static final HttpString HTTP_SERVLET_REQUEST = new HttpString("CamelHttpServletRequest");
    public static final HttpString HTTP_SERVLET_RESPONSE = new HttpString("CamelHttpServletResponse");

    public static final HttpString INTERCEPTED_ENDPOINT = new HttpString("CamelInterceptedEndpoint");
    public static final HttpString INTERCEPT_SEND_TO_ENDPOINT_WHEN_MATCHED = new HttpString("CamelInterceptSendToEndpointWhenMatched");

    public static final HttpString LANGUAGE_SCRIPT = new HttpString("CamelLanguageScript");
    public static final HttpString LOG_DEBUG_BODY_MAX_CHARS = new HttpString("CamelLogDebugBodyMaxChars");
    public static final HttpString LOG_DEBUG_BODY_STREAMS = new HttpString("CamelLogDebugStreams");
    public static final HttpString LOOP_INDEX = new HttpString("CamelLoopIndex");
    public static final HttpString LOOP_SIZE = new HttpString("CamelLoopSize");

    public static final HttpString MAXIMUM_CACHE_POOL_SIZE = new HttpString("CamelMaximumCachePoolSize");
    public static final HttpString MAXIMUM_ENDPOINT_CACHE_SIZE = new HttpString("CamelMaximumEndpointCacheSize");
    public static final HttpString MESSAGE_HISTORY = new HttpString("CamelMessageHistory");
    public static final HttpString MULTICAST_INDEX = new HttpString("CamelMulticastIndex");
    public static final HttpString MULTICAST_COMPLETE = new HttpString("CamelMulticastComplete");

    public static final HttpString ON_COMPLETION = new HttpString("CamelOnCompletion");
    public static final HttpString OVERRULE_FILE_NAME = new HttpString("CamelOverruleFileName");

    public static final HttpString PARENT_UNIT_OF_WORK = new HttpString("CamelParentUnitOfWork");

    public static final HttpString RECIPIENT_LIST_ENDPOINT = new HttpString("CamelRecipientListEndpoint");
    public static final HttpString RECEIVED_TIMESTAMP = new HttpString("CamelReceivedTimestamp");
    public static final HttpString REDELIVERED = new HttpString("CamelRedelivered");
    public static final HttpString REDELIVERY_COUNTER = new HttpString("CamelRedeliveryCounter");
    public static final HttpString REDELIVERY_MAX_COUNTER = new HttpString("CamelRedeliveryMaxCounter");
    public static final HttpString REDELIVERY_EXHAUSTED = new HttpString("CamelRedeliveryExhausted");
    public static final HttpString REDELIVERY_DELAY = new HttpString("CamelRedeliveryDelay");
    public static final HttpString ROLLBACK_ONLY = new HttpString("CamelRollbackOnly");
    public static final HttpString ROLLBACK_ONLY_LAST = new HttpString("CamelRollbackOnlyLast");
    public static final HttpString ROUTE_STOP = new HttpString("CamelRouteStop");

    public static final HttpString SAXPARSER_FACTORY = new HttpString("CamelSAXParserFactory");

    public static final HttpString SOAP_ACTION = new HttpString("CamelSoapAction");
    public static final HttpString SKIP_GZIP_ENCODING = new HttpString("CamelSkipGzipEncoding");
    public static final HttpString SKIP_WWW_FORM_URLENCODED = new HttpString("CamelSkipWwwFormUrlEncoding");
    public static final HttpString SLIP_ENDPOINT = new HttpString("CamelSlipEndpoint");
    public static final HttpString SPLIT_INDEX = new HttpString("CamelSplitIndex");
    public static final HttpString SPLIT_COMPLETE = new HttpString("CamelSplitComplete");
    public static final HttpString SPLIT_SIZE = new HttpString("CamelSplitSize");

    public static final HttpString TIMER_COUNTER = new HttpString("CamelTimerCounter");
    public static final HttpString TIMER_FIRED_TIME = new HttpString("CamelTimerFiredTime");
    public static final HttpString TIMER_NAME = new HttpString("CamelTimerName");
    public static final HttpString TIMER_PERIOD = new HttpString("CamelTimerPeriod");
    public static final HttpString TIMER_TIME = new HttpString("CamelTimerTime");
    public static final HttpString TO_ENDPOINT = new HttpString("CamelToEndpoint");
    public static final HttpString TRACE_EVENT = new HttpString("CamelTraceEvent");
    public static final HttpString TRACE_EVENT_NODE_ID = new HttpString("CamelTraceEventNodeId");
    public static final HttpString TRACE_EVENT_TIMESTAMP = new HttpString("CamelTraceEventTimestamp");
    public static final HttpString TRACE_EVENT_EXCHANGE = new HttpString("CamelTraceEventExchange");
    public static final HttpString TRY_ROUTE_BLOCK = new HttpString("TryRouteBlock");
    public static final HttpString TRANSFER_ENCODING = new HttpString("Transfer-Encoding");

    public static final HttpString UNIT_OF_WORK_EXHAUSTED = new HttpString("CamelUnitOfWorkExhausted");

    /**
     * @deprecated UNIT_OF_WORK_PROCESS_SYNC is not in use and will be removed in future Camel release
     */
    @Deprecated
    public static final HttpString UNIT_OF_WORK_PROCESS_SYNC = new HttpString("CamelUnitOfWorkProcessSync");

    public static final HttpString XSLT_FILE_NAME = new HttpString("CamelXsltFileName");
    public static final HttpString XSLT_ERROR = new HttpString("CamelXsltError");
    public static final HttpString XSLT_FATAL_ERROR = new HttpString("CamelXsltFatalError");
    public static final HttpString XSLT_WARNING = new HttpString("CamelXsltWarning");

    private ExchangeHeaders() {
    }

}
