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
package org.apache.camel.util;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.MessageHistory;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.NoSuchHeaderException;
import org.apache.camel.NoSuchPropertyException;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.TypeConversionException;
import org.apache.camel.TypeConverter;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.MessageSupport;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.spi.UnitOfWork;

/**
 * Some helper methods for working with {@link Exchange} objects
 *
 * @version
 */
public final class ExchangeHelper {

    /**
     * Utility classes should not have a public constructor.
     */
    private ExchangeHelper() {
    }

    /**
     * Extracts the Exchange.BINDING of the given type or null if not present
     *
     * @param exchange the message exchange
     * @param type     the expected binding type
     * @return the binding object of the given type or null if it could not be found or converted
     */
    public static <T> T getBinding(Exchange exchange, Class<T> type) {
        return exchange != null ? exchange.getProperty(Exchange.BINDING, type) : null;
    }

    /**
     * Attempts to resolve the endpoint for the given value
     *
     * @param exchange the message exchange being processed
     * @param value    the value which can be an {@link Endpoint} or an object
     *                 which provides a String representation of an endpoint via
     *                 {@link #toString()}
     * @return the endpoint
     * @throws NoSuchEndpointException if the endpoint cannot be resolved
     */
    public static Endpoint resolveEndpoint(Exchange exchange, Object value) throws NoSuchEndpointException {
        Endpoint endpoint;
        if (value instanceof Endpoint) {
            endpoint = (Endpoint) value;
        } else {
            String uri = value.toString().trim();
            endpoint = CamelContextHelper.getMandatoryEndpoint(exchange.getContext(), uri);
        }
        return endpoint;
    }

    /**
     * Gets the mandatory property of the exchange of the correct type
     *
     * @param exchange      the exchange
     * @param propertyName  the property name
     * @param type          the type
     * @return the property value
     * @throws TypeConversionException is thrown if error during type conversion
     * @throws NoSuchPropertyException is thrown if no property exists
     */
    public static <T> T getMandatoryProperty(Exchange exchange, String propertyName, Class<T> type) throws NoSuchPropertyException {
        T result = exchange.getProperty(propertyName, type);
        if (result != null) {
            return result;
        }
        throw new NoSuchPropertyException(exchange, propertyName, type);
    }

    /**
     * Gets the mandatory inbound header of the correct type
     *
     * @param exchange      the exchange
     * @param headerName    the header name
     * @param type          the type
     * @return the header value
     * @throws TypeConversionException is thrown if error during type conversion
     * @throws NoSuchHeaderException is thrown if no headers exists
     */
    public static <T> T getMandatoryHeader(Exchange exchange, String headerName, Class<T> type) throws TypeConversionException, NoSuchHeaderException {
        T answer = exchange.getIn().getHeader(headerName, type);
        if (answer == null) {
            throw new NoSuchHeaderException(exchange, headerName, type);
        }
        return answer;
    }

    /**
     * Gets the mandatory inbound header of the correct type
     *
     * @param message       the message
     * @param headerName    the header name
     * @param type          the type
     * @return the header value
     * @throws TypeConversionException is thrown if error during type conversion
     * @throws NoSuchHeaderException is thrown if no headers exists
     */
    public static <T> T getMandatoryHeader(Message message, String headerName, Class<T> type) throws TypeConversionException, NoSuchHeaderException {
        T answer = message.getHeader(headerName, type);
        if (answer == null) {
            throw new NoSuchHeaderException(message.getExchange(), headerName, type);
        }
        return answer;
    }

    /**
     * Gets an header or property of the correct type
     *
     * @param exchange      the exchange
     * @param name          the name of the header or the property
     * @param type          the type
     * @return the header or property value
     * @throws TypeConversionException is thrown if error during type conversion
     * @throws NoSuchHeaderException is thrown if no headers exists
     */
    public static <T> T getHeaderOrProperty(Exchange exchange, String name, Class<T> type) throws TypeConversionException {
        T answer = exchange.getIn().getHeader(name, type);
        if (answer == null) {
            answer = exchange.getProperty(name, type);
        }
        return answer;
    }

