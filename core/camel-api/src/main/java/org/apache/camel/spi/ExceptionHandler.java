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
package org.apache.camel.spi;

import org.apache.camel.Exchange;

/**
 * A Strategy pattern for handling exceptions; particularly in asynchronous processes such as consumers.
 * <p/>
 * Its important to <b>not</b> throw any exceptions when handling exceptions as they handler
 * is often invoked in a try .. catch logic already
 */
public interface ExceptionHandler {
    
    /**
     * Handles the given exception
     *
     * @param exception the exception
     */
    void handleException(Throwable exception);

    /**
     * Handles the given exception
     *
     * @param message additional message
     * @param exception the exception
     */
    void handleException(String message, Throwable exception);

    /**
     * Handles the given exception
     *
     * @param message additional message
     * @param exchange exchange which cause the exception
     * @param exception the exception
     */
    void handleException(String message, Exchange exchange, Throwable exception);
}
