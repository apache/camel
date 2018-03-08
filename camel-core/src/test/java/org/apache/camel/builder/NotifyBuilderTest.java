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

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version 
 */
public class NotifyBuilderTest extends ContextTestSupport {

    public void testMustBeCreated() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(1);

        try {
            notify.matches();
            fail("Should have thrown an exception");
        } catch (IllegalStateException e) {
            assertEquals("NotifyBuilder has not been created. Invoke the create() method before matching.", e.getMessage());
        }
    }

    public void testDestroyUnregistersBuilder() throws Exception {
        // Given:
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(1).create();
        // When:
        int withReg = context.getManagementStrategy().getEventNotifiers().size();
        notify.destroy();
        int afterDestroy = context.getManagementStrategy().getEventNotifiers().size();
        // Then:
        assertEquals(withReg - afterDestroy, 1);
    }

    public void testDestroyResetsBuilder() throws Exception {
        // Given:
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(1).create();
        // When:
        notify.destroy();
        // Then:
        try {
            notify.matches();
            fail("Should have thrown an exception");
        } catch (IllegalStateException e) {
            assertEquals("NotifyBuilder has not been created. Invoke the create() method before matching.", e.getMessage());
        }
    }

    public void testDestroyedBuilderCannotBeRecreated() throws Exception {
        // Given:
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(1).create();
        // When:
        notify.destroy();
        // Then:
        try {
            notify.create();
            fail("Should have thrown an exception");
        } catch (IllegalStateException e) {
            assertEquals("A destroyed NotifyBuilder cannot be re-created.", e.getMessage());
        }
    }

    public void testDirectWhenExchangeDoneSimple() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context)
                .from("direct:foo").whenDone(1)
                .create();

        assertEquals("from(direct:foo).whenDone(1)", notify.toString());

        assertEquals(false, notify.matches());

        template.sendBody("direct:foo", "A");

        assertEquals(true, notify.matches());
    }

    public void testDirectBeerWhenExchangeDoneSimple() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context)
                .from("direct:beer").whenDone(1)
                .create();

        assertEquals("from(direct:beer).whenDone(1)", notify.toString());

        assertEquals(false, notify.matches());

        template.sendBody("direct:beer", "A");

        assertEquals(true, notify.matches());
    }

    public void testDirectFromRoute() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context)
                .fromRoute("foo").whenDone(1)
                .create();

        assertEquals("fromRoute(foo).whenDone(1)", notify.toString());

        assertEquals(false, notify.matches());

        template.sendBody("direct:bar", "A");
        assertEquals(false, notify.matches());

        template.sendBody("direct:foo", "B");
        assertEquals(true, notify.matches());
    }

    public void testDirectFromRouteReceived() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context)
                .fromRoute("foo").whenReceived(1)
                .create();

        assertEquals("fromRoute(foo).whenReceived(1)", notify.toString());

        assertEquals(false, notify.matches());

        template.sendBody("direct:bar", "A");
        assertEquals(false, notify.matches());

        template.sendBody("direct:foo", "B");
        assertEquals(true, notify.matches());
    }

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

    public void testFromRouteWhenExchangeDoneAnd() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context)
                .fromRoute("foo").whenDone(5)
                .and().fromRoute("bar").whenDone(7)
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

    public void testFromRouteAndNot() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context)
                .fromRoute("foo").whenDone(2)
                .and().fromRoute("bar").whenReceived(1)
                .not().fromRoute("cake").whenDone(1)
                .create();

        assertEquals(false, notify.matches());

        template.sendBody("direct:foo", "A");
        template.sendBody("direct:foo", "B");
        assertEquals(false, notify.matches());

        template.sendBody("direct:bar", "C");
        assertEquals(true, notify.matches());

        template.sendBody("direct:foo", "D");
        template.sendBody("direct:bar", "E");
        assertEquals(true, notify.matches());

        // and now the cake to make it false
        template.sendBody("direct:cake", "F");
        assertEquals(false, notify.matches());
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
            fail("Should have thrown exception");
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
            fail("Should have thrown exception");
        } catch (Exception e) {
            // ignore
        }

        assertEquals(false, notify.matches());
    }

    public void testFilterWhenExchangeDone() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context)
                .filter(body().contains("World")).whenDone(3)
                .create();

        assertEquals("filter(simple{${body}} contains World).whenDone(3)", notify.toString());

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
            fail("Should have thrown exception");
        } catch (Exception e) {
            // ignore
        }

        try {
            template.sendBody("direct:fail", "E");
            fail("Should have thrown exception");
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
            fail("Should have thrown exception");
        } catch (Exception e) {
            // ignore
        }

        template.sendBody("direct:bar", "E");
        assertEquals(false, notify.matches());

        try {
            template.sendBody("direct:fail", "F");
            fail("Should have thrown exception");
        } catch (Exception e) {
            // ignore
        }
        assertEquals(true, notify.matches());

        template.sendBody("direct:bar", "G");
        assertEquals(true, notify.matches());

        try {
            template.sendBody("direct:fail", "H");
            fail("Should have thrown exception");
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

    public void testOneNonAbstractPredicate() throws Exception {
        try {
            new NotifyBuilder(context)
                    .wereSentTo("mock:foo")
                    .create();
            fail("Should throw exception");
        } catch (IllegalArgumentException e) {
            assertEquals("NotifyBuilder must contain at least one non-abstract predicate (such as whenDone)", e.getMessage());
        }
    }

    public void testWereSentTo() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context)
                .wereSentTo("mock:foo").whenDone(1)
                .create();

        template.sendBody("direct:bar", "Hello World");
        assertEquals(false, notify.matches());

        template.sendBody("direct:foo", "Bye World");
        assertEquals(true, notify.matches());
    }

    public void testTwoWereSentTo() throws Exception {
        // sent to both endpoints
        NotifyBuilder notify = new NotifyBuilder(context)
                .wereSentTo("log:beer").wereSentTo("mock:beer").whenDone(1)
                .create();

        template.sendBody("direct:bar", "Hello World");
        assertEquals(false, notify.matches());

        template.sendBody("direct:beer", "Bye World");
        assertEquals(true, notify.matches());
    }

    public void testWhenDoneWereSentTo() throws Exception {
        // only match when two are done and were sent to mock:beer
        NotifyBuilder notify = new NotifyBuilder(context)
                .whenDone(2).wereSentTo("mock:beer")
                .create();

        template.sendBody("direct:bar", "A");
        assertEquals(false, notify.matches());

        template.sendBody("direct:beer", "B");
        assertEquals(false, notify.matches());

        template.sendBody("direct:bar", "C");
        assertEquals(false, notify.matches());

        template.sendBody("direct:bar", "D");
        assertEquals(false, notify.matches());

        template.sendBody("direct:cake", "E");
        assertEquals(false, notify.matches());

        template.sendBody("direct:beer", "F");
        assertEquals(true, notify.matches());
    }

    public void testWereSentToWhenDone() throws Exception {
        // like the other test, but ordering of wereSentTo does not matter
        NotifyBuilder notify = new NotifyBuilder(context)
                .wereSentTo("mock:beer").whenDone(2)
                .create();

        template.sendBody("direct:bar", "A");
        assertEquals(false, notify.matches());

        template.sendBody("direct:beer", "B");
        assertEquals(false, notify.matches());

        template.sendBody("direct:bar", "C");
        assertEquals(false, notify.matches());

        template.sendBody("direct:bar", "D");
        assertEquals(false, notify.matches());

        template.sendBody("direct:cake", "E");
        assertEquals(false, notify.matches());

        template.sendBody("direct:beer", "F");
        assertEquals(true, notify.matches());
    }

    public void testTwoWereSentToRegExp() throws Exception {
        // send to any endpoint with beer in the uri
        NotifyBuilder notify = new NotifyBuilder(context)
                .wereSentTo(".*beer.*").whenDone(1)
                .create();

        template.sendBody("direct:bar", "Hello World");
        assertEquals(false, notify.matches());

        template.sendBody("direct:beer", "Bye World");
        assertEquals(true, notify.matches());
    }

    public void testTwoWereSentToDoneAndFailed() throws Exception {
        // we expect 2+ done messages which were sent to mock:bar
        // and 1+ failed message which were sent to mock:fail
        NotifyBuilder notify = new NotifyBuilder(context)
                .whenDone(2).wereSentTo("mock:bar")
                .and()
                .whenFailed(1).wereSentTo("mock:fail")
                .create();

        template.sendBody("direct:bar", "Hello World");
        assertEquals(false, notify.matches());

        template.sendBody("direct:bar", "Hello World");
        assertEquals(false, notify.matches());

        template.sendBody("direct:foo", "Hello World");
        assertEquals(false, notify.matches());

        try {
            template.sendBody("direct:fail", "Bye World");
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            // expected
        }
        assertEquals(true, notify.matches());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:foo").routeId("foo").to("mock:foo");

                from("direct:bar").routeId("bar").to("log:bar").to("mock:bar");

                from("direct:fail").routeId("fail").to("mock:fail").throwException(new IllegalArgumentException("Damn"));

                from("direct:cake").routeId("cake").transform(body().prepend("Bye ")).to("log:cake");

                from("direct:beer").routeId("beer").to("log:beer").to("mock:beer");
            }
        };
    }
}
