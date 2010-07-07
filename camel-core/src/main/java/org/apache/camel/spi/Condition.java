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
import org.apache.camel.model.ProcessorDefinition;

/**
 * A condition to define when a given {@link Exchange} matches when is being routed.
 * <p/>
 * Is used by the {@link org.apache.camel.spi.Debugger} to apply {@link Condition}s
 * to {@link org.apache.camel.spi.Breakpoint}s to define rules when the breakpoints should match.
 *
 * @version $Revision$
 */
public interface Condition {

    /**
     * Does the condition match
     *
     * @param exchange the exchange
     * @param definition the current node in the route where the Exchange is at
     * @return <tt>true</tt> to match, <tt>false</tt> otherwise
     */
    boolean match(Exchange exchange, ProcessorDefinition definition);

}
