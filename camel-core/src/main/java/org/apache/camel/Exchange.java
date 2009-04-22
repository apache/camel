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
package org.apache.camel;

import java.util.Map;

import org.apache.camel.spi.UnitOfWork;

/**
 * The base message exchange interface providing access to the request, response
 * and fault {@link Message} instances. Different providers such as JMS, JBI,
 * CXF and HTTP can provide their own derived API to expose the underlying
 * transport semantics to avoid the leaky abstractions of generic APIs.
 *
 * @version $Revision$
 */
public interface Exchange {

    String BEAN_METHOD_NAME = "CamelBeanMethodName";
    String BEAN_HOLDER = "CamelBeanHolder";
    String BEAN_MULTI_PARAMETER_ARRAY = "CamelBeanMultiParameterArray";

    String AGGREGATED_SIZE = "CamelAggregatedSize";

    String CHARSET_NAME = "CamelCharsetName";

    String DATASET_INDEX = "CamelDataSetIndex";

    String EXCEPTION_CAUGHT = "CamelExceptionCaught";
    String EXCEPTION_HANDLED = "CamelExceptionHandled";
    String FAILURE_HANDLED = "CamelFailureHandled";

    String FILE_BATCH_INDEX = "CamelFileBatchIndex";
    String FILE_BATCH_SIZE = "CamelFileBatchSize";
    String FILE_LOCAL_WORK_PATH = "CamelFileLocalWorkPath";
    String FILE_NAME = "CamelFileName";
    String FILE_NAME_ONLY = "CamelFileNameOnly";
    String FILE_NAME_PRODUCED = "CamelFileNameProduced";

    String LOOP_INDEX = "CamelLoopIndex";
    String LOOP_SIZE = "CamelLoopSize";

    String PROCESSED_SYNC = "CamelProcessedSync";

    String REDELIVERED = "CamelRedelivered";
    String REDELIVERY_COUNTER = "CamelRedeliveryCounter";

    String SPLIT_INDEX = "CamelSplitIndex";
    String SPLIT_SIZE = "CamelSplitSize";

    String TIMER_NAME = "CamelTimerName";
    String TIMER_FIRED_TIME = "CamelTimerFiredTime";
    String TIMER_PERIOD = "CamelTimerPeriod";
    String TIMER_TIME = "CamelTimerTime";

    String TRANSACTED = "CamelTransacted";
    String ROLLBACK_ONLY = "CamelRollbackOnly";

    /**
     * Returns the {@link ExchangePattern} (MEP) of this exchange.
     *
     * @return the message exchange pattern of this exchange
     */
    ExchangePattern getPattern();

    /**
     * Allows the {@link ExchangePattern} (MEP) of this exchange to be customized.
     *
     * This typically won't be required as an exchange can be created with a specific MEP
     * by calling {@link Endpoint#createExchange(ExchangePattern)} but it is here just in case
     * it is needed.
     *
     * @param pattern  the pattern 
     */
    void setPattern(ExchangePattern pattern);

    /**
     * Returns a property associated with this exchange by name
     *
     * @param name the name of the property
     * @return the value of the given header or null if there is no property for
     *         the given name
     */
    Object getProperty(String name);

    /**
     * Returns a property associated with this exchange by name and specifying
     * the type required
     *
     * @param name the name of the property
     * @param type the type of the property
     * @return the value of the given header or null if there is no property for
     *         the given name or null if it cannot be converted to the given
     *         type
     */
    <T> T getProperty(String name, Class<T> type);

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
     * @param name of the property
     * @return the old value of the property
     */
    Object removeProperty(String name);

    /**
     * Returns all of the properties associated with the exchange
     *
     * @return all the headers in a Map
     */
    Map<String, Object> getProperties();

    /**
     * Returns the inbound request message
     *
     * @return the message
     */
    Message getIn();

    /**
     * Sets the inbound message instance
     *
     * @param in the inbound message
     */
    void setIn(Message in);

