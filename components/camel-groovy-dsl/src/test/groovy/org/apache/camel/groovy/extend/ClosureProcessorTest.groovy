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
package org.apache.camel.groovy.extend

import static org.junit.Assert.*
import static org.apache.camel.groovy.extend.CamelGroovyMethods.toProcessor

import org.apache.camel.Exchange
import org.apache.camel.impl.DefaultCamelContext
import org.apache.camel.impl.DefaultExchange
import org.junit.Before
import org.junit.Test


class ClosureProcessorTest {

    private Exchange exchange
    private static final String HELLO = "Hello"
    
    @Before
    public void setUp() throws Exception {
        exchange = new DefaultExchange(new DefaultCamelContext());
    }
    
    @Test
    public void testProcessor() {
        exchange.in.body = HELLO
        ClosureProcessor processor = toProcessor { Exchange exchange ->
            exchange.in.body = exchange.in.body.reverse()
        }
        processor.process(exchange)
        assertEquals(HELLO.reverse(), exchange.in.body )       
    }

    @Test(expected=NullPointerException)
    public void testProcessorException() {
        exchange.in.body = null
        ClosureProcessor processor = toProcessor { Exchange exchange ->
            exchange.in.body = exchange.in.body.reverse()
        }
        processor.process(exchange)
    }
}
