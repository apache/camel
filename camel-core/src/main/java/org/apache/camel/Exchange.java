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

/**
 * Represents the base exchange interface providing access to the request, response and fault {@link Message} instances.
 *
 * @version $Revision$
 */
public interface Exchange  {
    
    /**
     * Returns the exchange id
     * @return the unique id of the exchange
     */
    String getExchangeId();
    
    /**
     * Set the exchange id
     * @param id
     */
    void setExchangeId(String id);


    /**
     * Returns a property associated with this exchange by name
     *
     * @param name the name of the property
     * @return the value of the given header or null if there is no property for the given name
     */
    Object getProperty(String name);

    /**
     * Sets a property on the exchange
     *
     * @param name of the property
     * @param value to associate with the name
     */
    void setProperty(String name, Object value);

    /**
     * Returns all of the properties associated with the exchange
     *
     * @return all the headers in a Map
     */
    Map<String, Object> getHeaders();

    
    /**
     * Returns the inbound request message
     * @return the message
     */
    Message getIn();

    /**
     * Returns the aresponse message
     * @return the response
     */
    Message getOut();

    /**
     * Returns the fault message
     * @return the fault
     */
    Message getFault();

    /**
     * Returns the exception associated with this exchange
     * @return the exception (or null if no faults)
     */
    Throwable getException();

    /**
     * Sets the exception associated with this exchange
     * @param e
     */
    void setException(Throwable e);

    /**
     * Returns the container so that a processor can resolve endpoints from URIs
     *
     * @return the container which owns this exchange
     */
    CamelContext getContext();

    /**
     * Creates a copy of the current message exchange so that it can be forwarded to another
     * destination
     */
    Exchange copy();

    /**
     * Copies the data into this exchange from the given exchange
     *
     * #param source is the source from which headers and messages will be copied 
     */
    void copyFrom(Exchange source);
}
