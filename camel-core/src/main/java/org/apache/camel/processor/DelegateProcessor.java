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

import org.apache.camel.Processor;
import org.apache.camel.Exchange;
import org.apache.camel.spi.Policy;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.util.ServiceHelper;

/**
 * A Delegate pattern which delegates processing to a nested processor which can be useful for implementation inheritence
 * when writing an {@link Policy}
 *
 * @version $Revision: 519941 $
 */
public class DelegateProcessor extends ServiceSupport implements Processor {
    protected Processor next;

    public DelegateProcessor() {
    }

    public DelegateProcessor(Processor next) {
        this.next = next;
    }

    public void process(Exchange exchange) throws Exception {
        processNext(exchange);
    }

    protected void processNext(Exchange exchange) throws Exception {
        if (next != null) {
            next.process(exchange);
        }
    }

    @Override
    public String toString() {
        return "delegate(" + next + ")";
    }

    public Processor getNext() {
        return next;
    }

    public void setNext(Processor next) {
        this.next = next;
    }

    protected void doStart() throws Exception {
        ServiceHelper.startServices(next);
    }

    protected void doStop() throws Exception {
        ServiceHelper.stopServices(next);
    }
}
