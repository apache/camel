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
public class NotifyBuilderTest extends ContextTestSupport {

    public void testWhenExchangeDone() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context)
                .from("direct:foo").whenDone(5)
                .create();

        assertEquals("from(direct:foo).whenDone(5)", notify.toString());

        assertEquals(false, notify.matches());

        template.sendBody("direct:foo", "A");
        template.sendBody("direct:foo", "B");
        template.sendBody("direct:foo", "C");

        template.sendBody("direct:bar", "D");
        template.sendBody("direct:bar", "E");

        assertEquals(false, notify.matches());

        template.sendBody("direct:foo", "F");
        template.sendBody("direct:bar", "G");

        assertEquals(false, notify.matches());

        template.sendBody("direct:foo", "H");
        template.sendBody("direct:bar", "I");

        assertEquals(true, notify.matches());
    }

    public void testWhenExchangeDoneAnd() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context)
                .from("direct:foo").whenDone(5)
                .and().from("direct:bar").whenDone(7)
                .create();

        assertEquals(false, notify.matches());

        template.sendBody("direct:foo", "A");
        template.sendBody("direct:foo", "B");
        template.sendBody("direct:foo", "C");

        template.sendBody("direct:bar", "D");
        template.sendBody("direct:bar", "E");

        assertEquals(false, notify.matches());

        template.sendBody("direct:foo", "F");
        template.sendBody("direct:bar", "G");

        assertEquals(false, notify.matches());

        template.sendBody("direct:foo", "H");
        template.sendBody("direct:bar", "I");

        assertEquals(false, notify.matches());

        template.sendBody("direct:bar", "J");
        template.sendBody("direct:bar", "K");
        template.sendBody("direct:bar", "L");

        assertEquals(true, notify.matches());
    }

    public void testWhenExchangeDoneOr() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context)
                .from("direct:foo").whenDone(5)
                .or().from("direct:bar").whenDone(7)
                .create();

        assertEquals("from(direct:foo).whenDone(5).or().from(direct:bar).whenDone(7)", notify.toString());

        assertEquals(false, notify.matches());

        template.sendBody("direct:foo", "A");
        template.sendBody("direct:foo", "B");
        template.sendBody("direct:foo", "C");

        template.sendBody("direct:bar", "D");
        template.sendBody("direct:bar", "E");

        assertEquals(false, notify.matches());

        template.sendBody("direct:bar", "G");

        assertEquals(false, notify.matches());

        template.sendBody("direct:bar", "I");

        assertEquals(false, notify.matches());

        template.sendBody("direct:bar", "J");
        template.sendBody("direct:bar", "K");
        template.sendBody("direct:bar", "L");

        assertEquals(true, notify.matches());
    }

    public void testWhenExchangeDoneNot() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context)
                .from("direct:foo").whenDone(5)
                .not().from("direct:bar").whenDone(1)
                .create();

        assertEquals("from(direct:foo).whenDone(5).not().from(direct:bar).whenDone(1)", notify.toString());

        assertEquals(false, notify.matches());

        template.sendBody("direct:foo", "A");
        template.sendBody("direct:foo", "B");
        template.sendBody("direct:foo", "C");
        template.sendBody("direct:foo", "D");

        assertEquals(false, notify.matches());
        template.sendBody("direct:foo", "E");
        assertEquals(true, notify.matches());

        template.sendBody("direct:foo", "F");
        assertEquals(true, notify.matches());

        template.sendBody("direct:bar", "G");
        assertEquals(false, notify.matches());
    }

    public void testWhenExchangeDoneOrFailure() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context)
                .whenDone(5)
                .or().whenFailed(1)
                .create();

        assertEquals("whenDone(5).or().whenFailed(1)", notify.toString());

        assertEquals(false, notify.matches());

        template.sendBody("direct:foo", "A");
        template.sendBody("direct:foo", "B");
        template.sendBody("direct:foo", "D");

        assertEquals(false, notify.matches());

        try {
            template.sendBody("direct:fail", "E");
        } catch (Exception e) {
            // ignore
        }

        assertEquals(true, notify.matches());
    }

    public void testWhenExchangeDoneNotFailure() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context)
                .whenDone(5)
                .not().whenFailed(1)
                .create();

        assertEquals(false, notify.matches());

        template.sendBody("direct:foo", "A");
        template.sendBody("direct:foo", "B");
        template.sendBody("direct:foo", "D");
        template.sendBody("direct:bar", "E");
        template.sendBody("direct:bar", "F");

        assertEquals(true, notify.matches());

        try {
            template.sendBody("direct:fail", "G");
        } catch (Exception e) {
            // ignore
        }

        assertEquals(false, notify.matches());
    }

    public void testFilterWhenExchangeDone() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context)
                .filter(body().contains("World")).whenDone(3)
                .create();

        assertEquals("filter(body contains World).whenDone(3)", notify.toString());

        assertEquals(false, notify.matches());

        template.sendBody("direct:foo", "Hello World");
        template.sendBody("direct:foo", "Hi World");
        template.sendBody("direct:foo", "A");

        assertEquals(false, notify.matches());

        template.sendBody("direct:bar", "B");
        template.sendBody("direct:bar", "C");

        assertEquals(false, notify.matches());

        template.sendBody("direct:bar", "Bye World");

        assertEquals(true, notify.matches());

        template.sendBody("direct:foo", "D");
        template.sendBody("direct:bar", "Hey World");

        assertEquals(true, notify.matches());
    }

    public void testFromFilterWhenExchangeDone() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context)
                .from("direct:foo").filter(body().contains("World")).whenDone(3)
                .create();

        assertEquals(false, notify.matches());

        template.sendBody("direct:foo", "Hello World");
        template.sendBody("direct:foo", "Hi World");
        template.sendBody("direct:foo", "A");

        assertEquals(false, notify.matches());

        template.sendBody("direct:bar", "B");
        template.sendBody("direct:foo", "C");

        assertEquals(false, notify.matches());

        template.sendBody("direct:bar", "Bye World");

        assertEquals(false, notify.matches());

        template.sendBody("direct:bar", "D");
        template.sendBody("direct:foo", "Hey World");

        assertEquals(true, notify.matches());

        template.sendBody("direct:bar", "E");
        template.sendBody("direct:foo", "Hi Again World");

        assertEquals(true, notify.matches());
    }

    public void testFromFilterBuilderWhenExchangeDone() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context)
                .filter().xpath("/person[@name='James']").whenDone(1)
                .create();

        assertEquals(false, notify.matches());

        template.sendBody("direct:foo", "<person name='Claus'/>");
        assertEquals(false, notify.matches());

        template.sendBody("direct:foo", "<person name='Jonathan'/>");
        assertEquals(false, notify.matches());

        template.sendBody("direct:foo", "<person name='James'/>");
        assertEquals(true, notify.matches());

        template.sendBody("direct:foo", "<person name='Hadrian'/>");
        assertEquals(true, notify.matches());
    }

    public void testWhenExchangeCompleted() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context)
                .whenCompleted(5)
                .create();

        assertEquals(false, notify.matches());

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
        assertEquals(false, notify.matches());

        template.sendBody("direct:bar", "F");
        template.sendBody("direct:foo", "G");
        template.sendBody("direct:bar", "H");

        // now it should match
        assertEquals(true, notify.matches());
    }

    public void testWhenExchangeReceivedWithDelay() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context)
                .whenReceived(1)
                .create();

        long start = System.currentTimeMillis();
        template.sendBody("seda:cheese", "Hello Cheese");
        long end = System.currentTimeMillis();
        assertTrue("Should be faster than: " + (end - start), (end - start) < 2000);

        // should be quick as its when received and NOT when done
        assertEquals(true, notify.matches(5, TimeUnit.SECONDS));
        long end2 = System.currentTimeMillis();

        assertTrue("Should be faster than: " + (end2 - start), (end2 - start) < 2000);
    }

    public void testWhenExchangeDoneWithDelay() throws Exception {
        // There are two done event, one for the exchange which is created by DefaultProducerTemplate
        // the other is for the exchange which is created by route context
        NotifyBuilder notify = new NotifyBuilder(context)
                .whenDone(2)
                .create();

        long start = System.currentTimeMillis();
        template.sendBody("seda:cheese", "Hello Cheese");
        long end = System.currentTimeMillis();
        assertTrue("Should be faster than: " + (end - start), (end - start) < 2000);

        assertEquals(false, notify.matches());

        // should NOT be quick as its when DONE
        assertEquals(true, notify.matches(5, TimeUnit.SECONDS));
        long end2 = System.currentTimeMillis();

        assertTrue("Should be slower than: " + (end2 - start), (end2 - start) > 2900);
    }

    public void testWhenExchangeDoneAndTimeoutWithDelay() throws Exception {
        // There are two done event, one for the exchange which is created by DefaultProducerTemplate
        // the other is for the exchange which is created by route context
        NotifyBuilder notify = new NotifyBuilder(context)
                .whenDone(2)
                .create();

        template.sendBody("seda:cheese", "Hello Cheese");

        assertEquals(false, notify.matches());

        // should timeout
        assertEquals(false, notify.matches(1, TimeUnit.SECONDS));

        // should NOT timeout
        assertEquals(true, notify.matches(5, TimeUnit.SECONDS));
    }

    public void testWhenExchangeExactlyDone() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context)
                .whenExactlyDone(5)
                .create();

        assertEquals(false, notify.matches());

        template.sendBody("direct:foo", "A");
        template.sendBody("direct:foo", "B");
        template.sendBody("direct:foo", "C");

        template.sendBody("direct:bar", "D");
        assertEquals(false, notify.matches());

        template.sendBody("direct:bar", "E");
        assertEquals(true, notify.matches());

        template.sendBody("direct:foo", "F");
        assertEquals(false, notify.matches());
    }

    public void testWhenExchangeExactlyComplete() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context)
                .whenExactlyCompleted(5)
                .create();

        assertEquals(false, notify.matches());

        template.sendBody("direct:foo", "A");
        template.sendBody("direct:foo", "B");
        template.sendBody("direct:foo", "C");

        template.sendBody("direct:bar", "D");
        assertEquals(false, notify.matches());

        template.sendBody("direct:bar", "E");
        assertEquals(true, notify.matches());

        template.sendBody("direct:foo", "F");
        assertEquals(false, notify.matches());
    }

    public void testWhenExchangeExactlyFailed() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context)
                .whenExactlyFailed(2)
                .create();

        assertEquals(false, notify.matches());

        template.sendBody("direct:foo", "A");
        template.sendBody("direct:foo", "B");
        template.sendBody("direct:foo", "C");

        try {
            template.sendBody("direct:fail", "D");
        } catch (Exception e) {
            // ignore
        }

        template.sendBody("direct:bar", "E");
        assertEquals(false, notify.matches());

        try {
            template.sendBody("direct:fail", "F");
        } catch (Exception e) {
            // ignore
        }
        assertEquals(true, notify.matches());

        template.sendBody("direct:bar", "G");
        assertEquals(true, notify.matches());

        try {
            template.sendBody("direct:fail", "H");
        } catch (Exception e) {
            // ignore
        }
        assertEquals(false, notify.matches());
    }

    public void testWhenAnyReceivedMatches() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context)
                .whenAnyReceivedMatches(body().contains("Camel"))
                .create();

        assertEquals(false, notify.matches());

        template.sendBody("direct:foo", "Hello World");
        assertEquals(false, notify.matches());

        template.sendBody("direct:foo", "Bye World");
        assertEquals(false, notify.matches());

        template.sendBody("direct:bar", "Hello Camel");
        assertEquals(true, notify.matches());
    }

    public void testWhenAllReceivedMatches() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context)
                .whenAllReceivedMatches(body().contains("Camel"))
                .create();

        assertEquals(false, notify.matches());

        template.sendBody("direct:foo", "Hello Camel");
        assertEquals(true, notify.matches());

        template.sendBody("direct:foo", "Bye Camel");
        assertEquals(true, notify.matches());

        template.sendBody("direct:bar", "Hello World");
        assertEquals(false, notify.matches());
    }

    public void testWhenAnyDoneMatches() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context)
                .whenAnyDoneMatches(body().contains("Bye"))
                .create();

        assertEquals(false, notify.matches());

        template.sendBody("direct:foo", "Hi World");
        assertEquals(false, notify.matches());

        template.sendBody("direct:cake", "Camel");
        assertEquals(true, notify.matches());

        template.sendBody("direct:foo", "Damn World");
        assertEquals(true, notify.matches());
    }

    public void testWhenAllDoneMatches() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context)
                .whenAllDoneMatches(body().contains("Bye"))
                .create();

        assertEquals(false, notify.matches());

        template.sendBody("direct:cake", "Camel");
        assertEquals(true, notify.matches());

        template.sendBody("direct:cake", "World");
        assertEquals(true, notify.matches());

        template.sendBody("direct:foo", "Hi World");
        assertEquals(false, notify.matches());
    }

    public void testWhenBodiesReceived() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context)
                .whenBodiesReceived("Hi World", "Hello World")
                .create();

        assertEquals(false, notify.matches());

        template.sendBody("direct:foo", "Hi World");
        assertEquals(false, notify.matches());

        template.sendBody("direct:foo", "Hello World");
        assertEquals(true, notify.matches());

        // should keep being true
        template.sendBody("direct:foo", "Bye World");
        assertEquals(true, notify.matches());

        template.sendBody("direct:foo", "Damn World");
        assertEquals(true, notify.matches());
    }

    public void testWhenBodiesDone() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context)
                .whenBodiesDone("Bye World", "Bye Camel")
                .create();

        assertEquals(false, notify.matches());

        template.requestBody("direct:cake", "World");
        assertEquals(false, notify.matches());

        template.sendBody("direct:cake", "Camel");
        assertEquals(true, notify.matches());

        // should keep being true
        template.sendBody("direct:foo", "Damn World");
        assertEquals(true, notify.matches());
    }

    public void testWhenExactBodiesReceived() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context)
                .whenExactBodiesReceived("Hi World", "Hello World")
                .create();

        assertEquals(false, notify.matches());

        template.sendBody("direct:foo", "Hi World");
        assertEquals(false, notify.matches());

        template.sendBody("direct:foo", "Hello World");
        assertEquals(true, notify.matches());

        // should not keep being true
        template.sendBody("direct:foo", "Bye World");
        assertEquals(false, notify.matches());

        template.sendBody("direct:foo", "Damn World");
        assertEquals(false, notify.matches());
    }

    public void testWhenExactBodiesDone() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context)
                .whenExactBodiesDone("Bye World", "Bye Camel")
                .create();

        assertEquals(false, notify.matches());

        template.requestBody("direct:cake", "World");
        assertEquals(false, notify.matches());

        template.sendBody("direct:cake", "Camel");
        assertEquals(true, notify.matches());

        // should NOT keep being true
        template.sendBody("direct:foo", "Damn World");
        assertEquals(false, notify.matches());
    }

    public void testWhenReceivedSatisfied() throws Exception {
        // lets use a mock to set the expressions as it got many great assertions for that
        // notice we use mock:assert which does NOT exist in the route, its just a pseudo name
        MockEndpoint mock = getMockEndpoint("mock:assert");
        mock.expectedBodiesReceivedInAnyOrder("Hello World", "Bye World", "Hi World");

        NotifyBuilder notify = new NotifyBuilder(context)
                .from("direct:foo").whenDoneSatisfied(mock)
                .create();

        assertEquals(false, notify.matches());

        template.sendBody("direct:foo", "Bye World");
        assertEquals(false, notify.matches());

        template.sendBody("direct:foo", "Hello World");
        assertEquals(false, notify.matches());

        // the notify  is based on direct:foo so sending to bar should not trigger match
        template.sendBody("direct:bar", "Hi World");
        assertEquals(false, notify.matches());

        template.sendBody("direct:foo", "Hi World");
        assertEquals(true, notify.matches());
    }

    public void testWhenReceivedNotSatisfied() throws Exception {
        // lets use a mock to set the expressions as it got many great assertions for that
        // notice we use mock:assert which does NOT exist in the route, its just a pseudo name
        MockEndpoint mock = getMockEndpoint("mock:assert");
        mock.expectedMessageCount(2);
        mock.message(1).body().contains("Camel");

        NotifyBuilder notify = new NotifyBuilder(context)
                .from("direct:foo").whenReceivedNotSatisfied(mock)
                .create();

        // is always false to start with
        assertEquals(false, notify.matches());

        template.sendBody("direct:foo", "Bye World");
        assertEquals(true, notify.matches());

        template.sendBody("direct:foo", "Hello Camel");
        assertEquals(false, notify.matches());
    }

    public void testWhenNotSatisfiedUsingSatisfied() throws Exception {
        // lets use a mock to set the expressions as it got many great assertions for that
        // notice we use mock:assert which does NOT exist in the route, its just a pseudo name
        MockEndpoint mock = getMockEndpoint("mock:assert");
        mock.expectedMessageCount(2);
        mock.message(1).body().contains("Camel");

        NotifyBuilder notify = new NotifyBuilder(context)
                .from("direct:foo").whenReceivedSatisfied(mock)
                .create();

        assertEquals(false, notify.matches());

        template.sendBody("direct:foo", "Bye World");
        assertEquals(false, notify.matches());

        template.sendBody("direct:foo", "Hello Camel");
        assertEquals(true, notify.matches());
    }

    public void testComplexOrCamel() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:assert");
        mock.expectedBodiesReceivedInAnyOrder("Hello World", "Bye World", "Hi World");

        NotifyBuilder notify = new NotifyBuilder(context)
                .from("direct:foo").whenReceivedSatisfied(mock)
                .and().from("direct:bar").whenExactlyDone(5).whenAnyReceivedMatches(body().contains("Camel"))
                .create();

        assertEquals(false, notify.matches());

        template.sendBody("direct:foo", "Bye World");
        assertEquals(false, notify.matches());

        template.sendBody("direct:foo", "Hello World");
        assertEquals(false, notify.matches());

        // the notify  is based on direct:foo so sending to bar should not trigger match
        template.sendBody("direct:bar", "Hi World");
        assertEquals(false, notify.matches());

        template.sendBody("direct:foo", "Hi World");
        assertEquals(false, notify.matches());

        template.sendBody("direct:bar", "Hi Camel");
        assertEquals(false, notify.matches());

        template.sendBody("direct:bar", "A");
        template.sendBody("direct:bar", "B");
        template.sendBody("direct:bar", "C");
        assertEquals(true, notify.matches());
    }

    public void testWhenDoneSatisfied() throws Exception {
        // lets use a mock to set the expressions as it got many great assertions for that
        // notice we use mock:assert which does NOT exist in the route, its just a pseudo name
        MockEndpoint mock = getMockEndpoint("mock:assert");
        mock.expectedBodiesReceived("Bye World", "Bye Camel");

        NotifyBuilder notify = new NotifyBuilder(context)
                .whenDoneSatisfied(mock)
                .create();

        // is always false to start with
        assertEquals(false, notify.matches());

        template.requestBody("direct:cake", "World");
        assertEquals(false, notify.matches());

        template.requestBody("direct:cake", "Camel");
        assertEquals(true, notify.matches());

        template.requestBody("direct:cake", "Damn");
        // will still be true as the mock has been completed
        assertEquals(true, notify.matches());
    }

    public void testWhenDoneNotSatisfied() throws Exception {
        // lets use a mock to set the expressions as it got many great assertions for that
        // notice we use mock:assert which does NOT exist in the route, its just a pseudo name
        MockEndpoint mock = getMockEndpoint("mock:assert");
        mock.expectedBodiesReceived("Bye World", "Bye Camel");

        NotifyBuilder notify = new NotifyBuilder(context)
                .whenDoneNotSatisfied(mock)
                .create();

        // is always false to start with
        assertEquals(false, notify.matches());

        template.requestBody("direct:cake", "World");
        assertEquals(true, notify.matches());

        template.requestBody("direct:cake", "Camel");
        assertEquals(false, notify.matches());

        template.requestBody("direct:cake", "Damn");
        // will still be false as the mock has been completed
        assertEquals(false, notify.matches());
    }

    public void testReset() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context)
                .whenExactlyDone(1)
                .create();

        template.sendBody("direct:foo", "Hello World");
        assertEquals(true, notify.matches());

        template.sendBody("direct:foo", "Bye World");
        assertEquals(false, notify.matches());

        // reset
        notify.reset();
        assertEquals(false, notify.matches());

        template.sendBody("direct:foo", "Hello World");
        assertEquals(true, notify.matches());

        template.sendBody("direct:foo", "Bye World");
        assertEquals(false, notify.matches());
    }

    public void testResetBodiesReceived() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context)
                .whenBodiesReceived("Hello World", "Bye World")
                .create();

        template.sendBody("direct:foo", "Hello World");
        template.sendBody("direct:foo", "Bye World");
        assertEquals(true, notify.matches());

        // reset
        notify.reset();
        assertEquals(false, notify.matches());

        template.sendBody("direct:foo", "Hello World");
        assertEquals(false, notify.matches());

        template.sendBody("direct:foo", "Bye World");
        assertEquals(true, notify.matches());
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

                from("direct:cake").transform(body().prepend("Bye ")).to("log:cake");
            }
        };
    }
}
