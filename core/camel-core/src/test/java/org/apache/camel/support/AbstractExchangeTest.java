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
package org.apache.camel.support;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.clock.Clock;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.DataTypeAware;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The unit test for the class {@link AbstractExchange}.
 */
public class AbstractExchangeTest {

    static class CustomAbstractExchange extends AbstractExchange {
        private final Clock clock = new MonotonicClock();

        CustomAbstractExchange(CustomAbstractExchange abstractExchange) {
            super(abstractExchange);
        }

        public CustomAbstractExchange(CamelContext context) {
            super(context);
        }

        @Override
        AbstractExchange newCopy() {
            return new CustomAbstractExchange(this);
        }

        @Override
        public Clock getClock() {
            return clock;
        }
    }

    @Test
    void shouldPreserveDataTypeOnCopy() {
        AbstractExchange e1 = new CustomAbstractExchange(new DefaultCamelContext());
        Object body1 = new Object();
        DataType type1 = new DataType("foo1");
        DefaultMessage in = new DefaultMessage((Exchange) null);
        in.setBody(body1, type1);
        e1.setIn(in);
        Object body2 = new Object();
        DataType type2 = new DataType("foo2");
        DefaultMessage out = new DefaultMessage((Exchange) null);
        out.setBody(body2, type2);
        e1.setOut(out);

        Exchange e2 = e1.copy();
        assertSame(body1, e2.getIn().getBody());
        assertInstanceOf(DataTypeAware.class, e2.getIn());
        assertSame(type1, ((DataTypeAware) e2.getIn()).getDataType());
        assertSame(body2, e2.getMessage().getBody());
        assertInstanceOf(DataTypeAware.class, e2.getMessage());
        assertSame(type2, ((DataTypeAware) e2.getMessage()).getDataType());
    }

}