    /**
     * Returns the mandatory inbound message body of the correct type or throws
     * an exception if it is not present
     *
     * @param exchange the exchange
     * @return the body, is never <tt>null</tt>
     * @throws InvalidPayloadException Is thrown if the body being <tt>null</tt> or wrong class type
     * @deprecated use {@link org.apache.camel.Message#getMandatoryBody()}
     */
    @Deprecated
    public static Object getMandatoryInBody(Exchange exchange) throws InvalidPayloadException {
        return exchange.getIn().getMandatoryBody();
    }

    /**
     * Returns the mandatory inbound message body of the correct type or throws
     * an exception if it is not present
     * @deprecated use {@link org.apache.camel.Message#getMandatoryBody(Class)}
     */
    @Deprecated
    public static <T> T getMandatoryInBody(Exchange exchange, Class<T> type) throws InvalidPayloadException {
        return exchange.getIn().getMandatoryBody(type);
    }

    /**
     * Returns the mandatory outbound message body of the correct type or throws
     * an exception if it is not present
     * @deprecated use {@link org.apache.camel.Message#getMandatoryBody()}
     */
    @Deprecated
    public static Object getMandatoryOutBody(Exchange exchange) throws InvalidPayloadException {
        return exchange.getOut().getMandatoryBody();
    }

    /**
     * Returns the mandatory outbound message body of the correct type or throws
     * an exception if it is not present
     * @deprecated use {@link org.apache.camel.Message#getMandatoryBody(Class)}
     */
    @Deprecated
    public static <T> T getMandatoryOutBody(Exchange exchange, Class<T> type) throws InvalidPayloadException {
        return exchange.getOut().getMandatoryBody(type);
    }

    /**
     * Converts the value to the given expected type or throws an exception
     *
     * @return the converted value
     * @throws TypeConversionException is thrown if error during type conversion
     * @throws NoTypeConversionAvailableException} if no type converters exists to convert to the given type
     */
    public static <T> T convertToMandatoryType(Exchange exchange, Class<T> type, Object value)
        throws TypeConversionException, NoTypeConversionAvailableException {
        CamelContext camelContext = exchange.getContext();
        ObjectHelper.notNull(camelContext, "CamelContext of Exchange");
        TypeConverter converter = camelContext.getTypeConverter();
        if (converter != null) {
            return converter.mandatoryConvertTo(type, exchange, value);
        }
        throw new NoTypeConversionAvailableException(value, type);
    }

    /**
     * Converts the value to the given expected type
     *
     * @return the converted value
     * @throws org.apache.camel.TypeConversionException is thrown if error during type conversion
     */
    public static <T> T convertToType(Exchange exchange, Class<T> type, Object value) throws TypeConversionException {
        CamelContext camelContext = exchange.getContext();
        ObjectHelper.notNull(camelContext, "CamelContext of Exchange");
        TypeConverter converter = camelContext.getTypeConverter();
        if (converter != null) {
            return converter.convertTo(type, exchange, value);
        }
        return null;
    }

    /**
     * Creates a new instance and copies from the current message exchange so that it can be
     * forwarded to another destination as a new instance. Unlike regular copy this operation
     * will not share the same {@link org.apache.camel.spi.UnitOfWork} so its should be used
     * for async messaging, where the original and copied exchange are independent.
     *
     * @param exchange original copy of the exchange
     * @param handover whether the on completion callbacks should be handed over to the new copy.
     */
    public static Exchange createCorrelatedCopy(Exchange exchange, boolean handover) {
        return createCorrelatedCopy(exchange, handover, false);
    }

    /**
     * Creates a new instance and copies from the current message exchange so that it can be
     * forwarded to another destination as a new instance. Unlike regular copy this operation
     * will not share the same {@link org.apache.camel.spi.UnitOfWork} so its should be used
     * for async messaging, where the original and copied exchange are independent.
     *
     * @param exchange original copy of the exchange
     * @param handover whether the on completion callbacks should be handed over to the new copy.
     * @param useSameMessageId whether to use same message id on the copy message.
     */
    public static Exchange createCorrelatedCopy(Exchange exchange, boolean handover, boolean useSameMessageId) {
        return createCorrelatedCopy(exchange, handover, useSameMessageId, null);
    }

