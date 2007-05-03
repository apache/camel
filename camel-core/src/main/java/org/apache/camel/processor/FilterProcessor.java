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

import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.Exchange;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.util.ServiceHelper;

/**
 * @version $Revision$
 */
public class FilterProcessor extends ServiceSupport implements Processor {
    private Predicate<Exchange> predicate;
    private Processor processor;

    public FilterProcessor(Predicate<Exchange> predicate, Processor processor) {
        this.predicate = predicate;
        this.processor = processor;
    }

    public void process(Exchange exchange) throws Exception {
        if (predicate.matches(exchange)) {
            processor.process(exchange);
        }
    }

    @Override
    public String toString() {
        return "filter (" + predicate + ") " + processor;
    }

    public Predicate<Exchange> getPredicate() {
        return predicate;
    }

    public Processor getProcessor() {
        return processor;
    }

    protected void doStart() throws Exception {
        ServiceHelper.startServices(processor);
    }

    protected void doStop() throws Exception {
        ServiceHelper.stopServices(processor);
    }
}
