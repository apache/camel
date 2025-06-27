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
package org.apache.camel.spring.interceptor;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.spi.InterceptStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// START SNIPPET: e1
public class ContainerWideInterceptor implements InterceptStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(ContainerWideInterceptor.class);
    private static int count;

    @Override
    public Processor wrapProcessorInInterceptors(
            final CamelContext context, final NamedNode definition,
            final Processor target, final Processor nextTarget)
            throws Exception {

        // as this is based on an unit test we are a bit lazy and just create an inlined processor
        // where we implement our interception logic.
        return new Processor() {
            public void process(Exchange exchange) throws Exception {
                // we just count number of interceptions
                count++;
                LOG.info("I am the container wide interceptor. Intercepted total count: {}", count);
                // its important that we delegate to the real target so we let target process the exchange
                target.process(exchange);
            }

            @Override
            public String toString() {
                return "ContainerWideInterceptor[" + target + "]";
            }
        };
    }

    public int getCount() {
        return count;
    }
}
// END SNIPPET: e1