    /**
     * Creates a new instance and copies from the current message exchange so that it can be
     * forwarded to another destination as a new instance. Unlike regular copy this operation
     * will not share the same {@link org.apache.camel.spi.UnitOfWork} so its should be used
     * for async messaging, where the original and copied exchange are independent.
     *
     * @param exchange original copy of the exchange
     * @param handover whether the on completion callbacks should be handed over to the new copy.
     * @param useSameMessageId whether to use same message id on the copy message.
     * @param filter whether to handover the on completion
     */
    public static Exchange createCorrelatedCopy(Exchange exchange, boolean handover, boolean useSameMessageId, Predicate<Synchronization> filter) {
        String id = exchange.getExchangeId();

        // make sure to do a safe copy as the correlated copy can be routed independently of the source.
        Exchange copy = exchange.copy(true);
        // do not reuse message id on copy
        if (!useSameMessageId) {
            if (copy.hasOut()) {
                copy.getOut().setMessageId(null);
            }
            copy.getIn().setMessageId(null);
        }
        // do not share the unit of work
        copy.setUnitOfWork(null);
        // do not reuse the message id
        // hand over on completion to the copy if we got any
        UnitOfWork uow = exchange.getUnitOfWork();
        if (handover && uow != null) {
            uow.handoverSynchronization(copy, filter);
        }
        // set a correlation id so we can track back the original exchange
        copy.setProperty(Exchange.CORRELATION_ID, id);
        return copy;
    }

    /**
     * Creates a new instance and copies from the current message exchange so that it can be
     * forwarded to another destination as a new instance.
     *
     * @param exchange original copy of the exchange
     * @param preserveExchangeId whether or not the exchange id should be preserved
     * @return the copy
     */
    public static Exchange createCopy(Exchange exchange, boolean preserveExchangeId) {
        Exchange copy = exchange.copy();
        if (preserveExchangeId) {
            // must preserve exchange id
            copy.setExchangeId(exchange.getExchangeId());
        }
        return copy;
    }

    /**
     * Copies the results of a message exchange from the source exchange to the result exchange
     * which will copy the message contents, exchange properties and the exception.
     * Notice the {@link ExchangePattern} is <b>not</b> copied/altered.
     *
     * @param result the result exchange which will have the output and error state added
     * @param source the source exchange which is not modified
     */
    public static void copyResults(Exchange result, Exchange source) {

        // --------------------------------------------------------------------
        //  TODO: merge logic with that of copyResultsPreservePattern()
        // --------------------------------------------------------------------

        if (result == source) {
            // we just need to ensure MEP is as expected (eg copy result to OUT if out capable)
            // and the result is not failed
            if (result.getPattern() == ExchangePattern.InOptionalOut) {
                // keep as is
            } else if (result.getPattern().isOutCapable() && !result.hasOut() && !result.isFailed()) {
                // copy IN to OUT as we expect a OUT response
                result.getOut().copyFrom(source.getIn());
            }
            return;
        }

        if (result != source) {
            result.setException(source.getException());
            if (source.hasOut()) {
                result.getOut().copyFrom(source.getOut());
            } else if (result.getPattern() == ExchangePattern.InOptionalOut) {
                // special case where the result is InOptionalOut and with no OUT response
                // so we should return null to indicate this fact
                result.setOut(null);
            } else {
                // no results so lets copy the last input
                // as the final processor on a pipeline might not
                // have created any OUT; such as a mock:endpoint
                // so lets assume the last IN is the OUT
                if (result.getPattern().isOutCapable()) {
                    // only set OUT if its OUT capable
                    result.getOut().copyFrom(source.getIn());
                } else {
                    // if not replace IN instead to keep the MEP
                    result.getIn().copyFrom(source.getIn());
                    // clear any existing OUT as the result is on the IN
                    if (result.hasOut()) {
                        result.setOut(null);
                    }
                }
            }

            if (source.hasProperties()) {
                result.getProperties().putAll(source.getProperties());
            }
        }
    }

