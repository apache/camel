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
package org.apache.camel.processor.interceptor;

import org.apache.camel.Exchange;
import org.apache.camel.model.ProcessorDefinition;

/**
 * Formatter to format trace logs when tracing {@link Exchange} during routing.
 */
@Deprecated
public interface TraceFormatter {

    /**
     * Formats a log message at given point of interception.
     * 
     * @param interceptor    the tracing interceptor
     * @param node           the node where the interception occurred
     * @param exchange       the current exchange
     * @return the log message
     */
    Object format(TraceInterceptor interceptor, ProcessorDefinition<?> node, Exchange exchange);
}
