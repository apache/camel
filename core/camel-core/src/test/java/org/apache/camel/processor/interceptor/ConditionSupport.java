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
package org.apache.camel.processor.interceptor;

import org.apache.camel.Exchange;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.spi.CamelEvent.ExchangeEvent;
import org.apache.camel.spi.Condition;

/**
 * A support class for {@link org.apache.camel.spi.Condition} implementations to
 * use as base class.
 */
public class ConditionSupport implements Condition {

    @Override
    public boolean matchProcess(Exchange exchange, Processor processor, NamedNode definition) {
        return false;
    }

    @Override
    public boolean matchEvent(Exchange exchange, ExchangeEvent event) {
        return false;
    }
}
