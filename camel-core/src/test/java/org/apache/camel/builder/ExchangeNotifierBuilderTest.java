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
package org.apache.camel.builder;

import org.apache.camel.ContextTestSupport;

/**
 * @version $Revision$
 */
public class ExchangeNotifierBuilderTest extends ContextTestSupport {

    public void testWhenExchangeDone() throws Exception {
        ExchangeNotifierBuilder builder = new ExchangeNotifierBuilder(context)
                .from("direct:foo").whenDone(5)
                .create();

        assertEquals("from(direct:foo).whenDone(5)", builder.toString());

        assertEquals(false, builder.matches());

        template.sendBody("direct:foo", "A");
        template.sendBody("direct:foo", "B");
        template.sendBody("direct:foo", "C");

        template.sendBody("direct:bar", "D");
        template.sendBody("direct:bar", "E");

        assertEquals(false, builder.matches());

        template.sendBody("direct:foo", "F");
        template.sendBody("direct:bar", "G");

        assertEquals(false, builder.matches());

        template.sendBody("direct:foo", "H");
        template.sendBody("direct:bar", "I");

        assertEquals(true, builder.matches());
    }

    public void testWhenExchangeDoneAnd() throws Exception {
        ExchangeNotifierBuilder builder = new ExchangeNotifierBuilder(context)
                .from("direct:foo").whenDone(5)
                .and().from("direct:bar").whenDone(7)
                .create();

        assertEquals(false, builder.matches());

        template.sendBody("direct:foo", "A");
        template.sendBody("direct:foo", "B");
        template.sendBody("direct:foo", "C");

        template.sendBody("direct:bar", "D");
        template.sendBody("direct:bar", "E");

        assertEquals(false, builder.matches());

        template.sendBody("direct:foo", "F");
        template.sendBody("direct:bar", "G");

        assertEquals(false, builder.matches());

        template.sendBody("direct:foo", "H");
        template.sendBody("direct:bar", "I");

        assertEquals(false, builder.matches());

        template.sendBody("direct:bar", "J");
        template.sendBody("direct:bar", "K");
        template.sendBody("direct:bar", "L");

        assertEquals(true, builder.matches());
    }

    public void testWhenExchangeDoneOr() throws Exception {
        ExchangeNotifierBuilder builder = new ExchangeNotifierBuilder(context)
                .from("direct:foo").whenDone(5)
                .or().from("direct:bar").whenDone(7)
                .create();

        assertEquals("from(direct:foo).whenDone(5).or().from(direct:bar).whenDone(7)", builder.toString());

        assertEquals(false, builder.matches());

        template.sendBody("direct:foo", "A");
        template.sendBody("direct:foo", "B");
        template.sendBody("direct:foo", "C");

        template.sendBody("direct:bar", "D");
        template.sendBody("direct:bar", "E");

        assertEquals(false, builder.matches());

        template.sendBody("direct:bar", "G");

        assertEquals(false, builder.matches());

        template.sendBody("direct:bar", "I");

        assertEquals(false, builder.matches());

        template.sendBody("direct:bar", "J");
        template.sendBody("direct:bar", "K");
        template.sendBody("direct:bar", "L");

        assertEquals(true, builder.matches());
    }

    public void testWhenExchangeDoneNot() throws Exception {
        ExchangeNotifierBuilder builder = new ExchangeNotifierBuilder(context)
                .from("direct:foo").whenDone(5)
                .not().from("direct:bar").whenDone(1)
                .create();

        assertEquals("from(direct:foo).whenDone(5).not().from(direct:bar).whenDone(1)", builder.toString());

        assertEquals(false, builder.matches());

        template.sendBody("direct:foo", "A");
        template.sendBody("direct:foo", "B");
        template.sendBody("direct:foo", "C");
        template.sendBody("direct:foo", "D");

        assertEquals(false, builder.matches());
        template.sendBody("direct:foo", "E");
        assertEquals(true, builder.matches());

        template.sendBody("direct:foo", "F");
        assertEquals(true, builder.matches());

        template.sendBody("direct:bar", "G");
        assertEquals(false, builder.matches());
    }

    public void testWhenExchangeDoneOrFailure() throws Exception {
        ExchangeNotifierBuilder builder = new ExchangeNotifierBuilder(context)
                .whenDone(5)
                .or().whenFailed(1)
                .create();

        assertEquals("whenDone(5).or().whenFailed(1)", builder.toString());

        assertEquals(false, builder.matches());

        template.sendBody("direct:foo", "A");
        template.sendBody("direct:foo", "B");
        template.sendBody("direct:foo", "D");

        assertEquals(false, builder.matches());

        try {
            template.sendBody("direct:fail", "E");
        } catch (Exception e) {
            // ignore
        }

        assertEquals(true, builder.matches());
    }

    public void testWhenExchangeDoneNotFailure() throws Exception {
        ExchangeNotifierBuilder builder = new ExchangeNotifierBuilder(context)
                .whenDone(5)
                .not().whenFailed(1)
                .create();

        assertEquals(false, builder.matches());

        template.sendBody("direct:foo", "A");
        template.sendBody("direct:foo", "B");
        template.sendBody("direct:foo", "D");
        template.sendBody("direct:bar", "E");
        template.sendBody("direct:bar", "F");

        assertEquals(true, builder.matches());

        try {
            template.sendBody("direct:fail", "G");
        } catch (Exception e) {
            // ignore
        }

        assertEquals(false, builder.matches());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:foo").to("mock:foo");

                from("direct:bar").to("mock:bar");

                from("direct:fail").throwException(new IllegalArgumentException("Damn"));
            }
        };
    }
}
