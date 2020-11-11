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

import org.apache.camel.Processor;

/**
 * Customizer for {@link ErrorHandler} which supports redeliveries. This is used internally by Camel to instrument the
 * error handler with additional instrumentations during route initialization.
 */
public interface ErrorHandlerRedeliveryCustomizer {

    /**
     * Determines if redelivery is enabled by checking if any of the redelivery policy settings may allow redeliveries.
     *
     * @return           <tt>true</tt> if redelivery is possible, <tt>false</tt> otherwise
     * @throws Exception can be thrown
     */
    boolean determineIfRedeliveryIsEnabled() throws Exception;

    /**
     * Returns the output processor
     */
    Processor getOutput();

    /**
     * Allows to change the output of the error handler which are used when optimising the JMX instrumentation to use
     * either an advice or wrapped processor when calling a processor. The former is faster and therefore preferred,
     * however if the error handler supports redelivery we need fine grained instrumentation which then must be wrapped
     * and therefore need to change the output on the error handler.
     */
    void changeOutput(Processor output);

}
