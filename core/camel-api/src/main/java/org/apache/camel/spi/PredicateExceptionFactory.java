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
import org.apache.camel.Predicate;

/**
 * A factory that can be used to create a specific exception when a {@link Predicate} returning false, which can be used
 * by camel-validator and other components.
 */
public interface PredicateExceptionFactory {

    /**
     * Allows to return a specific exception for the given predicate in case it failed
     *
     * @param  exchange  the current exchange
     * @param  predicate the predicate that returned false
     * @param  nodeId    optional node id from validate EIP using this factory
     * @return           the exception, or <tt>null</tt> to not use a specific exception but let Camel use a standard
     *                   exception such as PredicateValidationException.
     */
    Exception newPredicateException(Exchange exchange, Predicate predicate, String nodeId);

}