    /**
     * Returns the outbound message, lazily creating one if one has not already
     * been associated with this exchange. If you want to inspect this property
     * but not force lazy creation then invoke the {@link #getOut(boolean)}
     * method passing in <tt>false</tt>
     *
     * @return the response
     */
    Message getOut();

    /**
     * Returns the outbound message; optionally lazily creating one if one has
     * not been associated with this exchange
     *
     * @param lazyCreate <tt>true</tt> will lazy create the out message
     * @return the response
     */
    Message getOut(boolean lazyCreate);

    /**
     * Sets the outbound message
     *
     * @param out the outbound message
     */
    void setOut(Message out);

    /**
     * Returns the fault message
     *
     * @return the fault
     */
    Message getFault();

    /**
     * Returns the fault message; optionally lazily creating one if one has
     * not been associated with this exchange
     *
     * @param lazyCreate <tt>true</tt> will lazy create the fault message
     * @return the fault
     */
    Message getFault(boolean lazyCreate);

    /**
     * Removes the fault message.
     */
    void removeFault();

    /**
     * Returns the exception associated with this exchange
     *
     * @return the exception (or null if no faults)
     */
    Exception getException();

    /**
     * Returns the exception associated with this exchange.
     * <p/>
     * Is used to get the caused exception that typically have been wrapped in some sort
     * of Camel wrapper exception
     * <p/>
     * The stategy is to look in the exception hieracy to find the first given cause that matches the type.
     * Will start from the bottom (the real cause) and walk upwards.
     *
     * @param type the exception type
     * @return the exception (or null if no faults or if no caused exception matched)
     */
    <T> T getException(Class<T> type);

    /**
     * Sets the exception associated with this exchange
     *
     * @param e  the caused exception
     */
    void setException(Exception e);

    /**
     * Returns true if this exchange failed due to either an exception or fault
     *
     * @return true if this exchange failed due to either an exception or fault
     * @see Exchange#getException()
     * @see Exchange#getFault()
     */
    boolean isFailed();

    /**
     * Returns true if this exchange is transacted
     */
    boolean isTransacted();

    /**
     * Returns true if this exchange is marked for rollback
     */
    boolean isRollbackOnly();

    /**
     * Returns the container so that a processor can resolve endpoints from URIs
     *
     * @return the container which owns this exchange
     */
    CamelContext getContext();

    /**
     * Creates a new exchange instance with empty messages, headers and properties
     */
    Exchange newInstance();

    /**
     * Creates a copy of the current message exchange so that it can be
     * forwarded to another destination
     */
    Exchange copy();

    /**
     * Creates a new instance and copies from the current message exchange so that it can be
     * forwarded to another destination as a new instance. Unlike regular copy this operation
     * will not share the same {@link org.apache.camel.spi.UnitOfWork} so its should be used
     * for async messaging, where the original and copied exchange are independent.
     */
    Exchange newCopy();

    /**
     * Copies the data into this exchange from the given exchange
     *
     * @param source is the source from which headers and messages will be copied
     */
    void copyFrom(Exchange source);

    /**
     * Returns the endpoint which originated this message exchange if a consumer on an endpoint created the message exchange
     * otherwise this property will be null
     */
    Endpoint getFromEndpoint();

    /**
     * Sets the endpoint which originated this message exchange. This method
     * should typically only be called by {@link org.apache.camel.Endpoint} implementations
     *
     * @param fromEndpoint the endpoint which is originating this message exchange
     */
    void setFromEndpoint(Endpoint fromEndpoint);
    
    /**
     * Returns the unit of work that this exchange belongs to; which may map to
     * zero, one or more physical transactions
     */
    UnitOfWork getUnitOfWork();

    /**
     * Sets the unit of work that this exchange belongs to; which may map to
     * zero, one or more physical transactions
     */
    void setUnitOfWork(UnitOfWork unitOfWork);

    /**
     * Returns the exchange id (unique)
     */
    String getExchangeId();

    /**
     * Set the exchange id
     */
    void setExchangeId(String id);

}
