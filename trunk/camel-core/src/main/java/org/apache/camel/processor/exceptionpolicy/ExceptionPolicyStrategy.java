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
package org.apache.camel.processor.exceptionpolicy;

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.model.OnExceptionDefinition;

/**
 * A strategy to determine which {@link org.apache.camel.model.OnExceptionDefinition} should handle the thrown
 * exception.
 *
 * @see org.apache.camel.processor.exceptionpolicy.DefaultExceptionPolicyStrategy DefaultExceptionPolicy
 */
public interface ExceptionPolicyStrategy {

    /**
     * Resolves the {@link org.apache.camel.model.OnExceptionDefinition} that should handle the thrown exception.
     *
     * @param exceptionPolicies the configured exception policies to resolve from
     * @param exchange           the exchange
     * @param exception          the exception that was thrown
     * @return the resolved exception type to handle this exception, <tt>null</tt> if none found.
     */
    OnExceptionDefinition getExceptionPolicy(Map<ExceptionPolicyKey, OnExceptionDefinition> exceptionPolicies,
                                             Exchange exchange, Throwable exception);

}
