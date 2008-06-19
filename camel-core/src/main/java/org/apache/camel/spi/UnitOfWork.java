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

import org.apache.camel.Exchange;

/**
 * An object representing the unit of work processing an {@link Exchange}
 * which allows the use of {@link Synchronization} hooks. This object might map one-to-one with
 * a transaction in JPA or Spring; or might not.
 *
 * @version $Revision$
 */
public interface UnitOfWork {

    /**
     * Adds a synchronization hook
     *
     * @param synchronization
     */
    void addSynchronization(Synchronization synchronization);

    /**
     * Removes a synchronization hook
     *
     * @param synchronization
     */
    void removeSynchronization(Synchronization synchronization);

    /**
     * Invoked when this unit of work has been completed, whether it has failed or completed
     */
    void done(Exchange exchange);

    /**
     * Returns the unique ID of this unit of work, lazily creating one if it does not yet have one
     *
     * @return
     */
    String getId();
}
