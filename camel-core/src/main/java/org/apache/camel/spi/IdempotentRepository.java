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
package org.apache.camel.spi;

import org.apache.camel.Service;

/**
 * Access to a repository of Message IDs to implement the
 * <a href="http://camel.apache.org/idempotent-consumer.html">Idempotent Consumer</a> pattern.
 * <p/>
 * The <tt>add</tt> and <tt>contains</tt> methods is operating according to the {@link java.util.Set} contract.
 *
 * @version $Revision$
 */
public interface IdempotentRepository<E> extends Service {

    /**
     * Adds the key to the repository.
     *
     * @param key the key of the message for duplicate test
     * @return <tt>true</tt> if this repository did <b>not</b> already contain the specified element
     */
    boolean add(E key);

    /**
     * Returns <tt>true</tt> if this repository contains the specified element.
     * <p/>
     * This operation is used if the option <tt>eager</tt> has been enabled.
     *
     * @param key the key of the message
     * @return <tt>true</tt> if this repository contains the specified element
     */
    boolean contains(E key);

    /**
     * Removes the key from the repository.
     * <p/>
     * Is usually invoked if the exchange failed.
     *
     * @param key the key of the message for duplicate test
     * @return <tt>true</tt> if the key was removed
     */
    boolean remove(E key);

    /**
     * Confirms the key, after the exchange has been processed successfully.
     * <p/>
     * This operation is used if the option <tt>eager</tt> has been enabled.
     *
     * @param key the key of the message for duplicate test
     * @return <tt>true</tt> if the key was confirmed
     */
    boolean confirm(E key);

}
