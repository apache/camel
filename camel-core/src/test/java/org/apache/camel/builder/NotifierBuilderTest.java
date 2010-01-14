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
public class NotifierBuilderTest extends ContextTestSupport {

    public void testWhenExchangeDone() throws Exception {
        NotifierBuilder notifier = new NotifierBuilder(context)
                .from("direct:foo").whenDone(5)
                .create();

        assertEquals("from(direct:foo).whenDone(5)", notifier.toString());

        assertEquals(false, notifier.matches());

        template.sendBody("direct:foo", "A");
        template.sendBody("direct:foo", "B");
        template.sendBody("direct:foo", "C");

        template.sendBody("direct:bar", "D");
        template.sendBody("direct:bar", "E");

        assertEquals(false, notifier.matches());

        template.sendBody("direct:foo", "F");
        template.sendBody("direct:bar", "G");

        assertEquals(false, notifier.matches());

        template.sendBody("direct:foo", "H");
        template.sendBody("direct:bar", "I");

        assertEquals(true, notifier.matches());
    }

    public void testWhenExchangeDoneAnd() throws Exception {
        NotifierBuilder notifier = new NotifierBuilder(context)
                .from("direct:foo").whenDone(5)
                .and().from("direct:bar").whenDone(7)
                .create();

        assertEquals(false, notifier.matches());

        template.sendBody("direct:foo", "A");
        template.sendBody("direct:foo", "B");
        template.sendBody("direct:foo", "C");

        template.sendBody("direct:bar", "D");
        template.sendBody("direct:bar", "E");

        assertEquals(false, notifier.matches());

        template.sendBody("direct:foo", "F");
        template.sendBody("direct:bar", "G");

        assertEquals(false, notifier.matches());

        template.sendBody("direct:foo", "H");
        template.sendBody("direct:bar", "I");

        assertEquals(false, notifier.matches());

        template.sendBody("direct:bar", "J");
        template.sendBody("direct:bar", "K");
        template.sendBody("direct:bar", "L");

        assertEquals(true, notifier.matches());
    }

    public void testWhenExchangeDoneOr() throws Exception {
        NotifierBuilder notifier = new NotifierBuilder(context)
                .from("direct:foo").whenDone(5)
                .or().from("direct:bar").whenDone(7)
                .create();

        assertEquals("from(direct:foo).whenDone(5).or().from(direct:bar).whenDone(7)", notifier.toString());

        assertEquals(false, notifier.matches());

        template.sendBody("direct:foo", "A");
        template.sendBody("direct:foo", "B");
        template.sendBody("direct:foo", "C");

        template.sendBody("direct:bar", "D");
        template.sendBody("direct:bar", "E");

        assertEquals(false, notifier.matches());

        template.sendBody("direct:bar", "G");

        assertEquals(false, notifier.matches());

        template.sendBody("direct:bar", "I");

        assertEquals(false, notifier.matches());

        template.sendBody("direct:bar", "J");
        template.sendBody("direct:bar", "K");
        template.sendBody("direct:bar", "L");

        assertEquals(true, notifier.matches());
    }

    public void testWhenExchangeDoneNot() throws Exception {
        NotifierBuilder notifier = new NotifierBuilder(context)
                .from("direct:foo").whenDone(5)
                .not().from("direct:bar").whenDone(1)
                .create();

        assertEquals("from(direct:foo).whenDone(5).not().from(direct:bar).whenDone(1)", notifier.toString());

        assertEquals(false, notifier.matches());

        template.sendBody("direct:foo", "A");
        template.sendBody("direct:foo", "B");
        template.sendBody("direct:foo", "C");
        template.sendBody("direct:foo", "D");

        assertEquals(false, notifier.matches());
        template.sendBody("direct:foo", "E");
        assertEquals(true, notifier.matches());

        template.sendBody("direct:foo", "F");
        assertEquals(true, notifier.matches());

        template.sendBody("direct:bar", "G");
        assertEquals(false, notifier.matches());
    }

