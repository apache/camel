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

import org.apache.camel.Processor;

/**
 * An interface for {@link Processor} aware of its {@link ErrorHandler} which are wrapped via the
 * {@link org.apache.camel.Channel} during route initialization, or specially used by
 * {@link org.apache.camel.RecipientList} EIP annotation.
 */
public interface ErrorHandlerAware {

    /**
     * Sets the error handler
     *
     * @param errorHandler the error handler
     */
    void setErrorHandler(Processor errorHandler);

    /**
     * Gets the error handler
     */
    Processor getErrorHandler();

}
