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
package org.apache.camel.processor;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.model.ExceptionType;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @version $Revision: 1.1 $
 */
public abstract class ErrorHandlerSupport extends ServiceSupport implements ErrorHandler {
    private Map<Class, ExceptionType> exceptionPolicices = new IdentityHashMap<Class, ExceptionType>();

    public void addExceptionPolicy(ExceptionType exception) {
        Processor processor = exception.getErrorHandler();
        addChildService(processor);

        List<Class> list = exception.getExceptionClasses();

        for (Class exceptionType : list) {
            exceptionPolicices.put(exceptionType, exception);
        }
    }

    /**
     * Attempts to invoke the handler for this particular exception if one is available
     *
     * @param exchange
     * @param exception
     * @return
     */
    protected boolean customProcessorForException(Exchange exchange, Throwable exception) throws Exception {
        ExceptionType policy = getExceptionPolicy(exchange, exception);
        Processor processor = policy.getErrorHandler();
        if (processor != null) {
            processor.process(exchange);
            return true;
        }
        return false;
    }

    protected ExceptionType getExceptionPolicy(Exchange exchange, Throwable exception) {
        Set<Map.Entry<Class, ExceptionType>> entries = exceptionPolicices.entrySet();
        for (Map.Entry<Class, ExceptionType> entry : entries) {
            Class type = entry.getKey();
            if (type.isInstance(exception)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
