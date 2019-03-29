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

/**
 * Access to a repository of keys to implement the
 * <a href="http://camel.apache.org/claim-check.html">Claim Check</a> pattern.
 * <p/>
 * The <tt>add</tt> and <tt>contains</tt> methods is operating according to the {@link java.util.Map} contract,
 * and the <tt>push</tt> and <tt>pop</tt> methods is operating according to the {@link java.util.Stack} contract.
 * <p/>
 * See important details about the Claim Check EIP implementation in Apache Camel at {@link org.apache.camel.processor.ClaimCheckProcessor}.
 */
public interface ClaimCheckRepository extends Service {

    /**
     * Adds the exchange to the repository.
     *
     * @param key the claim check key
     * @return <tt>true</tt> if this repository did <b>not</b> already contain the specified key
     */
    boolean add(String key, Exchange exchange);

    /**
     * Returns <tt>true</tt> if this repository contains the specified key.
     *
     * @param key the claim check key
     * @return <tt>true</tt> if this repository contains the specified key
     */
    boolean contains(String key);

    /**
     * Gets the exchange from the repository.
     *
     * @param key the claim check key
     */
    Exchange get(String key);

    /**
     * Gets and removes the exchange from the repository.
     *
     * @param key the claim check key
     * @return the removed exchange, or <tt>null</tt> if the key did not exists.
     */
    Exchange getAndRemove(String key);

    /**
     * Pushes the exchange on top of the repository.
     */
    void push(Exchange exchange);

    /**
     * Pops the repository and returns the latest. Or returns <tt>null</tt> if the stack is empty.
     */
    Exchange pop();

    /**
     * Clear the repository.
     */
    void clear();

}
