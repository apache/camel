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
import org.apache.camel.Service;
import org.jspecify.annotations.Nullable;

/**
 * Storage repository for the <a href="https://camel.apache.org/manual/claim-check.html">Claim Check EIP</a>, which
 * temporarily parks a message payload so that a lighter message can travel through an intermediate part of a route.
 * <p/>
 * The Claim Check pattern solves the problem of passing large payloads through components that have size limits or
 * performance concerns. The producing side stores the payload under a unique key (the "claim check") using
 * {@link #add(String, Exchange)} or {@link #push(Exchange)}, replacing the message body with just the key. The
 * consuming side later retrieves the payload with {@link #get(String)} or {@link #pop()} and restores it to the
 * exchange.
 * <p/>
 * The interface exposes two access styles:
 * <ul>
 * <li><b>keyed</b> ({@link #add}, {@link #contains}, {@link #get}, {@link #getAndRemove}) — operates like a
 * {@link java.util.Map}; the caller controls the key.</li>
 * <li><b>stack</b> ({@link #push}, {@link #pop}) — operates like a {@link java.util.Deque}; Camel manages the key
 * internally, useful for nested claim checks.</li>
 * </ul>
 *
 * @see org.apache.camel.processor.ClaimCheckProcessor
 */
public interface ClaimCheckRepository extends Service {

    /**
     * Adds the exchange to the repository.
     *
     * @param  key the claim check key
     * @return     <tt>true</tt> if this repository did <b>not</b> already contain the specified key
     */
    boolean add(String key, Exchange exchange);

    /**
     * Returns <tt>true</tt> if this repository contains the specified key.
     *
     * @param  key the claim check key
     * @return     <tt>true</tt> if this repository contains the specified key
     */
    boolean contains(String key);

    /**
     * Gets the exchange from the repository.
     *
     * @param key the claim check key
     */
    @Nullable
    Exchange get(String key);

    /**
     * Gets and removes the exchange from the repository.
     *
     * @param  key the claim check key
     * @return     the removed exchange, or <tt>null</tt> if the key did not exists.
     */
    @Nullable
    Exchange getAndRemove(String key);

    /**
     * Pushes the exchange on top of the repository.
     */
    void push(Exchange exchange);

    /**
     * Pops the repository and returns the latest. Or returns <tt>null</tt> if the stack is empty.
     */
    @Nullable
    Exchange pop();

    /**
     * Clear the repository.
     */
    void clear();

}