    public void testWhenExchangeDoneOrFailure() throws Exception {
        NotifierBuilder notifier = new NotifierBuilder(context)
                .whenDone(5)
                .or().whenFailed(1)
                .create();

        assertEquals("whenDone(5).or().whenFailed(1)", notifier.toString());

        assertEquals(false, notifier.matches());

        template.sendBody("direct:foo", "A");
        template.sendBody("direct:foo", "B");
        template.sendBody("direct:foo", "D");

        assertEquals(false, notifier.matches());

        try {
            template.sendBody("direct:fail", "E");
        } catch (Exception e) {
            // ignore
        }

        assertEquals(true, notifier.matches());
    }

    public void testWhenExchangeDoneNotFailure() throws Exception {
        NotifierBuilder notifier = new NotifierBuilder(context)
                .whenDone(5)
                .not().whenFailed(1)
                .create();

        assertEquals(false, notifier.matches());

        template.sendBody("direct:foo", "A");
        template.sendBody("direct:foo", "B");
        template.sendBody("direct:foo", "D");
        template.sendBody("direct:bar", "E");
        template.sendBody("direct:bar", "F");

        assertEquals(true, notifier.matches());

        try {
            template.sendBody("direct:fail", "G");
        } catch (Exception e) {
            // ignore
        }

        assertEquals(false, notifier.matches());
    }

    public void testWhenExchangeCompleted() throws Exception {
        NotifierBuilder notifier = new NotifierBuilder(context)
                .whenCompleted(5)
                .create();

        assertEquals(false, notifier.matches());

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
        assertEquals(false, notifier.matches());

        template.sendBody("direct:bar", "F");
        template.sendBody("direct:foo", "G");
        template.sendBody("direct:bar", "H");

        // now it should match
        assertEquals(true, notifier.matches());
    }

    public void testWhenExchangeReceivedWithDelay() throws Exception {
        NotifierBuilder notifier = new NotifierBuilder(context)
                .whenReceived(1)
                .create();

        long start = System.currentTimeMillis();
        template.sendBody("seda:cheese", "Hello Cheese");
        long end = System.currentTimeMillis();
        assertTrue("Should be faster than: " + (end - start), (end - start) < 1500);

        assertEquals(false, notifier.matches());

        // should be quick as its when received and NOT when done
        assertEquals(true, notifier.matches(5, TimeUnit.SECONDS));
        long end2 = System.currentTimeMillis();

        assertTrue("Should be faster than: " + (end2 - start), (end2 - start) < 1500);
    }

    public void testWhenExchangeDoneWithDelay() throws Exception {
        NotifierBuilder notifier = new NotifierBuilder(context)
                .whenDone(1)
                .create();

        long start = System.currentTimeMillis();
        template.sendBody("seda:cheese", "Hello Cheese");
        long end = System.currentTimeMillis();
        assertTrue("Should be faster than: " + (end - start), (end - start) < 1500);

        assertEquals(false, notifier.matches());

        // should NOT be quick as its when DONE
        assertEquals(true, notifier.matches(5, TimeUnit.SECONDS));
        long end2 = System.currentTimeMillis();

        assertTrue("Should be slower than: " + (end2 - start), (end2 - start) > 2900);
    }

    public void testWhenExchangeDoneAndTimeoutWithDelay() throws Exception {
        NotifierBuilder notifier = new NotifierBuilder(context)
                .whenDone(1)
                .create();

        template.sendBody("seda:cheese", "Hello Cheese");

        assertEquals(false, notifier.matches());

        // should timeout
        assertEquals(false, notifier.matches(1, TimeUnit.SECONDS));

        // should NOT timeout
        assertEquals(true, notifier.matches(5, TimeUnit.SECONDS));
    }

    public void testWhenExchangeExactlyDone() throws Exception {
        NotifierBuilder notifier = new NotifierBuilder(context)
                .whenExactlyDone(5)
                .create();

        assertEquals(false, notifier.matches());

        template.sendBody("direct:foo", "A");
        template.sendBody("direct:foo", "B");
        template.sendBody("direct:foo", "C");

        template.sendBody("direct:bar", "D");
        assertEquals(false, notifier.matches());

        template.sendBody("direct:bar", "E");
        assertEquals(true, notifier.matches());

        template.sendBody("direct:foo", "F");
        assertEquals(false, notifier.matches());
    }

