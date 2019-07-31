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
package org.apache.camel.test.junit4;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultExchange;
import org.junit.Before;

/**
 * A base class for a test which requires a {@link org.apache.camel.CamelContext} and
 * a populated {@link Exchange}
 */
public abstract class ExchangeTestSupport extends CamelTestSupport {
    protected Exchange exchange;

    /**
     * A factory method to create an Exchange implementation
     */
    protected Exchange createExchange() {
        return new DefaultExchange(context);
    }

    /**
     * A strategy method to populate an exchange with some example values for use
     * by language plugins
     */
    protected void populateExchange(Exchange exchange) {
        Message in = exchange.getIn();
        in.setHeader("foo", "abc");
        in.setHeader("bar", 123);
        in.setBody("<hello id='m123'>world!</hello>");
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        exchange = createExchange();
        assertNotNull("No exchange created!", exchange);
        populateExchange(exchange);
    }
}
