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

import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.component.mock.MockEndpoint;

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

    public void testWhenExchangeCompleted() throws Exception {
        ExchangeNotifierBuilder builder = new ExchangeNotifierBuilder(context)
                .whenCompleted(5)
                .create();

        assertEquals(false, builder.matches());

        template.sendBody("direct:foo", "A");
        template.sendBody("direct:foo", "B");
        template.sendBody("direct:bar", "C");

        try {
            template.sendBody("direct:fail", "D");
        } catch (Exception e) {
            // ignore
        }

        try {
            template.sendBody("direct:fail", "E");
        } catch (Exception e) {
            // ignore
        }

        // should NOT be completed as it only counts successful exchanges
        assertEquals(false, builder.matches());

        template.sendBody("direct:bar", "F");
        template.sendBody("direct:foo", "G");
        template.sendBody("direct:bar", "H");

        // now it should match
        assertEquals(true, builder.matches());
    }

    public void testWhenExchangeReceivedWithDelay() throws Exception {
        ExchangeNotifierBuilder builder = new ExchangeNotifierBuilder(context)
                .whenReceived(1)
                .create();

        long start = System.currentTimeMillis();
        template.sendBody("seda:cheese", "Hello Cheese");
        long end = System.currentTimeMillis();
        assertTrue("Should be faster than: " + (end-start), (end-start) < 1500);

        assertEquals(false, builder.matches());

        // should be quick as its when received and NOT when done
        assertEquals(true, builder.matches(5, TimeUnit.SECONDS));
        long end2 = System.currentTimeMillis();

        assertTrue("Should be faster than: " + (end2-start), (end2-start) < 1500);
    }

    public void testWhenExchangeDoneWithDelay() throws Exception {
        ExchangeNotifierBuilder builder = new ExchangeNotifierBuilder(context)
                .whenDone(1)
                .create();

        long start = System.currentTimeMillis();
        template.sendBody("seda:cheese", "Hello Cheese");
        long end = System.currentTimeMillis();
        assertTrue("Should be faster than: " + (end-start), (end-start) < 1500);

        assertEquals(false, builder.matches());

        // should NOT be quick as its when DONE
        assertEquals(true, builder.matches(5, TimeUnit.SECONDS));
        long end2 = System.currentTimeMillis();

        assertTrue("Should be slower than: " + (end2-start), (end2-start) > 2900);
    }

    public void testWhenExchangeDoneAndTimeoutWithDelay() throws Exception {
        ExchangeNotifierBuilder builder = new ExchangeNotifierBuilder(context)
                .whenDone(1)
                .create();

        template.sendBody("seda:cheese", "Hello Cheese");

        assertEquals(false, builder.matches());

        // should timeout
        assertEquals(false, builder.matches(1, TimeUnit.SECONDS));

        // should NOT timeout
        assertEquals(true, builder.matches(5, TimeUnit.SECONDS));
    }

    public void testWhenExchangeExactlyDone() throws Exception {
        ExchangeNotifierBuilder builder = new ExchangeNotifierBuilder(context)
                .whenExactlyDone(5)
                .create();

        assertEquals(false, builder.matches());

        template.sendBody("direct:foo", "A");
        template.sendBody("direct:foo", "B");
        template.sendBody("direct:foo", "C");

        template.sendBody("direct:bar", "D");
        assertEquals(false, builder.matches());

        template.sendBody("direct:bar", "E");
        assertEquals(true, builder.matches());

        template.sendBody("direct:foo", "F");
        assertEquals(false, builder.matches());
    }

    public void testWhenExchangeExactlyComplete() throws Exception {
        ExchangeNotifierBuilder builder = new ExchangeNotifierBuilder(context)
                .whenExactlyCompleted(5)
                .create();

        assertEquals(false, builder.matches());

        template.sendBody("direct:foo", "A");
        template.sendBody("direct:foo", "B");
        template.sendBody("direct:foo", "C");

        template.sendBody("direct:bar", "D");
        assertEquals(false, builder.matches());

        template.sendBody("direct:bar", "E");
        assertEquals(true, builder.matches());

        template.sendBody("direct:foo", "F");
        assertEquals(false, builder.matches());
    }

    public void testWhenExchangeExactlyFailed() throws Exception {
        ExchangeNotifierBuilder builder = new ExchangeNotifierBuilder(context)
                .whenExactlyFailed(2)
                .create();

        assertEquals(false, builder.matches());

        template.sendBody("direct:foo", "A");
        template.sendBody("direct:foo", "B");
        template.sendBody("direct:foo", "C");

        try {
            template.sendBody("direct:fail", "D");
        } catch (Exception e) {
            // ignore
        }

        template.sendBody("direct:bar", "E");
        assertEquals(false, builder.matches());

        try {
            template.sendBody("direct:fail", "F");
        } catch (Exception e) {
            // ignore
        }
        assertEquals(true, builder.matches());

        template.sendBody("direct:bar", "G");
        assertEquals(true, builder.matches());

        try {
            template.sendBody("direct:fail", "H");
        } catch (Exception e) {
            // ignore
        }
        assertEquals(false, builder.matches());
    }

    public void testWhenAnyReceivedMatches() throws Exception {
        ExchangeNotifierBuilder builder = new ExchangeNotifierBuilder(context)
                .whenAnyReceivedMatches(body().contains("Camel"))
                .create();

        assertEquals(false, builder.matches());

        template.sendBody("direct:foo", "Hello World");
        assertEquals(false, builder.matches());

        template.sendBody("direct:foo", "Bye World");
        assertEquals(false, builder.matches());

        template.sendBody("direct:bar", "Hello Camel");
        assertEquals(true, builder.matches());
    }

    public void testWhenAllReceivedMatches() throws Exception {
        ExchangeNotifierBuilder builder = new ExchangeNotifierBuilder(context)
                .whenAllReceivedMatches(body().contains("Camel"))
                .create();

        assertEquals(false, builder.matches());

        template.sendBody("direct:foo", "Hello Camel");
        assertEquals(true, builder.matches());

        template.sendBody("direct:foo", "Bye Camel");
        assertEquals(true, builder.matches());

        template.sendBody("direct:bar", "Hello World");
        assertEquals(false, builder.matches());
    }

    public void testWhenSatisfied() throws Exception {
        // lets use a mock to set the expressions as it got many great assertions for that
        // notice we use mock:assert which does NOT exist in the route, its just a pseudo name
        MockEndpoint mock = getMockEndpoint("mock:assert");
        mock.expectedBodiesReceivedInAnyOrder("Hello World", "Bye World", "Hi World");

        ExchangeNotifierBuilder builder = new ExchangeNotifierBuilder(context)
                .from("direct:foo").whenSatisfied(mock)
                .create();

        assertEquals(false, builder.matches());

        template.sendBody("direct:foo", "Bye World");
        assertEquals(false, builder.matches());

        template.sendBody("direct:foo", "Hello World");
        assertEquals(false, builder.matches());

        // the builder is based on direct:foo so sending to bar should not trigger match
        template.sendBody("direct:bar", "Hi World");
        assertEquals(false, builder.matches());

        template.sendBody("direct:foo", "Hi World");
        assertEquals(true, builder.matches());
    }

    public void testComplexOrCamel() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:assert");
        mock.expectedBodiesReceivedInAnyOrder("Hello World", "Bye World", "Hi World");

        ExchangeNotifierBuilder builder = new ExchangeNotifierBuilder(context)
                .from("direct:foo").whenSatisfied(mock)
                .and().from("direct:bar").whenExactlyDone(5).whenAnyReceivedMatches(body().contains("Camel"))
                .create();

        assertEquals(false, builder.matches());

        template.sendBody("direct:foo", "Bye World");
        assertEquals(false, builder.matches());

        template.sendBody("direct:foo", "Hello World");
        assertEquals(false, builder.matches());

        // the builder is based on direct:foo so sending to bar should not trigger match
        template.sendBody("direct:bar", "Hi World");
        assertEquals(false, builder.matches());

        template.sendBody("direct:foo", "Hi World");
        assertEquals(false, builder.matches());

        template.sendBody("direct:bar", "Hi Camel");
        assertEquals(false, builder.matches());

        template.sendBody("direct:bar", "A");
        template.sendBody("direct:bar", "B");
        template.sendBody("direct:bar", "C");
        assertEquals(true, builder.matches());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:foo").to("mock:foo");

                from("direct:bar").to("mock:bar");

                from("direct:fail").throwException(new IllegalArgumentException("Damn"));

                from("seda:cheese").delay(3000).to("mock:cheese");
            }
        };
    }
}