    /**
     * Copies the <code>source</code> exchange to <code>target</code> exchange
     * preserving the {@link ExchangePattern} of <code>target</code>.
     *
     * @param source source exchange.
     * @param result target exchange.
     */
    public static void copyResultsPreservePattern(Exchange result, Exchange source) {

        // --------------------------------------------------------------------
        //  TODO: merge logic with that of copyResults()
        // --------------------------------------------------------------------

        if (result == source) {
            // we just need to ensure MEP is as expected (eg copy result to OUT if out capable)
            // and the result is not failed
            if (result.getPattern() == ExchangePattern.InOptionalOut) {
                // keep as is
            } else if (result.getPattern().isOutCapable() && !result.hasOut() && !result.isFailed()) {
                // copy IN to OUT as we expect a OUT response
                result.getOut().copyFrom(source.getIn());
            }
            return;
        }

        // copy in message
        result.getIn().copyFrom(source.getIn());

        // copy out message
        if (source.hasOut()) {
            // exchange pattern sensitive
            Message resultMessage = source.getOut().isFault() ? result.getOut() : getResultMessage(result);
            resultMessage.copyFrom(source.getOut());
        }

        // copy exception
        result.setException(source.getException());

        // copy properties
        if (source.hasProperties()) {
            result.getProperties().putAll(source.getProperties());
        }
    }

    /**
     * Returns the message where to write results in an
     * exchange-pattern-sensitive way.
     *
     * @param exchange message exchange.
     * @return result message.
     */
    public static Message getResultMessage(Exchange exchange) {
        if (exchange.getPattern().isOutCapable()) {
            return exchange.getOut();
        } else {
            return exchange.getIn();
        }
    }

    /**
     * Returns true if the given exchange pattern (if defined) can support OUT messages
     *
     * @param exchange the exchange to interrogate
     * @return true if the exchange is defined as an {@link ExchangePattern} which supports
     *         OUT messages
     */
    public static boolean isOutCapable(Exchange exchange) {
        ExchangePattern pattern = exchange.getPattern();
        return pattern != null && pattern.isOutCapable();
    }

    /**
     * Creates a new instance of the given type from the injector
     *
     * @param exchange the exchange
     * @param type     the given type
     * @return the created instance of the given type
     */
    public static <T> T newInstance(Exchange exchange, Class<T> type) {
        return exchange.getContext().getInjector().newInstance(type);
    }

    /**
     * Creates a Map of the variables which are made available to a script or template
     *
     * @param exchange the exchange to make available
     * @return a Map populated with the require variables
     */
    public static Map<String, Object> createVariableMap(Exchange exchange) {
        Map<String, Object> answer = new HashMap<String, Object>();
        populateVariableMap(exchange, answer);
        return answer;
    }

    /**
     * Populates the Map with the variables which are made available to a script or template
     *
     * @param exchange the exchange to make available
     * @param map      the map to populate
     */
    public static void populateVariableMap(Exchange exchange, Map<String, Object> map) {
        map.put("exchange", exchange);
        Message in = exchange.getIn();
        map.put("in", in);
        map.put("request", in);
        map.put("headers", in.getHeaders());
        map.put("body", in.getBody());
        if (isOutCapable(exchange)) {
            // if we are out capable then set out and response as well
            // however only grab OUT if it exists, otherwise reuse IN
            // this prevents side effects to alter the Exchange if we force creating an OUT message
            Message msg = exchange.hasOut() ? exchange.getOut() : exchange.getIn();
            map.put("out", msg);
            map.put("response", msg);
        }
        map.put("camelContext", exchange.getContext());
    }

    /**
     * Returns the MIME content type on the input message or null if one is not defined
     *
     * @param exchange the exchange
     * @return the MIME content type
     */
    public static String getContentType(Exchange exchange) {
        return MessageHelper.getContentType(exchange.getIn());
    }

