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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.interceptor.Tracer;
import org.apache.camel.util.ExchangeHelper;

public class TraceInterceptorWithOutBodyTraceTest extends TraceInterceptorTest {

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: tracingOutExchanges
                Tracer tracer = new Tracer();
                tracer.setTraceOutExchanges(true);
                tracer.getFormatter().setShowOutBody(true);
                tracer.getFormatter().setShowOutBodyType(true);
                
                getContext().addInterceptStrategy(tracer);
                // END SNIPPET: tracingOutExchanges
                
                from("direct:start").
                    transform().body().
                    to("mock:a").
                    to("mock:b");
            }
        };
    }

}
