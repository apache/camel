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
package org.apache.camel.reifier.errorhandler;

import org.apache.camel.AsyncCallback;
import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedExchange;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.builder.NoErrorHandlerBuilder;
import org.apache.camel.support.processor.DelegateAsyncProcessor;

public class NoErrorHandlerReifier extends ErrorHandlerReifier<NoErrorHandlerBuilder> {

    public NoErrorHandlerReifier(Route route, ErrorHandlerFactory definition) {
        super(route, (NoErrorHandlerBuilder)definition);
    }

    @Override
    public Processor createErrorHandler(Processor processor) throws Exception {
        return new DelegateAsyncProcessor(processor) {
            @Override
            public boolean process(final Exchange exchange, final AsyncCallback callback) {
                return super.process(exchange, new AsyncCallback() {
                    @Override
                    public void done(boolean doneSync) {
                        exchange.adapt(ExtendedExchange.class).setRedeliveryExhausted(false);
                        callback.done(doneSync);
                    }
                });
            }

            @Override
            public String toString() {
                if (processor == null) {
                    // if no output then dont do any description
                    return "";
                }
                return "NoErrorHandler[" + processor + "]";
            }
        };
    }
}