    /**
     * Returns the MIME content encoding on the input message or null if one is not defined
     *
     * @param exchange the exchange
     * @return the MIME content encoding
     */
    public static String getContentEncoding(Exchange exchange) {
        return MessageHelper.getContentEncoding(exchange.getIn());
    }

    /**
     * Performs a lookup in the registry of the mandatory bean name and throws an exception if it could not be found
     *
     * @param exchange the exchange
     * @param name     the bean name
     * @return the bean
     * @throws NoSuchBeanException if no bean could be found in the registry
     */
    public static Object lookupMandatoryBean(Exchange exchange, String name) throws NoSuchBeanException {
        Object value = lookupBean(exchange, name);
        if (value == null) {
            throw new NoSuchBeanException(name);
        }
        return value;
    }

    /**
     * Performs a lookup in the registry of the mandatory bean name and throws an exception if it could not be found
     *
     * @param exchange the exchange
     * @param name     the bean name
     * @param type     the expected bean type
     * @return the bean
     * @throws NoSuchBeanException if no bean could be found in the registry
     */
    public static <T> T lookupMandatoryBean(Exchange exchange, String name, Class<T> type) {
        T value = lookupBean(exchange, name, type);
        if (value == null) {
            throw new NoSuchBeanException(name);
        }
        return value;
    }

    /**
     * Performs a lookup in the registry of the bean name
     *
     * @param exchange the exchange
     * @param name     the bean name
     * @return the bean, or <tt>null</tt> if no bean could be found
     */
    public static Object lookupBean(Exchange exchange, String name) {
        return exchange.getContext().getRegistry().lookupByName(name);
    }

    /**
     * Performs a lookup in the registry of the bean name and type
     *
     * @param exchange the exchange
     * @param name     the bean name
     * @param type     the expected bean type
     * @return the bean, or <tt>null</tt> if no bean could be found
     */
    public static <T> T lookupBean(Exchange exchange, String name, Class<T> type) {
        return exchange.getContext().getRegistry().lookupByNameAndType(name, type);
    }

    /**
     * Returns the first exchange in the given collection of exchanges which has the same exchange ID as the one given
     * or null if none could be found
     *
     * @param exchanges  the exchanges
     * @param exchangeId the exchangeId to find
     * @return matching exchange, or <tt>null</tt> if none found
     */
    public static Exchange getExchangeById(Iterable<Exchange> exchanges, String exchangeId) {
        for (Exchange exchange : exchanges) {
            String id = exchange.getExchangeId();
            if (id != null && id.equals(exchangeId)) {
                return exchange;
            }
        }
        return null;
    }

    /**
     * Prepares the exchanges for aggregation.
     * <p/>
     * This implementation will copy the OUT body to the IN body so when you do
     * aggregation the body is <b>only</b> in the IN body to avoid confusing end users.
     *
     * @param oldExchange the old exchange
     * @param newExchange the new exchange
     */
    public static void prepareAggregation(Exchange oldExchange, Exchange newExchange) {
        // move body/header from OUT to IN
        if (oldExchange != null) {
            if (oldExchange.hasOut()) {
                oldExchange.setIn(oldExchange.getOut());
                oldExchange.setOut(null);
            }
        }

        if (newExchange != null) {
            if (newExchange.hasOut()) {
                newExchange.setIn(newExchange.getOut());
                newExchange.setOut(null);
            }
        }
    }

    /**
     * Checks whether the exchange has been failure handed
     *
     * @param exchange  the exchange
     * @return <tt>true</tt> if failure handled, <tt>false</tt> otherwise
     */
    public static boolean isFailureHandled(Exchange exchange) {
        return exchange.getProperty(Exchange.FAILURE_HANDLED, false, Boolean.class);
    }

    /**
     * Checks whether the exchange {@link UnitOfWork} is exhausted
     *
     * @param exchange  the exchange
     * @return <tt>true</tt> if exhausted, <tt>false</tt> otherwise
     */
    public static boolean isUnitOfWorkExhausted(Exchange exchange) {
        return exchange.getProperty(Exchange.UNIT_OF_WORK_EXHAUSTED, false, Boolean.class);
    }

