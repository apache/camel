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
package org.apache.camel.processor;

import org.apache.camel.Processor;
import org.apache.camel.Exchange;

import java.util.List;

/**
 * @version $Revision: $
 */
public class CatchProcessor implements Processor {
    private List<Class> exceptions;
    private Processor processor;

    public CatchProcessor(List<Class> exceptions, Processor processor) {
        this.exceptions = exceptions;
        this.processor = processor;
    }


    public boolean catches(Exception e) {
        for (Class type : exceptions) {
            if (type.isInstance(e)) {
                return true;
            }
        }
        return false;
    }

    public void process(Exchange exchange) throws Exception {
        processor.process(exchange);
    }
}