    public void testWhenExchangeExactlyComplete() throws Exception {
        NotifierBuilder notifier = new NotifierBuilder(context)
                .whenExactlyCompleted(5)
                .create();

        assertEquals(false, notifier.matches());

        template.sendBody("direct:foo", "A");
        template.sendBody("direct:foo", "B");
        template.sendBody("direct:foo", "C");

        template.sendBody("direct:bar", "D");
        assertEquals(false, notifier.matches());

        template.sendBody("direct:bar", "E");
        assertEquals(true, notifier.matches());

        template.sendBody("direct:foo", "F");
        assertEquals(false, notifier.matches());
    }

    public void testWhenExchangeExactlyFailed() throws Exception {
        NotifierBuilder notifier = new NotifierBuilder(context)
                .whenExactlyFailed(2)
                .create();

        assertEquals(false, notifier.matches());

        template.sendBody("direct:foo", "A");
        template.sendBody("direct:foo", "B");
        template.sendBody("direct:foo", "C");

        try {
            template.sendBody("direct:fail", "D");
        } catch (Exception e) {
            // ignore
        }

        template.sendBody("direct:bar", "E");
        assertEquals(false, notifier.matches());

        try {
            template.sendBody("direct:fail", "F");
        } catch (Exception e) {
            // ignore
        }
        assertEquals(true, notifier.matches());

        template.sendBody("direct:bar", "G");
        assertEquals(true, notifier.matches());

        try {
            template.sendBody("direct:fail", "H");
        } catch (Exception e) {
            // ignore
        }
        assertEquals(false, notifier.matches());
    }

    public void testWhenAnyReceivedMatches() throws Exception {
        NotifierBuilder notifier = new NotifierBuilder(context)
                .whenAnyReceivedMatches(body().contains("Camel"))
                .create();

        assertEquals(false, notifier.matches());

        template.sendBody("direct:foo", "Hello World");
        assertEquals(false, notifier.matches());

        template.sendBody("direct:foo", "Bye World");
        assertEquals(false, notifier.matches());

        template.sendBody("direct:bar", "Hello Camel");
        assertEquals(true, notifier.matches());
    }

    public void testWhenAllReceivedMatches() throws Exception {
        NotifierBuilder notifier = new NotifierBuilder(context)
                .whenAllReceivedMatches(body().contains("Camel"))
                .create();

        assertEquals(false, notifier.matches());

        template.sendBody("direct:foo", "Hello Camel");
        assertEquals(true, notifier.matches());

        template.sendBody("direct:foo", "Bye Camel");
        assertEquals(true, notifier.matches());

        template.sendBody("direct:bar", "Hello World");
        assertEquals(false, notifier.matches());
    }

    public void testWhenReceivedSatisfied() throws Exception {
        // lets use a mock to set the expressions as it got many great assertions for that
        // notice we use mock:assert which does NOT exist in the route, its just a pseudo name
        MockEndpoint mock = getMockEndpoint("mock:assert");
        mock.expectedBodiesReceivedInAnyOrder("Hello World", "Bye World", "Hi World");

        NotifierBuilder notifier = new NotifierBuilder(context)
                .from("direct:foo").whenDoneSatisfied(mock)
                .create();

        assertEquals(false, notifier.matches());

        template.sendBody("direct:foo", "Bye World");
        assertEquals(false, notifier.matches());

        template.sendBody("direct:foo", "Hello World");
        assertEquals(false, notifier.matches());

        // the notifier  is based on direct:foo so sending to bar should not trigger match
        template.sendBody("direct:bar", "Hi World");
        assertEquals(false, notifier.matches());

        template.sendBody("direct:foo", "Hi World");
        assertEquals(true, notifier.matches());
    }