    /**
     * Sets the exchange to be failure handled.
     *
     * @param exchange  the exchange
     */
    public static void setFailureHandled(Exchange exchange) {
        exchange.setProperty(Exchange.FAILURE_HANDLED, Boolean.TRUE);
        // clear exception since its failure handled
        exchange.setException(null);
    }

    /**
     * Checks whether the exchange is redelivery exhausted
     *
     * @param exchange  the exchange
     * @return <tt>true</tt> if exhausted, <tt>false</tt> otherwise
     */
    public static boolean isRedeliveryExhausted(Exchange exchange) {
        return exchange.getProperty(Exchange.REDELIVERY_EXHAUSTED, false, Boolean.class);
    }

    /**
     * Checks whether the exchange {@link UnitOfWork} is redelivered
     *
     * @param exchange  the exchange
     * @return <tt>true</tt> if redelivered, <tt>false</tt> otherwise
     */
    public static boolean isRedelivered(Exchange exchange) {
        return exchange.getIn().hasHeaders() && exchange.getIn().getHeader(Exchange.REDELIVERED, false, Boolean.class);
    }

    /**
     * Checks whether the exchange {@link UnitOfWork} has been interrupted during processing
     *
     * @param exchange  the exchange
     * @return <tt>true</tt> if interrupted, <tt>false</tt> otherwise
     */
    public static boolean isInterrupted(Exchange exchange) {
        Object value = exchange.getProperty(Exchange.INTERRUPTED);
        return value != null && Boolean.TRUE == value;
    }

    /**
     * Check whether or not stream caching is enabled for the given route or globally.
     *
     * @param exchange  the exchange
     * @return <tt>true</tt> if enabled, <tt>false</tt> otherwise
     */
    public static boolean isStreamCachingEnabled(final Exchange exchange) {
        if (exchange.getFromRouteId() == null) {
            return exchange.getContext().getStreamCachingStrategy().isEnabled();
        } else {
            return exchange.getContext().getRoute(exchange.getFromRouteId()).getRouteContext().isStreamCaching();
        }
    }

    /**
     * Extracts the body from the given exchange.
     * <p/>
     * If the exchange pattern is provided it will try to honor it and retrieve the body
     * from either IN or OUT according to the pattern.
     *
     * @param exchange the exchange
     * @param pattern  exchange pattern if given, can be <tt>null</tt>
     * @return the result body, can be <tt>null</tt>.
     * @throws CamelExecutionException is thrown if the processing of the exchange failed
     */
    public static Object extractResultBody(Exchange exchange, ExchangePattern pattern) {
        Object answer = null;
        if (exchange != null) {
            // rethrow if there was an exception during execution
            if (exchange.getException() != null) {
                throw ObjectHelper.wrapCamelExecutionException(exchange, exchange.getException());
            }

            // result could have a fault message
            if (hasFaultMessage(exchange)) {
                Message msg = exchange.hasOut() ? exchange.getOut() : exchange.getIn();
                answer = msg.getBody();
                return answer;
            }

            // okay no fault then return the response according to the pattern
            // try to honor pattern if provided
            boolean notOut = pattern != null && !pattern.isOutCapable();
            boolean hasOut = exchange.hasOut();
            if (hasOut && !notOut) {
                // we have a response in out and the pattern is out capable
                answer = exchange.getOut().getBody();
            } else if (!hasOut && exchange.getPattern() == ExchangePattern.InOptionalOut) {
                // special case where the result is InOptionalOut and with no OUT response
                // so we should return null to indicate this fact
                answer = null;
            } else {
                // use IN as the response
                answer = exchange.getIn().getBody();
            }
        }
        return answer;
    }

    /**
     * Tests whether the exchange has a fault message set and that its not null.
     *
     * @param exchange the exchange
     * @return <tt>true</tt> if fault message exists
     */
    public static boolean hasFaultMessage(Exchange exchange) {
        Message msg = exchange.hasOut() ? exchange.getOut() : exchange.getIn();
        return msg.isFault() && msg.getBody() != null;
    }

