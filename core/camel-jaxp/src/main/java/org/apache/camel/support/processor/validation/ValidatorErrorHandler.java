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
package org.apache.camel.support.processor.validation;

import javax.xml.transform.Result;
import javax.xml.validation.Schema;

import org.xml.sax.ErrorHandler;

import org.apache.camel.Exchange;
import org.apache.camel.ValidationException;

/**
 * Validator error handler.
 */
public interface ValidatorErrorHandler extends ErrorHandler {

    /**
     * Resets any state within this error handler
     */
    void reset();

    /**
     * Process any errors which may have occurred during validation
     *
     * @param exchange the exchange
     * @param schema   the schema
     * @param result   the result
     * @throws ValidationException is thrown in case of validation errors
     */
    void handleErrors(Exchange exchange, Schema schema, Result result) throws ValidationException;
}