    public void testWhenReceivedNotSatisfied() throws Exception {
        // lets use a mock to set the expressions as it got many great assertions for that
        // notice we use mock:assert which does NOT exist in the route, its just a pseudo name
        MockEndpoint mock = getMockEndpoint("mock:assert");
        mock.expectedMessageCount(2);
        mock.message(1).body().contains("Camel");

        NotifierBuilder notifier = new NotifierBuilder(context)
                .from("direct:foo").whenReceivedNotSatisfied(mock)
                .create();

        // is always false to start with
        assertEquals(false, notifier.matches());

        template.sendBody("direct:foo", "Bye World");
        assertEquals(true, notifier.matches());

        template.sendBody("direct:foo", "Hello Camel");
        assertEquals(false, notifier.matches());
    }

    public void testWhenNotSatisfiedUsingSatisfied() throws Exception {
        // lets use a mock to set the expressions as it got many great assertions for that
        // notice we use mock:assert which does NOT exist in the route, its just a pseudo name
        MockEndpoint mock = getMockEndpoint("mock:assert");
        mock.expectedMessageCount(2);
        mock.message(1).body().contains("Camel");

        NotifierBuilder notifier = new NotifierBuilder(context)
                .from("direct:foo").whenReceivedSatisfied(mock)
                .create();

        assertEquals(false, notifier.matches());

        template.sendBody("direct:foo", "Bye World");
        assertEquals(false, notifier.matches());

        template.sendBody("direct:foo", "Hello Camel");
        assertEquals(true, notifier.matches());
    }

    public void testComplexOrCamel() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:assert");
        mock.expectedBodiesReceivedInAnyOrder("Hello World", "Bye World", "Hi World");

        NotifierBuilder notifier = new NotifierBuilder(context)
                .from("direct:foo").whenReceivedSatisfied(mock)
                .and().from("direct:bar").whenExactlyDone(5).whenAnyReceivedMatches(body().contains("Camel"))
                .create();

        assertEquals(false, notifier.matches());

        template.sendBody("direct:foo", "Bye World");
        assertEquals(false, notifier.matches());

        template.sendBody("direct:foo", "Hello World");
        assertEquals(false, notifier.matches());

        // the notifier  is based on direct:foo so sending to bar should not trigger match
        template.sendBody("direct:bar", "Hi World");
        assertEquals(false, notifier.matches());

        template.sendBody("direct:foo", "Hi World");
        assertEquals(false, notifier.matches());

        template.sendBody("direct:bar", "Hi Camel");
        assertEquals(false, notifier.matches());

        template.sendBody("direct:bar", "A");
        template.sendBody("direct:bar", "B");
        template.sendBody("direct:bar", "C");
        assertEquals(true, notifier.matches());
    }

    public void testWhenDoneSatisfied() throws Exception {
        // lets use a mock to set the expressions as it got many great assertions for that
        // notice we use mock:assert which does NOT exist in the route, its just a pseudo name
        MockEndpoint mock = getMockEndpoint("mock:assert");
        mock.expectedBodiesReceived("Bye World", "Bye Camel");

        NotifierBuilder notifier = new NotifierBuilder(context)
                .whenDoneSatisfied(mock)
                .create();

        // is always false to start with
        assertEquals(false, notifier.matches());

        template.requestBody("direct:cake", "World");
        assertEquals(false, notifier.matches());

        template.requestBody("direct:cake", "Camel");
        assertEquals(true, notifier.matches());

        template.requestBody("direct:cake", "Damn");
        // will still be true as the mock has been completed
        assertEquals(true, notifier.matches());
    }

    public void testWhenDoneNotSatisfied() throws Exception {
        // lets use a mock to set the expressions as it got many great assertions for that
        // notice we use mock:assert which does NOT exist in the route, its just a pseudo name
        MockEndpoint mock = getMockEndpoint("mock:assert");
        mock.expectedBodiesReceived("Bye World", "Bye Camel");

        NotifierBuilder notifier = new NotifierBuilder(context)
                .whenDoneNotSatisfied(mock)
                .create();

        // is always false to start with
        assertEquals(false, notifier.matches());

        template.requestBody("direct:cake", "World");
        assertEquals(true, notifier.matches());

        template.requestBody("direct:cake", "Camel");
        assertEquals(false, notifier.matches());

        template.requestBody("direct:cake", "Damn");
        // will still be false as the mock has been completed
        assertEquals(false, notifier.matches());
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

                from("direct:cake").transform(body().prepend("Bye "));
            }
        };
    }
}
