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

/**
 * Strategy for assigning name to a {@link org.apache.camel.CamelContext}.
 *
 * @see ManagementNameStrategy
 */
public interface CamelContextNameStrategy {

    /**
     * Gets the name
     * <p/>
     * The {@link #isFixedName()} determines if the name can be re-calculated such as when using a counter,
     * or the name is always fixed.
     *
     * @return the name.
     */
    String getName();

    /**
     * Gets the next calculated name, if this strategy is not using fixed names.
     * <p/>
     * The {@link #isFixedName()} determines if the name can be re-calculated such as when using a counter,
     * or the name is always fixed.
     *
     * @return the next name
     */
    String getNextName();

    /**
     * Whether the name will be fixed, or allow re-calculation such as by using an unique counter.
     * 
     * @return <tt>true</tt> for fixed names, <tt>false</tt> for names which can re-calculated
     */
    boolean isFixedName();
}
