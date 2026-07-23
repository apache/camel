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
 * Persistent store of message identifiers that implements the
 * <a href="https://camel.apache.org/manual/idempotent-consumer.html">Idempotent Consumer</a> pattern, ensuring each
 * message is processed at most once.
 * <p/>
 * The {@code add} and {@code contains} methods follow the {@link java.util.Set} contract: {@link #add(String)} returns
 * {@code true} only when the key is new (first occurrence), and {@link #contains(String)} tests membership without
 * adding.
 * <p/>
 * The repository supports two processing modes, controlled by the Idempotent Consumer EIP configuration:
 * <ul>
 * <li><b>eager (default)</b> — {@link #add(String)} is called on entry. On success, {@link #confirm(String)} commits
 * the key. On failure, {@link #remove(String)} rolls back so the message can be redelivered.</li>
 * <li><b>non-eager</b> — {@link #contains(String)} guards entry; {@link #add(String)} is only called on success.
 * {@link #remove(String)} is still called on failure.</li>
 * </ul>
 * The removal-on-failure callback can be disabled via the EIP option, which is useful for at-most-once semantics even
 * when the downstream processing fails. Persistent implementations backed by a file, JDBC database, Redis, or Hazelcast
 * are available as separate Camel components.
 */
public interface IdempotentRepository extends Service {

    /**
     * Adds the key to the repository.
     * <p/>
     * <b>Important:</b> Read the class javadoc about eager vs non-eager mode.
     *
     * @param  key the key of the message for duplicate test
     * @return     <tt>true</tt> if this repository did <b>not</b> already contain the specified element
     */
    boolean add(String key);

    /**
     * Returns <tt>true</tt> if this repository contains the specified element.
     * <p/>
     * <b>Important:</b> Read the class javadoc about eager vs non-eager mode.
     *
     * @param  key the key of the message
     * @return     <tt>true</tt> if this repository contains the specified element
     */
    boolean contains(String key);

    /**
     * Removes the key from the repository.
     * <p/>
     * Is usually invoked if the exchange failed.
     * <p/>
     * <b>Important:</b> Read the class javadoc about eager vs non-eager mode.
     *
     * @param  key the key of the message for duplicate test
     * @return     <tt>true</tt> if the key was removed
     */
    boolean remove(String key);

    /**
     * Confirms the key, after the exchange has been processed successfully.
     * <p/>
     * <b>Important:</b> Read the class javadoc about eager vs non-eager mode.
     *
     * @param  key the key of the message for duplicate test
     * @return     <tt>true</tt> if the key was confirmed
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
     * @param  key the key of the message for duplicate test
     * @return     <tt>true</tt> if this repository did <b>not</b> already contain the specified element
     */
    default boolean add(Exchange exchange, String key) {
        return add(key);
    }

    /**
     * Returns <tt>true</tt> if this repository contains the specified element.
     * <p/>
     * <b>Important:</b> Read the class javadoc about eager vs non-eager mode.
     *
     * @param  key the key of the message
     * @return     <tt>true</tt> if this repository contains the specified element
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
     * @param  key the key of the message for duplicate test
     * @return     <tt>true</tt> if the key was removed
     */
    default boolean remove(Exchange exchange, String key) {
        return remove(key);
    }

    /**
     * Confirms the key, after the exchange has been processed successfully.
     * <p/>
     * <b>Important:</b> Read the class javadoc about eager vs non-eager mode.
     *
     * @param  key the key of the message for duplicate test
     * @return     <tt>true</tt> if the key was confirmed
     */
    default boolean confirm(Exchange exchange, String key) {
        return confirm(key);
    }

}
