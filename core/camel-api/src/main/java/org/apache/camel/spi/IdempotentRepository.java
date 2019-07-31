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
 * Access to a repository of Message IDs to implement the
 * <a href="http://camel.apache.org/idempotent-consumer.html">Idempotent Consumer</a> pattern.
 * <p/>
 * The <tt>add</tt> and <tt>contains</tt> methods is operating according to the {@link java.util.Set} contract.
 * <p/>
 * The repository supports eager (default) and non-eager mode.
 * <ul>
 *     <li>eager: calls <tt>add</tt> and <tt>confirm</tt> if complete, or <tt>remove</tt> if failed</li>
 *     <li>non-eager: calls <tt>contains</tt> and <tt>add</tt> if complete, or <tt>remove</tt> if failed</li>
 * </ul>
 * Notice the remove callback, can be configured to be disabled.
 */
public interface IdempotentRepository extends Service {

    /**
     * Adds the key to the repository.
     * <p/>
     * <b>Important:</b> Read the class javadoc about eager vs non-eager mode.
     *
     * @param key the key of the message for duplicate test
     * @return <tt>true</tt> if this repository did <b>not</b> already contain the specified element
     */
    boolean add(String key);

    /**
     * Returns <tt>true</tt> if this repository contains the specified element.
     * <p/>
     * <b>Important:</b> Read the class javadoc about eager vs non-eager mode.
     *
     * @param key the key of the message
     * @return <tt>true</tt> if this repository contains the specified element
     */
    boolean contains(String key);

    /**
     * Removes the key from the repository.
     * <p/>
     * Is usually invoked if the exchange failed.
     * <p/>
     * <b>Important:</b> Read the class javadoc about eager vs non-eager mode.
     *
     * @param key the key of the message for duplicate test
     * @return <tt>true</tt> if the key was removed
     */
    boolean remove(String key);

    /**
     * Confirms the key, after the exchange has been processed successfully.
     * <p/>
     * <b>Important:</b> Read the class javadoc about eager vs non-eager mode.
     *
     * @param key the key of the message for duplicate test
     * @return <tt>true</tt> if the key was confirmed
     */
    boolean confirm(String key);
    
    /**
     * Clear the repository.
     * <p/>
     * <b>Important:</b> Read the class javadoc about eager vs non-eager mode.
     */
    void clear();

    /**
     * Adds the key to the repository.
     * <p/>
     * <b>Important:</b> Read the class javadoc about eager vs non-eager mode.
     *
     * @param key the key of the message for duplicate test
     * @return <tt>true</tt> if this repository did <b>not</b> already contain the specified element
     */
    default boolean add(Exchange exchange, String key) {
        return add(key);
    }

    /**
     * Returns <tt>true</tt> if this repository contains the specified element.
     * <p/>
     * <b>Important:</b> Read the class javadoc about eager vs non-eager mode.
     *
     * @param key the key of the message
     * @return <tt>true</tt> if this repository contains the specified element
     */
    default boolean contains(Exchange exchange, String key) {
        return contains(key);
    }

    /**
     * Removes the key from the repository.
     * <p/>
     * Is usually invoked if the exchange failed.
     * <p/>
     * <b>Important:</b> Read the class javadoc about eager vs non-eager mode.
     *
     * @param key the key of the message for duplicate test
     * @return <tt>true</tt> if the key was removed
     */
    default boolean remove(Exchange exchange, String key) {
        return remove(key);
    }

    /**
     * Confirms the key, after the exchange has been processed successfully.
     * <p/>
     * <b>Important:</b> Read the class javadoc about eager vs non-eager mode.
     *
     * @param key the key of the message for duplicate test
     * @return <tt>true</tt> if the key was confirmed
     */
    default boolean confirm(Exchange exchange, String key) {
        return confirm(key);
    }

}
