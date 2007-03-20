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
 * Represents the base interface of an exchange
 *
 * @version $Revision$
 * @param <M> message or payload type
 * @param <R> message or payload type for a response (for request/response exchange)
 * @param <F> fault type
 */
public interface Exchange<M,R,F> {
    
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
     * Accesses a specific header
     * @param name 
     * @return object header associated with the name
     */
    Object getHeader(String name);

    /**
     * Sets a header on the exchange
     * @param name of the header 
     * @param value to associate with the name
     */
    void setHeader(String name, Object value);

    /**
     * Returns all of the headers associated with the request
     * @return all the headers in a Map
     */
    Map<String,Object> getHeaders();

    /**
     * Returns the request message
     * @return the message
     */
    M getRequest();

    /**
     * Returns the response message
     * @return the response
     */
    R getResponse();

    /**
     * Returns the fault message
     * @return the fault
     */
    F getFault();

    /**
     * Returns the exception associated with this exchange
     * @return the exception (or null if no faults)
     */
    Exception getException();

    /**
     * Sets the exception associated with this exchange
     * @param e 
     */
    void setException(Exception e);

}
