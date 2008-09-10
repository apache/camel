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
package org.apache.camel.processor;

import java.io.Serializable;

/**
 * The base policy used when a fixed delay is needed.
 * <p/>
 * This policy is used by
 * <a href="http://activemq.apache.org/camel/transactional-client.html">Transactional client</a>
 * and <a href="http://activemq.apache.org/camel/dead-letter-channel.html">Dead Letter Channel</a>.
 *
 * The default values is:
 * <ul>
 *   <li>delay = 1000L</li>
 * </ul>
 * <p/>
 *
 * @version $Revision$
 */
public class DelayPolicy implements Cloneable, Serializable {

    protected long delay = 1000L;

    public DelayPolicy() {
    }

    @Override
    public String toString() {
        return "DelayPolicy[delay=" + delay + "]";
    }

    public DelayPolicy copy() {
        try {
            return (DelayPolicy)clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException("Could not clone: " + e, e);
        }
    }

    // Builder methods
    // -------------------------------------------------------------------------

    /**
     * Sets the delay in milliseconds
     */
    public DelayPolicy delay(long delay) {
        setDelay(delay);
        return this;
    }

    // Properties
    // -------------------------------------------------------------------------
    public long getDelay() {
        return delay;
    }

    /**
     * Sets the delay in milliseconds
     */
    public void setDelay(long delay) {
        this.delay = delay;
    }


}
