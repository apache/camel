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
package org.apache.camel.bam;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;

/**
 * @version $Revision: $
 */
public abstract class ProcessExpressionSupport<T> implements Expression<Exchange> {

    public static final String PROCESS_PROPERTY = "org.apache.camel.bam.Process";

    private Class<T> type;

    protected ProcessExpressionSupport(Class<T> type) {
        this.type = type;
    }

    public static <T> T getProcessEntity(Exchange exchange, Class<T> type) {
        return exchange.getProperty(PROCESS_PROPERTY, type);
    }

    public static <T> void storeProcessEntity(Exchange exchange, T processEntity) {
        exchange.setProperty(PROCESS_PROPERTY, processEntity);
    }

    public Object evaluate(Exchange exchange) {
        T processEntity = getProcessEntity(exchange, type);
        return evaluate(exchange, processEntity);
    }

    protected abstract Object evaluate(Exchange exchange, T processEntity);
}
