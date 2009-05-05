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
package org.apache.camel.component.cxf;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

/**
 * A simple class to wrap an existing processor in AOP around
 * with two processors that will be executed before and after the
 * main processor.
 */
public class CxfAroundProcessor implements Processor {

    // TODO: Should leverage AOP around when we have support for that in camel-core

    private final Processor processor;
    private final Processor before;
    private final Processor after;

    public CxfAroundProcessor(Processor processor, Processor before, Processor after) {
        this.processor = processor;
        this.before = before;
        this.after = after;
    }

    public void process(Exchange exchange) throws Exception {
        before.process(exchange);
        processor.process(exchange);
        after.process(exchange);
    }

}
