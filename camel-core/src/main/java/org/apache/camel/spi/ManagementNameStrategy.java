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

/**
 * Strategy for assigning the name part of the {@link javax.management.ObjectName}
 * for a managed {@link org.apache.camel.CamelContext}.
 * <p/>
 * A strategy is needed as you can run multiple CamelContext in the same JVM, and want them
 * to be enlisted in the JVM wide JMXMBeanServer. And this requires a strategy to be able
 * to calculate unique names, in case of clashes. Or to enforce an explicit fixed name,
 * to ensure the JMX name is not using dynamic counters etc.
 * <p/>
 * This strategy supports a naming pattern which supports at least the following tokens
 * <ul>
 *   <li>#camelId# - the camel id (eg the camel name)</li>
 *   <li>#name# - same as #camelId#</li>
 *   <li>#counter# - an incrementing counter</li>
 * </ul>
 *
 * @see CamelContextNameStrategy
 * @see org.apache.camel.impl.DefaultManagementNameStrategy
 */
public interface ManagementNameStrategy {

    /**
     * Gets the custom name pattern.
     *
     * @return the custom name pattern, or <tt>null</tt> if using the default pattern strategy.
     */
    String getNamePattern();

    /**
     * Sets a custom name pattern, which will be used instead of any default patterns.
     *
     * @param pattern a custom name pattern.
     */
    void setNamePattern(String pattern);

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

    /**
     * Creates a new management name with the given pattern.
     *
     * @param pattern the pattern
     * @param name    the name
     * @param invalidCheck whether to check for invalid pattern
     * @return the management name
     * @throws IllegalArgumentException if the pattern or name is invalid or empty
     */
    String resolveManagementName(String pattern, String name, boolean invalidCheck);
}
