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
package org.apache.camel.processor.errorhandler;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.reifier.errorhandler.DefaultErrorHandlerReifier;
import org.junit.Test;

public class ErrorHandlerSupportTest extends ContextTestSupport {

    @Test
    public void testOnePolicyChildFirst() {
        List<Class<? extends Throwable>> exceptions = new ArrayList<>();
        exceptions.add(ChildException.class);
        exceptions.add(ParentException.class);

        ErrorHandlerSupport support = new ShuntErrorHandlerSupport();
        addExceptionPolicy(support, context.getRoute("foo"), new OnExceptionDefinition(exceptions));

        assertEquals(ChildException.class.getName(), getExceptionPolicyFor(support, new ChildException(), 0));
        assertEquals(ParentException.class.getName(), getExceptionPolicyFor(support, new ParentException(), 1));
    }

    @Test
    public void testOnePolicyChildLast() {
        List<Class<? extends Throwable>> exceptions = new ArrayList<>();
        exceptions.add(ParentException.class);
        exceptions.add(ChildException.class);

        ErrorHandlerSupport support = new ShuntErrorHandlerSupport();
        addExceptionPolicy(support, context.getRoute("foo"), new OnExceptionDefinition(exceptions));

        assertEquals(ChildException.class.getName(), getExceptionPolicyFor(support, new ChildException(), 1));
        assertEquals(ParentException.class.getName(), getExceptionPolicyFor(support, new ParentException(), 0));
    }

    @Test
    public void testTwoPolicyChildFirst() {
        ErrorHandlerSupport support = new ShuntErrorHandlerSupport();
        addExceptionPolicy(support, context.getRoute("foo"), new OnExceptionDefinition(ChildException.class));
        addExceptionPolicy(support, context.getRoute("foo"), new OnExceptionDefinition(ParentException.class));

        assertEquals(ChildException.class.getName(), getExceptionPolicyFor(support, new ChildException(), 0));
        assertEquals(ParentException.class.getName(), getExceptionPolicyFor(support, new ParentException(), 0));
    }

    @Test
    public void testTwoPolicyChildLast() {
        ErrorHandlerSupport support = new ShuntErrorHandlerSupport();
        addExceptionPolicy(support, context.getRoute("foo"), new OnExceptionDefinition(ParentException.class));
        addExceptionPolicy(support, context.getRoute("foo"), new OnExceptionDefinition(ChildException.class));

        assertEquals(ChildException.class.getName(), getExceptionPolicyFor(support, new ChildException(), 0));
        assertEquals(ParentException.class.getName(), getExceptionPolicyFor(support, new ParentException(), 0));
    }

    private static void addExceptionPolicy(ErrorHandlerSupport handlerSupport, Route route, OnExceptionDefinition exceptionType) {
        new DefaultErrorHandlerReifier<>(route, null).addExceptionPolicy(handlerSupport, exceptionType);
    }

    private static String getExceptionPolicyFor(ErrorHandlerSupport support, Throwable childException, int index) {
        return support.getExceptionPolicy(null, childException).getExceptions().get(index);
    }

    private static class ParentException extends Exception {
        private static final long serialVersionUID = 1L;
    }

    private static class ChildException extends ParentException {
        private static final long serialVersionUID = 1L;
    }

    private static class ShuntErrorHandlerSupport extends ErrorHandlerSupport {

        @Override
        protected void doStart() throws Exception {
        }

        @Override
        protected void doStop() throws Exception {
        }

        @Override
        public boolean supportTransacted() {
            return false;
        }

        @Override
        public Processor getOutput() {
            return null;
        }

        @Override
        public void process(Exchange exchange) throws Exception {
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:foo").to("mock:foo").routeId("foo");
            }
        };
    }
}
