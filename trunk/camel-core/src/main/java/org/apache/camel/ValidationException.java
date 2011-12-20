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

/**
 * The base class for any validation exception, such as
 * {@link org.apache.camel.processor.validation.SchemaValidationException} so
 * that it is easy to treat all validation errors in a similar way irrespective
 * of the particular validation technology used.
 * 
 * @version 
 */
public class ValidationException extends CamelExchangeException {
    private static final long serialVersionUID = -7485357452450907415L;

    public ValidationException(Exchange exchange, String message) {
        super(message, exchange);
    }

    public ValidationException(String message, Exchange exchange, Throwable cause) {
        super(message, exchange, cause);
    }
}
