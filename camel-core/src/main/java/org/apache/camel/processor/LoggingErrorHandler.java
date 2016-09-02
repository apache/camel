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

import org.apache.camel.CamelContext;
import org.apache.camel.Processor;
import org.apache.camel.processor.exceptionpolicy.ExceptionPolicyStrategy;
import org.apache.camel.util.CamelLogger;

/**
 * An {@link ErrorHandler} which uses commons-logging to dump the error
 *
 * @version
 * @deprecated use {@link DeadLetterChannel} using a log endpoint instead
 */
@Deprecated
public class LoggingErrorHandler extends DefaultErrorHandler {

    /**
     * Creates the logging error handler.
     *
     * @param camelContext            the camel context
     * @param output                  outer processor that should use this logging error handler
     * @param logger                  logger to use for logging failures
     * @param redeliveryPolicy        redelivery policy
     * @param exceptionPolicyStrategy strategy for onException handling
     */
    public LoggingErrorHandler(CamelContext camelContext, Processor output, CamelLogger logger,
                               RedeliveryPolicy redeliveryPolicy, ExceptionPolicyStrategy exceptionPolicyStrategy) {
        super(camelContext, output, logger, null, redeliveryPolicy, exceptionPolicyStrategy, null, null, null, null);
    }

    @Override
    public String toString() {
        return "LoggingErrorHandler[" + output + "]";
    }

}
