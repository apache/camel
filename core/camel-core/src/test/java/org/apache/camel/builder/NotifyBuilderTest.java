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
package org.apache.camel.builder;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NotifyBuilderTest extends ContextTestSupport {

    @Test
    public void testMustBeCreated() {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(1);

        Exception e = assertThrows(IllegalStateException.class, notify::matches, "Should have thrown an exception");
        assertEquals("NotifyBuilder has not been created. Invoke the create() method before matching.", e.getMessage());
    }

    @Test
    public void testDestroyUnregistersBuilder() throws Exception {
        // Given:
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(1).create();
        // When:
        int withReg = context.getManagementStrategy().getEventNotifiers().size();
        notify.destroy();
        int afterDestroy = context.getManagementStrategy().getEventNotifiers().size();
        // Then:
        assertEquals(1, withReg - afterDestroy);
    }

    @Test
    public void testDestroyResetsBuilder() {
        // Given:
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(1).create();
        // When:
        notify.destroy();
        //Then
        Exception e = assertThrows(IllegalStateException.class, notify::matches, "Should have thrown an exception");
        assertEquals("NotifyBuilder has not been created. Invoke the create() method before matching.", e.getMessage());
    }

    @Test
    public void testDestroyedBuilderCannotBeRecreated() {
        // Given:
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(1).create();
        // When:
        notify.destroy();
        // Then:
        Exception e = assertThrows(IllegalStateException.class, notify::create, "Should have thrown an exception");
        assertEquals("A destroyed NotifyBuilder cannot be re-created.", e.getMessage());
    }

    @Test
    public void testDirectWhenExchangeDoneSimple() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).from("direct:foo").whenDone(1).create();

        assertEquals("from(direct:foo).whenDone(1)", notify.toString());

        assertFalse(notify.matches());

        template.sendBody("direct:foo", "A");

        assertTrue(notify.matches());
    }

    @Test
    public void testDirectBeerWhenExchangeDoneSimple() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).from("direct:beer").whenDone(1).create();

        assertEquals("from(direct:beer).whenDone(1)", notify.toString());

        assertFalse(notify.matches());

        template.sendBody("direct:beer", "A");

        assertTrue(notify.matches());
    }

    @Test
    public void testDirectFromRoute() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).fromRoute("foo").whenDone(1).create();

        assertEquals("fromRoute(foo).whenDone(1)", notify.toString());

        assertFalse(notify.matches());

        template.sendBody("direct:bar", "A");
        assertFalse(notify.matches());

        template.sendBody("direct:foo", "B");
        assertTrue(notify.matches());
    }

    @Test
    public void testDirectFromRouteReceived() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).fromRoute("foo").whenReceived(1).create();

        assertEquals("fromRoute(foo).whenReceived(1)", notify.toString());

        assertFalse(notify.matches());

        template.sendBody("direct:bar", "A");
        assertFalse(notify.matches());

        template.sendBody("direct:foo", "B");
        assertTrue(notify.matches());
    }

    @Test
    public void testWhenExchangeDone() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).from("direct:foo").whenDone(5).create();

        assertEquals("from(direct:foo).whenDone(5)", notify.toString());

        assertFalse(notify.matches());

        template.sendBody("direct:foo", "A");
        template.sendBody("direct:foo", "B");
        template.sendBody("direct:foo", "C");

        template.sendBody("direct:bar", "D");
        template.sendBody("direct:bar", "E");

        assertFalse(notify.matches());

        template.sendBody("direct:foo", "F");
        template.sendBody("direct:bar", "G");

        assertFalse(notify.matches());

        template.sendBody("direct:foo", "H");
        template.sendBody("direct:bar", "I");

        assertTrue(notify.matches());
    }

    @Test
    public void testWhenExchangeDoneAnd() throws Exception {
        NotifyBuilder notify
                = new NotifyBuilder(context).from("direct:foo").whenDone(5).and().from("direct:bar").whenDone(7).create();

        assertFalse(notify.matches());

        template.sendBody("direct:foo", "A");
        template.sendBody("direct:foo", "B");
        template.sendBody("direct:foo", "C");

        template.sendBody("direct:bar", "D");
        template.sendBody("direct:bar", "E");

        assertFalse(notify.matches());

        template.sendBody("direct:foo", "F");
        template.sendBody("direct:bar", "G");

        assertFalse(notify.matches());

        template.sendBody("direct:foo", "H");
        template.sendBody("direct:bar", "I");

        assertFalse(notify.matches());

        template.sendBody("direct:bar", "J");
        template.sendBody("direct:bar", "K");
        template.sendBody("direct:bar", "L");

        assertTrue(notify.matches());
    }

    @Test
    public void testFromRouteWhenExchangeDoneAnd() throws Exception {
        NotifyBuilder notify
                = new NotifyBuilder(context).fromRoute("foo").whenDone(5).and().fromRoute("bar").whenDone(7).create();

        assertFalse(notify.matches());

        template.sendBody("direct:foo", "A");
        template.sendBody("direct:foo", "B");
        template.sendBody("direct:foo", "C");

        template.sendBody("direct:bar", "D");
        template.sendBody("direct:bar", "E");

        assertFalse(notify.matches());

        template.sendBody("direct:foo", "F");
        template.sendBody("direct:bar", "G");

        assertFalse(notify.matches());

        template.sendBody("direct:foo", "H");
        template.sendBody("direct:bar", "I");

        assertFalse(notify.matches());

        template.sendBody("direct:bar", "J");
        template.sendBody("direct:bar", "K");
        template.sendBody("direct:bar", "L");

        assertTrue(notify.matches());
    }

    @Test
    public void testFromRouteAndNot() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).fromRoute("foo").whenDone(2).and().fromRoute("bar").whenReceived(1)
                .not().fromRoute("cake").whenDone(1).create();

        assertFalse(notify.matches());

        template.sendBody("direct:foo", "A");
        template.sendBody("direct:foo", "B");
        assertFalse(notify.matches());

        template.sendBody("direct:bar", "C");
        assertTrue(notify.matches());

        template.sendBody("direct:foo", "D");
        template.sendBody("direct:bar", "E");
        assertTrue(notify.matches());

        // and now the cake to make it false
        template.sendBody("direct:cake", "F");
        assertFalse(notify.matches());
    }

    @Test
    public void testWhenExchangeDoneOr() throws Exception {
        NotifyBuilder notify
                = new NotifyBuilder(context).from("direct:foo").whenDone(5).or().from("direct:bar").whenDone(7).create();

        assertEquals("from(direct:foo).whenDone(5).or().from(direct:bar).whenDone(7)", notify.toString());

        assertFalse(notify.matches());

        template.sendBody("direct:foo", "A");
        template.sendBody("direct:foo", "B");
        template.sendBody("direct:foo", "C");

        template.sendBody("direct:bar", "D");
        template.sendBody("direct:bar", "E");

        assertFalse(notify.matches());

        template.sendBody("direct:bar", "G");

        assertFalse(notify.matches());

        template.sendBody("direct:bar", "I");

        assertFalse(notify.matches());

        template.sendBody("direct:bar", "J");
        template.sendBody("direct:bar", "K");
        template.sendBody("direct:bar", "L");

        assertTrue(notify.matches());
    }

    @Test
    public void testWhenExchangeDoneNot() throws Exception {
        NotifyBuilder notify
                = new NotifyBuilder(context).from("direct:foo").whenDone(5).not().from("direct:bar").whenDone(1).create();

        assertEquals("from(direct:foo).whenDone(5).not().from(direct:bar).whenDone(1)", notify.toString());

        assertFalse(notify.matches());

        template.sendBody("direct:foo", "A");
        template.sendBody("direct:foo", "B");
        template.sendBody("direct:foo", "C");
        template.sendBody("direct:foo", "D");

        assertFalse(notify.matches());
        template.sendBody("direct:foo", "E");
        assertTrue(notify.matches());

        template.sendBody("direct:foo", "F");
        assertTrue(notify.matches());

        template.sendBody("direct:bar", "G");
        assertFalse(notify.matches());
    }

    @Test
    public void testWhenExchangeDoneOrFailure() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(5).or().whenFailed(1).create();

        assertEquals("whenDone(5).or().whenFailed(1)", notify.toString());

        assertFalse(notify.matches());

        template.sendBody("direct:foo", "A");
        template.sendBody("direct:foo", "B");
        template.sendBody("direct:foo", "D");

        assertFalse(notify.matches());

        assertThrows(Exception.class, () -> template.sendBody("direct:fail", "E"), "Should have thrown exception");

        assertTrue(notify.matches());
    }

    @Test
    public void testWhenExchangeDoneNotFailure() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(5).not().whenFailed(1).create();

        assertFalse(notify.matches());

        template.sendBody("direct:foo", "A");
        template.sendBody("direct:foo", "B");
        template.sendBody("direct:foo", "D");
        template.sendBody("direct:bar", "E");
        template.sendBody("direct:bar", "F");

        assertTrue(notify.matches());

        assertThrows(Exception.class, () -> template.sendBody("direct:fail", "G"), "Should have thrown exception");

        assertFalse(notify.matches());
    }

    @Test
    public void testFilterWhenExchangeDone() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).filter(body().contains("World")).whenDone(3).create();

        assertEquals("filter(body contains World).whenDone(3)", notify.toString());

        assertFalse(notify.matches());

        template.sendBody("direct:foo", "Hello World");
        template.sendBody("direct:foo", "Hi World");
        template.sendBody("direct:foo", "A");

        assertFalse(notify.matches());

        template.sendBody("direct:bar", "B");
        template.sendBody("direct:bar", "C");

        assertFalse(notify.matches());

        template.sendBody("direct:bar", "Bye World");

        assertTrue(notify.matches());

        template.sendBody("direct:foo", "D");
        template.sendBody("direct:bar", "Hey World");

        assertTrue(notify.matches());
    }

    @Test
    public void testFromFilterWhenExchangeDone() throws Exception {
        NotifyBuilder notify
                = new NotifyBuilder(context).from("direct:foo").filter(body().contains("World")).whenDone(3).create();

        assertFalse(notify.matches());

        template.sendBody("direct:foo", "Hello World");
        template.sendBody("direct:foo", "Hi World");
        template.sendBody("direct:foo", "A");

        assertFalse(notify.matches());

        template.sendBody("direct:bar", "B");
        template.sendBody("direct:foo", "C");

        assertFalse(notify.matches());

        template.sendBody("direct:bar", "Bye World");

        assertFalse(notify.matches());

        template.sendBody("direct:bar", "D");
        template.sendBody("direct:foo", "Hey World");

        assertTrue(notify.matches());

        template.sendBody("direct:bar", "E");
        template.sendBody("direct:foo", "Hi Again World");

        assertTrue(notify.matches());
    }

    @Test
    public void testFromFilterBuilderWhenExchangeDone() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).filter().xpath("/person[@name='James']").whenDone(1).create();

        assertFalse(notify.matches());

        template.sendBody("direct:foo", "<person name='Claus'/>");
        assertFalse(notify.matches());

        template.sendBody("direct:foo", "<person name='Jonathan'/>");
        assertFalse(notify.matches());

        template.sendBody("direct:foo", "<person name='James'/>");
        assertTrue(notify.matches());

        template.sendBody("direct:foo", "<person name='Hadrian'/>");
        assertTrue(notify.matches());
    }

    @Test
    public void testWhenExchangeCompleted() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenCompleted(5).create();

        assertFalse(notify.matches());

        template.sendBody("direct:foo", "A");
        template.sendBody("direct:foo", "B");
        template.sendBody("direct:bar", "C");

        assertThrows(Exception.class, () -> template.sendBody("direct:fail", "D"), "Should have thrown exception");
        assertThrows(Exception.class, () -> template.sendBody("direct:fail", "E"), "Should have thrown exception");

        // should NOT be completed as it only counts successful exchanges
        assertFalse(notify.matches());

        template.sendBody("direct:bar", "F");
        template.sendBody("direct:foo", "G");
        template.sendBody("direct:bar", "H");

        // now it should match
        assertTrue(notify.matches());
    }

    @Test
    public void testWhenExchangeExactlyDone() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenExactlyDone(5).create();

        assertFalse(notify.matches());

        template.sendBody("direct:foo", "A");
        template.sendBody("direct:foo", "B");
        template.sendBody("direct:foo", "C");

        template.sendBody("direct:bar", "D");
        assertFalse(notify.matches());

        template.sendBody("direct:bar", "E");
        assertTrue(notify.matches());

        template.sendBody("direct:foo", "F");
        assertFalse(notify.matches());
    }

    @Test
    public void testWhenExchangeExactlyComplete() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenExactlyCompleted(5).create();

        assertFalse(notify.matches());

        template.sendBody("direct:foo", "A");
        template.sendBody("direct:foo", "B");
        template.sendBody("direct:foo", "C");

        template.sendBody("direct:bar", "D");
        assertFalse(notify.matches());

        template.sendBody("direct:bar", "E");
        assertTrue(notify.matches());

        template.sendBody("direct:foo", "F");
        assertFalse(notify.matches());
    }

    @Test
    public void testWhenExchangeExactlyFailed() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenExactlyFailed(2).create();

        assertFalse(notify.matches());

        template.sendBody("direct:foo", "A");
        template.sendBody("direct:foo", "B");
        template.sendBody("direct:foo", "C");

        assertThrows(Exception.class, () -> template.sendBody("direct:fail", "D"), "Should have thrown exception");

        template.sendBody("direct:bar", "E");
        assertFalse(notify.matches());

        assertThrows(Exception.class, () -> template.sendBody("direct:fail", "F"), "Should have thrown exception");

        assertTrue(notify.matches());

        template.sendBody("direct:bar", "G");
        assertTrue(notify.matches());

        assertThrows(Exception.class, () -> template.sendBody("direct:fail", "H"), "Should have thrown exception");
        assertFalse(notify.matches());
    }

    @Test
    public void testWhenAnyReceivedMatches() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenAnyReceivedMatches(body().contains("Camel")).create();

        assertFalse(notify.matches());

        template.sendBody("direct:foo", "Hello World");
        assertFalse(notify.matches());

        template.sendBody("direct:foo", "Bye World");
        assertFalse(notify.matches());

        template.sendBody("direct:bar", "Hello Camel");
        assertTrue(notify.matches());
    }

    @Test
    public void testWhenAllReceivedMatches() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenAllReceivedMatches(body().contains("Camel")).create();

        assertFalse(notify.matches());

        template.sendBody("direct:foo", "Hello Camel");
        assertTrue(notify.matches());

        template.sendBody("direct:foo", "Bye Camel");
        assertTrue(notify.matches());

        template.sendBody("direct:bar", "Hello World");
        assertFalse(notify.matches());
    }

    @Test
    public void testWhenAnyDoneMatches() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenAnyDoneMatches(body().contains("Bye")).create();

        assertFalse(notify.matches());

        template.sendBody("direct:foo", "Hi World");
        assertFalse(notify.matches());

        template.sendBody("direct:cake", "Camel");
        assertTrue(notify.matches());

        template.sendBody("direct:foo", "Damn World");
        assertTrue(notify.matches());
    }

    @Test
    public void testWhenAllDoneMatches() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenAllDoneMatches(body().contains("Bye")).create();

        assertFalse(notify.matches());

        template.sendBody("direct:cake", "Camel");
        assertTrue(notify.matches());

        template.sendBody("direct:cake", "World");
        assertTrue(notify.matches());

        template.sendBody("direct:foo", "Hi World");
        assertFalse(notify.matches());
    }

    @Test
    public void testWhenBodiesReceived() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenBodiesReceived("Hi World", "Hello World").create();

        assertFalse(notify.matches());

        template.sendBody("direct:foo", "Hi World");
        assertFalse(notify.matches());

        template.sendBody("direct:foo", "Hello World");
        assertTrue(notify.matches());

        // should keep being true
        template.sendBody("direct:foo", "Bye World");
        assertTrue(notify.matches());

        template.sendBody("direct:foo", "Damn World");
        assertTrue(notify.matches());
    }

    @Test
    public void testWhenBodiesDone() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenBodiesDone("Bye World", "Bye Camel").create();

        assertFalse(notify.matches());

        template.requestBody("direct:cake", "World");
        assertFalse(notify.matches());

        template.sendBody("direct:cake", "Camel");
        assertTrue(notify.matches());

        // should keep being true
        template.sendBody("direct:foo", "Damn World");
        assertTrue(notify.matches());
    }

    @Test
    public void testWhenExactBodiesReceived() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenExactBodiesReceived("Hi World", "Hello World").create();

        assertFalse(notify.matches());

        template.sendBody("direct:foo", "Hi World");
        assertFalse(notify.matches());

        template.sendBody("direct:foo", "Hello World");
        assertTrue(notify.matches());

        // should not keep being true
        template.sendBody("direct:foo", "Bye World");
        assertFalse(notify.matches());

        template.sendBody("direct:foo", "Damn World");
        assertFalse(notify.matches());
    }

    @Test
    public void testWhenExactBodiesDone() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenExactBodiesDone("Bye World", "Bye Camel").create();

        assertFalse(notify.matches());

        template.requestBody("direct:cake", "World");
        assertFalse(notify.matches());

        template.sendBody("direct:cake", "Camel");
        assertTrue(notify.matches());

        // should NOT keep being true
        template.sendBody("direct:foo", "Damn World");
        assertFalse(notify.matches());
    }

    @Test
    public void testWhenReceivedSatisfied() throws Exception {
        // lets use a mock to set the expressions as it got many great
        // assertions for that
        // notice we use mock:assert which does NOT exist in the route, its just
        // a pseudo name
        MockEndpoint mock = getMockEndpoint("mock:assert");
        mock.expectedBodiesReceivedInAnyOrder("Hello World", "Bye World", "Hi World");

        NotifyBuilder notify = new NotifyBuilder(context).from("direct:foo").whenDoneSatisfied(mock).create();

        assertFalse(notify.matches());

        template.sendBody("direct:foo", "Bye World");
        assertFalse(notify.matches());

        template.sendBody("direct:foo", "Hello World");
        assertFalse(notify.matches());

        // the notify is based on direct:foo so sending to bar should not
        // trigger match
        template.sendBody("direct:bar", "Hi World");
        assertFalse(notify.matches());

        template.sendBody("direct:foo", "Hi World");
        assertTrue(notify.matches());
    }

    @Test
    public void testWhenReceivedSatisfiedFalse() throws Exception {
        // lets use a mock to set the expressions as it got many great
        // assertions for that
        // notice we use mock:assert which does NOT exist in the route, its just
        // a pseudo name
        MockEndpoint mock = getMockEndpoint("mock:assert");
        mock.expectedBodiesReceivedInAnyOrder("Hello World", "Bye World", "Does not happen", "Hi World");

        NotifyBuilder notify = new NotifyBuilder(context).from("direct:foo").whenDoneSatisfied(mock).create();

        assertFalse(notify.matches());

        template.sendBody("direct:foo", "Bye World");
        assertFalse(notify.matches());

        template.sendBody("direct:foo", "Hello World");
        assertFalse(notify.matches());

        // the notify is based on direct:foo so sending to bar should not
        // trigger match
        template.sendBody("direct:bar", "Hi World");
        assertFalse(notify.matches());

        template.sendBody("direct:foo", "Hi World");
        assertFalse(notify.matches());
    }

    @Test
    public void testWhenReceivedNotSatisfied() throws Exception {
        // lets use a mock to set the expressions as it got many great
        // assertions for that
        // notice we use mock:assert which does NOT exist in the route, its just
        // a pseudo name
        MockEndpoint mock = getMockEndpoint("mock:assert");
        mock.expectedMessageCount(2);
        mock.message(1).body().contains("Camel");

        NotifyBuilder notify = new NotifyBuilder(context).from("direct:foo").whenReceivedNotSatisfied(mock).create();

        // is always false to start with
        assertFalse(notify.matches());

        template.sendBody("direct:foo", "Bye World");
        assertTrue(notify.matches());

        template.sendBody("direct:foo", "Hello Camel");
        assertFalse(notify.matches());
    }

    @Test
    public void testWhenNotSatisfiedUsingSatisfied() throws Exception {
        // lets use a mock to set the expressions as it got many great
        // assertions for that
        // notice we use mock:assert which does NOT exist in the route, its just
        // a pseudo name
        MockEndpoint mock = getMockEndpoint("mock:assert");
        mock.expectedMessageCount(2);
        mock.message(1).body().contains("Camel");

        NotifyBuilder notify = new NotifyBuilder(context).from("direct:foo").whenReceivedSatisfied(mock).create();

        assertFalse(notify.matches());

        template.sendBody("direct:foo", "Bye World");
        assertFalse(notify.matches());

        template.sendBody("direct:foo", "Hello Camel");
        assertTrue(notify.matches());
    }

    @Test
    public void testComplexOrCamel() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:assert");
        mock.expectedBodiesReceivedInAnyOrder("Hello World", "Bye World", "Hi World");

        NotifyBuilder notify = new NotifyBuilder(context).from("direct:foo").whenReceivedSatisfied(mock).and()
                .from("direct:bar").whenExactlyDone(5)
                .whenAnyReceivedMatches(body().contains("Camel")).create();

        assertFalse(notify.matches());

        template.sendBody("direct:foo", "Bye World");
        assertFalse(notify.matches());

        template.sendBody("direct:foo", "Hello World");
        assertFalse(notify.matches());

        // the notify is based on direct:foo so sending to bar should not
        // trigger match
        template.sendBody("direct:bar", "Hi World");
        assertFalse(notify.matches());

        template.sendBody("direct:foo", "Hi World");
        assertFalse(notify.matches());

        template.sendBody("direct:bar", "Hi Camel");
        assertFalse(notify.matches());

        template.sendBody("direct:bar", "A");
        template.sendBody("direct:bar", "B");
        template.sendBody("direct:bar", "C");
        assertTrue(notify.matches());
    }

    @Test
    public void testWhenDoneSatisfied() throws Exception {
        // lets use a mock to set the expressions as it got many great
        // assertions for that
        // notice we use mock:assert which does NOT exist in the route, its just
        // a pseudo name
        MockEndpoint mock = getMockEndpoint("mock:assert");
        mock.expectedBodiesReceived("Bye World", "Bye Camel");

        NotifyBuilder notify = new NotifyBuilder(context).whenDoneSatisfied(mock).create();

        // is always false to start with
        assertFalse(notify.matches());

        template.requestBody("direct:cake", "World");
        assertFalse(notify.matches());

        template.requestBody("direct:cake", "Camel");
        assertTrue(notify.matches());

        template.requestBody("direct:cake", "Damn");
        // will still be true as the mock has been completed
        assertTrue(notify.matches());
    }

    @Test
    public void testWhenDoneNotSatisfied() throws Exception {
        // lets use a mock to set the expressions as it got many great
        // assertions for that
        // notice we use mock:assert which does NOT exist in the route, its just
        // a pseudo name
        MockEndpoint mock = getMockEndpoint("mock:assert");
        mock.expectedBodiesReceived("Bye World", "Bye Camel");

        NotifyBuilder notify = new NotifyBuilder(context).whenDoneNotSatisfied(mock).create();

        // is always false to start with
        assertFalse(notify.matches());

        template.requestBody("direct:cake", "World");
        assertTrue(notify.matches());

        template.requestBody("direct:cake", "Camel");
        assertFalse(notify.matches());

        template.requestBody("direct:cake", "Damn");
        // will still be false as the mock has been completed
        assertFalse(notify.matches());
    }

    @Test
    public void testReset() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenExactlyDone(1).create();

        template.sendBody("direct:foo", "Hello World");
        assertTrue(notify.matches());

        template.sendBody("direct:foo", "Bye World");
        assertFalse(notify.matches());

        // reset
        notify.reset();
        assertFalse(notify.matches());

        template.sendBody("direct:foo", "Hello World");
        assertTrue(notify.matches());

        template.sendBody("direct:foo", "Bye World");
        assertFalse(notify.matches());
    }

    @Test
    public void testResetBodiesReceived() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenBodiesReceived("Hello World", "Bye World").create();

        template.sendBody("direct:foo", "Hello World");
        template.sendBody("direct:foo", "Bye World");
        assertTrue(notify.matches());

        // reset
        notify.reset();
        assertFalse(notify.matches());

        template.sendBody("direct:foo", "Hello World");
        assertFalse(notify.matches());

        template.sendBody("direct:foo", "Bye World");
        assertTrue(notify.matches());
    }

    @Test
    public void testOneNonAbstractPredicate() {
        Exception e = assertThrows(IllegalArgumentException.class, () -> new NotifyBuilder(context)
                .wereSentTo("mock:foo")
                .create(), "Should throw exception");
        assertEquals("NotifyBuilder must contain at least one non-abstract predicate (such as whenDone)", e.getMessage());
    }

    @Test
    public void testWereSentTo() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).wereSentTo("mock:foo").whenDone(1).create();

        template.sendBody("direct:bar", "Hello World");
        assertFalse(notify.matches());

        template.sendBody("direct:foo", "Bye World");
        assertTrue(notify.matches());
    }

    @Test
    public void testTwoWereSentTo() throws Exception {
        // sent to both endpoints
        NotifyBuilder notify = new NotifyBuilder(context).wereSentTo("log:beer").wereSentTo("mock:beer").whenDone(1).create();

        template.sendBody("direct:bar", "Hello World");
        assertFalse(notify.matches());

        template.sendBody("direct:beer", "Bye World");
        assertTrue(notify.matches());
    }

    @Test
    public void testWhenDoneWereSentTo() throws Exception {
        // only match when two are done and were sent to mock:beer
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(2).wereSentTo("mock:beer").create();

        template.sendBody("direct:bar", "A");
        assertFalse(notify.matches());

        template.sendBody("direct:beer", "B");
        assertFalse(notify.matches());

        template.sendBody("direct:bar", "C");
        assertFalse(notify.matches());

        template.sendBody("direct:bar", "D");
        assertFalse(notify.matches());

        template.sendBody("direct:cake", "E");
        assertFalse(notify.matches());

        template.sendBody("direct:beer", "F");
        assertTrue(notify.matches());
    }

    @Test
    public void testWereSentToWhenDone() throws Exception {
        // like the other test, but ordering of wereSentTo does not matter
        NotifyBuilder notify = new NotifyBuilder(context).wereSentTo("mock:beer").whenDone(2).create();

        template.sendBody("direct:bar", "A");
        assertFalse(notify.matches());

        template.sendBody("direct:beer", "B");
        assertFalse(notify.matches());

        template.sendBody("direct:bar", "C");
        assertFalse(notify.matches());

        template.sendBody("direct:bar", "D");
        assertFalse(notify.matches());

        template.sendBody("direct:cake", "E");
        assertFalse(notify.matches());

        template.sendBody("direct:beer", "F");
        assertTrue(notify.matches());
    }

    @Test
    public void testTwoWereSentToRegExp() throws Exception {
        // send to any endpoint with beer in the uri
        NotifyBuilder notify = new NotifyBuilder(context).wereSentTo(".*beer.*").whenDone(1).create();

        template.sendBody("direct:bar", "Hello World");
        assertFalse(notify.matches());

        template.sendBody("direct:beer", "Bye World");
        assertTrue(notify.matches());
    }

    @Test
    public void testTwoWereSentToDoneAndFailed() throws Exception {
        // we expect 2+ done messages which were sent to mock:bar
        // and 1+ failed message which were sent to mock:fail
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(2).wereSentTo("mock:bar").and().whenFailed(1)
                .wereSentTo("mock:fail").create();

        template.sendBody("direct:bar", "Hello World");
        assertFalse(notify.matches());

        template.sendBody("direct:bar", "Hello World");
        assertFalse(notify.matches());

        template.sendBody("direct:foo", "Hello World");
        assertFalse(notify.matches());

        assertThrows(CamelExecutionException.class, () -> template.sendBody("direct:fail", "Bye World"),
                "Should have thrown exception");
        assertTrue(notify.matches());
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