    /**
     * Tests whether the exchange has already been handled by the error handler
     *
     * @param exchange the exchange
     * @return <tt>true</tt> if handled already by error handler, <tt>false</tt> otherwise
     */
    public static boolean hasExceptionBeenHandledByErrorHandler(Exchange exchange) {
        return Boolean.TRUE.equals(exchange.getProperty(Exchange.ERRORHANDLER_HANDLED));
    }

    /**
     * Extracts the body from the given future, that represents a handle to an asynchronous exchange.
     * <p/>
     * Will wait until the future task is complete.
     *
     * @param context the camel context
     * @param future  the future handle
     * @param type    the expected body response type
     * @return the result body, can be <tt>null</tt>.
     * @throws CamelExecutionException is thrown if the processing of the exchange failed
     */
    public static <T> T extractFutureBody(CamelContext context, Future<?> future, Class<T> type) {
        try {
            return doExtractFutureBody(context, future.get(), type);
        } catch (InterruptedException e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        } catch (ExecutionException e) {
            // execution failed due to an exception so rethrow the cause
            throw ObjectHelper.wrapCamelExecutionException(null, e.getCause());
        } finally {
            // its harmless to cancel if task is already completed
            // and in any case we do not want to get hold of the task a 2nd time
            // and its recommended to cancel according to Brian Goetz in his Java Concurrency in Practice book
            future.cancel(true);
        }
    }

    /**
     * Extracts the body from the given future, that represents a handle to an asynchronous exchange.
     * <p/>
     * Will wait for the future task to complete, but waiting at most the timeout value.
     *
     * @param context the camel context
     * @param future  the future handle
     * @param timeout timeout value
     * @param unit    timeout unit
     * @param type    the expected body response type
     * @return the result body, can be <tt>null</tt>.
     * @throws CamelExecutionException is thrown if the processing of the exchange failed
     * @throws java.util.concurrent.TimeoutException is thrown if a timeout triggered
     */
    public static <T> T extractFutureBody(CamelContext context, Future<?> future, long timeout, TimeUnit unit, Class<T> type) throws TimeoutException {
        try {
            if (timeout > 0) {
                return doExtractFutureBody(context, future.get(timeout, unit), type);
            } else {
                return doExtractFutureBody(context, future.get(), type);
            }
        } catch (InterruptedException e) {
            // execution failed due interruption so rethrow the cause
            throw ObjectHelper.wrapCamelExecutionException(null, e);
        } catch (ExecutionException e) {
            // execution failed due to an exception so rethrow the cause
            throw ObjectHelper.wrapCamelExecutionException(null, e.getCause());
        } finally {
            // its harmless to cancel if task is already completed
            // and in any case we do not want to get hold of the task a 2nd time
            // and its recommended to cancel according to Brian Goetz in his Java Concurrency in Practice book
            future.cancel(true);
        }
    }

    private static <T> T doExtractFutureBody(CamelContext context, Object result, Class<T> type) {
        if (result == null) {
            return null;
        }
        if (type.isAssignableFrom(result.getClass())) {
            return type.cast(result);
        }
        if (result instanceof Exchange) {
            Exchange exchange = (Exchange) result;
            Object answer = ExchangeHelper.extractResultBody(exchange, exchange.getPattern());
            return context.getTypeConverter().convertTo(type, exchange, answer);
        }
        return context.getTypeConverter().convertTo(type, result);
    }

    /**
     * @deprecated use org.apache.camel.CamelExchangeException.createExceptionMessage instead
     */
    @Deprecated
    public static String createExceptionMessage(String message, Exchange exchange, Throwable cause) {
        return CamelExchangeException.createExceptionMessage(message, exchange, cause);
    }

    /**
     * Strategy to prepare results before next iterator or when we are complete,
     * which is done by copying OUT to IN, so there is only an IN as input
     * for the next iteration.
     *
     * @param exchange the exchange to prepare
     */
    public static void prepareOutToIn(Exchange exchange) {
        // we are routing using pipes and filters so we need to manually copy OUT to IN
        if (exchange.hasOut()) {
            exchange.setIn(exchange.getOut());
            exchange.setOut(null);
        }
    }

