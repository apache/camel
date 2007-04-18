/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.processor.idempotent;

import java.util.Set;
import java.util.HashSet;

/**
 * A simple memory implementation of {@link MessageIdRepository}; though warning this could use up lots of RAM!
 *
 * @version $Revision: 1.1 $
 */
public class MemoryMessageIdRepository implements MessageIdRepository {
    private Set set;

    /**
     * Creates a new MemoryMessageIdRepository with a memory based respository
     */
    public static MessageIdRepository memoryMessageIdRepository() {
        return memoryMessageIdRepository(new HashSet());
    }

    /**
     * Creates a new MemoryMessageIdRepository using the given {@link Set} to use to store the
     * processed Message ID objects
     */
    public static MessageIdRepository memoryMessageIdRepository(Set set) {
        return new MemoryMessageIdRepository(set);
    }

    public MemoryMessageIdRepository(Set set) {
        this.set = set;
    }

    public boolean contains(String messageId) {
        synchronized (set) {
            if (set.contains(messageId)) {
                return true;
            }
            else {
                set.add(messageId);
                return false;
            }
        }
    }
}
