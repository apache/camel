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
package org.apache.camel.impl;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.spi.CamelContextNameStrategy;

/**
 * A default name strategy which auto assigns a name using a prefix-counter pattern.
 *
 * @version $Revision$
 */
public class DefaultCamelContextNameStrategy implements CamelContextNameStrategy {

    private static final String NAME_PREFIX = "camel-";
    private static final AtomicInteger CONTEXT_COUNTER = new AtomicInteger(0);
    private String name;

    public DefaultCamelContextNameStrategy() {
        name = getNextName();
    }

    public String getName() {
        return name;
    }

    public static String getNextName() {
        return NAME_PREFIX + CONTEXT_COUNTER.incrementAndGet();
    }

    /**
     * To reset the counter, should only be used for testing purposes.
     *
     * @param value the counter value
     */
    public static void setCounter(int value) {
        CONTEXT_COUNTER.set(value);
    }

}
