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
package org.apache.camel.util;

import java.util.List;

import junit.framework.TestCase;

import org.apache.camel.Exchange;
import org.apache.camel.NoSuchPropertyException;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @version $Revision: 1.1 $
 */
public class ExchangeHelperTest extends TestCase {
    private static final transient Log LOG = LogFactory.getLog(ExchangeHelperTest.class);

    protected Exchange exchange;

    public void testValidProperty() throws Exception {
        String value = ExchangeHelper.getMandatoryProperty(exchange, "foo", String.class);
        assertEquals("foo property", "123", value);
    }

    public void testMissingProperty() throws Exception {
        try {
            String value = ExchangeHelper.getMandatoryProperty(exchange, "bar", String.class);
            fail("Should have failed but got: " + value);
        } catch (NoSuchPropertyException e) {
            LOG.debug("Caught expected: " + e, e);
        }
    }

    public void testPropertyOfIncompatibleType() throws Exception {
        try {
            List value = ExchangeHelper.getMandatoryProperty(exchange, "foo", List.class);
            fail("Should have failed but got: " + value);
        } catch (NoSuchPropertyException e) {
            LOG.debug("Caught expected: " + e, e);
        }
    }

    @Override
    protected void setUp() throws Exception {
        exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.setProperty("foo", 123);
    }
}