    /**
     * Gets both the messageId and exchangeId to be used for logging purposes.
     * <p/>
     * Logging both ids, can help to correlate exchanges which may be redelivered messages
     * from for example a JMS broker.
     *
     * @param exchange the exchange
     * @return a log message with both the messageId and exchangeId
     */
    public static String logIds(Exchange exchange) {
        String msgId = exchange.hasOut() ? exchange.getOut().getMessageId() : exchange.getIn().getMessageId();
        return "(MessageId: " + msgId + " on ExchangeId: " + exchange.getExchangeId()  + ")";
    }

    /**
     * Copies the exchange but the copy will be tied to the given context
     *
     * @param exchange  the source exchange
     * @param context   the camel context
     * @return a copy with the given camel context
     */
    public static Exchange copyExchangeAndSetCamelContext(Exchange exchange, CamelContext context) {
        return copyExchangeAndSetCamelContext(exchange, context, true);
    }

    /**
     * Copies the exchange but the copy will be tied to the given context
     *
     * @param exchange  the source exchange
     * @param context   the camel context
     * @param handover  whether to handover on completions from the source to the copy
     * @return a copy with the given camel context
     */
    public static Exchange copyExchangeAndSetCamelContext(Exchange exchange, CamelContext context, boolean handover) {
        DefaultExchange answer = new DefaultExchange(context, exchange.getPattern());
        if (exchange.hasProperties()) {
            answer.setProperties(safeCopyProperties(exchange.getProperties()));
        }
        if (handover) {
            // Need to hand over the completion for async invocation
            exchange.handoverCompletions(answer);
        }
        answer.setIn(exchange.getIn().copy());
        if (exchange.hasOut()) {
            answer.setOut(exchange.getOut().copy());
        }
        answer.setException(exchange.getException());
        return answer;
    }

    /**
     * Replaces the existing message with the new message
     *
     * @param exchange  the exchange
     * @param newMessage the new message
     * @param outOnly    whether to replace the message as OUT message
     */
    public static void replaceMessage(Exchange exchange, Message newMessage, boolean outOnly) {
        Message old = exchange.hasOut() ? exchange.getOut() : exchange.getIn();
        if (outOnly || exchange.hasOut()) {
            exchange.setOut(newMessage);
        } else {
            exchange.setIn(newMessage);
        }

        // need to de-reference old from the exchange so it can be GC
        if (old instanceof MessageSupport) {
            ((MessageSupport) old).setExchange(null);
        }
    }

    /**
     * Gets the original IN {@link Message} this Unit of Work was started with.
     * <p/>
     * The original message is only returned if the option {@link org.apache.camel.RuntimeConfiguration#isAllowUseOriginalMessage()}
     * is enabled. If its disabled, then <tt>null</tt> is returned.
     *
     * @return the original IN {@link Message}, or <tt>null</tt> if using original message is disabled.
     */
    public static Message getOriginalInMessage(Exchange exchange) {
        Message answer = null;

        // try parent first
        UnitOfWork uow = exchange.getProperty(Exchange.PARENT_UNIT_OF_WORK, UnitOfWork.class);
        if (uow != null) {
            answer = uow.getOriginalInMessage();
        }
        // fallback to the current exchange
        if (answer == null) {
            uow = exchange.getUnitOfWork();
            if (uow != null) {
                answer = uow.getOriginalInMessage();
            }
        }
        return answer;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> safeCopyProperties(Map<String, Object> properties) {
        if (properties == null) {
            return null;
        }

        Map<String, Object> answer = new HashMap<>(properties);

        // safe copy message history using a defensive copy
        List<MessageHistory> history = (List<MessageHistory>) answer.remove(Exchange.MESSAGE_HISTORY);
        if (history != null) {
            answer.put(Exchange.MESSAGE_HISTORY, new LinkedList<>(history));
        }

        return answer;
    }
}
