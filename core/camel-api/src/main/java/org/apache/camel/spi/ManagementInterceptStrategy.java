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

import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.NamedNode;
import org.apache.camel.Ordered;
import org.apache.camel.Processor;

public interface ManagementInterceptStrategy {

    InstrumentationProcessor<?> createProcessor(NamedNode definition, Processor target);

    InstrumentationProcessor<?> createProcessor(String type);

    interface InstrumentationProcessor<T> extends AsyncProcessor, Ordered {

        T before(Exchange exchange);

        void after(Exchange exchange, T data);

        void setProcessor(Processor processor);

        void setCounter(Object object);
    }
}
