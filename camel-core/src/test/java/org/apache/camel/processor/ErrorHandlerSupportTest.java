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
package org.apache.camel.processor;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.model.OnExceptionDefinition;

public class ErrorHandlerSupportTest extends TestCase {

    public void testOnePolicyChildFirst() {
        List<Class<? extends Throwable>> exceptions = new ArrayList<Class<? extends Throwable>>();
        exceptions.add(ChildException.class);
        exceptions.add(ParentException.class);

        ErrorHandlerSupport support = new ShuntErrorHandlerSupport();
        support.addExceptionPolicy(null, new OnExceptionDefinition(exceptions));

        assertEquals(ChildException.class, getExceptionPolicyFor(support, new ChildException(), 0));
        assertEquals(ParentException.class, getExceptionPolicyFor(support, new ParentException(), 1));
    }

    public void testOnePolicyChildLast() {
        List<Class<? extends Throwable>> exceptions = new ArrayList<Class<? extends Throwable>>();
        exceptions.add(ParentException.class);
        exceptions.add(ChildException.class);

        ErrorHandlerSupport support = new ShuntErrorHandlerSupport();
        support.addExceptionPolicy(null, new OnExceptionDefinition(exceptions));

        assertEquals(ChildException.class, getExceptionPolicyFor(support, new ChildException(), 1));
        assertEquals(ParentException.class, getExceptionPolicyFor(support, new ParentException(), 0));
    }

    public void testTwoPolicyChildFirst() {
        ErrorHandlerSupport support = new ShuntErrorHandlerSupport();
        support.addExceptionPolicy(null, new OnExceptionDefinition(ChildException.class));
        support.addExceptionPolicy(null, new OnExceptionDefinition(ParentException.class));

        assertEquals(ChildException.class, getExceptionPolicyFor(support, new ChildException(), 0));
        assertEquals(ParentException.class, getExceptionPolicyFor(support, new ParentException(), 0));
    }

    public void testTwoPolicyChildLast() {
        ErrorHandlerSupport support = new ShuntErrorHandlerSupport();
        support.addExceptionPolicy(null, new OnExceptionDefinition(ParentException.class));
        support.addExceptionPolicy(null, new OnExceptionDefinition(ChildException.class));

        assertEquals(ChildException.class, getExceptionPolicyFor(support, new ChildException(), 0));
        assertEquals(ParentException.class, getExceptionPolicyFor(support, new ParentException(), 0));
    }

    private static Class<? extends Throwable> getExceptionPolicyFor(ErrorHandlerSupport support, Throwable childException,
                                               int index) {
        return support.getExceptionPolicy(null, childException).getExceptionClasses().get(index);
    }

    private static class ParentException extends Exception {
        private static final long serialVersionUID = 1L;
    }

    private static class ChildException extends ParentException {
        private static final long serialVersionUID = 1L;
    }

    private static class ShuntErrorHandlerSupport extends ErrorHandlerSupport {

        protected void doStart() throws Exception {
        }

        protected void doStop() throws Exception {
        }

        public boolean supportTransacted() {
            return false;
        }

        public Processor getOutput() {
            return null;
        }

        public void process(Exchange exchange) throws Exception {
        }
    }

}
