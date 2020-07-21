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

/**
 * Evaluates a binary <a href="http://camel.apache.org/predicate.html">predicate</a> on the
 * message exchange.
 */
public interface Predicate {

    /**
     * Evaluates the predicate on the message exchange and returns true if this
     * exchange matches the predicate
     * 
     * @param exchange the message exchange
     * @return true if the predicate matches
     */
    boolean matches(Exchange exchange);

    /**
     * Initialize the predicate with the given camel context
     */
    default void init(CamelContext context) {
    }

}
