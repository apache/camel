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
 * Implemented by objects that carry an explicit ordering position within a sorted collection.
 * <p/>
 * Camel uses this interface wherever the evaluation or application sequence matters: {@link Processor} chains,
 * interceptors, {@link org.apache.camel.spi.LifecycleStrategy} callbacks, and bean-method resolution all sort their
 * participants by {@link #getOrder()}. Lower values run first; {@link #HIGHEST} ({@code Integer.MIN_VALUE}) gives the
 * absolute earliest slot and {@link #LOWEST} gives a near-last slot (values above {@code LOWEST} are reserved for
 * internal Camel use).
 */
public interface Ordered {

    /**
     * The highest precedence
     */
    int HIGHEST = Integer.MIN_VALUE;

    /**
     * The lowest precedence
     */
    int LOWEST = Integer.MAX_VALUE - 1000; // reserve for internal use

    /**
     * Gets the order.
     * <p/>
     * Use low numbers for higher priority. Normally the sorting will start from 0 and move upwards. So if you want to
     * be last then use {@link Integer#MAX_VALUE} or eg {@link #LOWEST}.
     *
     * @return the order
     */
    int getOrder();
}
