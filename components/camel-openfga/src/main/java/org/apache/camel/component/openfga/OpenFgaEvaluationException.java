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
package org.apache.camel.component.openfga;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;

/**
 * Thrown when OpenFGA could not be asked at all, for example because the server is unreachable or answered with an
 * error.
 * <p/>
 * This is not the same thing as a denial. A deny is an answer, and is reported through the
 * {@link OpenFgaConstants#ALLOWED} header (producer) or a {@link org.apache.camel.CamelAuthorizationException}
 * (security policy). Catch this exception to react to an unavailable decision point.
 */
public class OpenFgaEvaluationException extends CamelExchangeException {

    private static final long serialVersionUID = 1L;

    public OpenFgaEvaluationException(String message, Exchange exchange, Throwable cause) {
        super(message, exchange, cause);
    }
}
