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
package org.apache.camel.test.main.junit5;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.model.ProcessorDefinition;

/**
 * {@code DebuggerCallback} is an interface to implement in case a test wishes to be called <strong>immediately</strong>
 * before and after invoking a processor by enabling and configuring automatically the debug mode.
 * <p/>
 * Only an outer class can implement this interface, implementing this interface from a {@code @Nested} test class has
 * no effect.
 */
public interface DebuggerCallback {

    /**
     * Single step debugs and Camel invokes this method before entering the given processor
     */
    void debugBefore(
            Exchange exchange, Processor processor, ProcessorDefinition<?> definition, String id, String label);

    /**
     * Single step debugs and Camel invokes this method after processing the given processor
     */
    void debugAfter(
            Exchange exchange, Processor processor, ProcessorDefinition<?> definition, String id, String label,
            long timeTaken);
}
