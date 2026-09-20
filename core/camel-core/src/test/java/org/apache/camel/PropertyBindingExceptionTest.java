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
package org.apache.camel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class PropertyBindingExceptionTest {
    public static final String EXPECTED_EXCEPTION_MESSAGE
            = "Error binding property (prefix.property=value) with name: property";

    @Test
    public void messageCarriesTheReason() {
        // CAMEL-24836: the first line said only that the binding failed; the reason sat two causes down
        IllegalArgumentException reason = new IllegalArgumentException(
                "host must be an absolute URI (e.g. http://api.example.com), given: `http://localhost:8080/api`");
        PropertyBindingException inner
                = new PropertyBindingException(new Object(), "host", "http://localhost:8080/api", reason);
        PropertyBindingException outer = new PropertyBindingException(
                new Object(), "host", "http://localhost:8080/api", "camel.component.rest-openapi", "host", inner);
        assertTrue(outer.getMessage().endsWith("with value: http://localhost:8080/api: host must be an absolute URI"
                                               + " (e.g. http://api.example.com), given: `http://localhost:8080/api`"),
                outer.getMessage());
        assertTrue(inner.getMessage().endsWith(": host must be an absolute URI (e.g. http://api.example.com), given:"
                                               + " `http://localhost:8080/api`"),
                inner.getMessage());
        // no reason to add: a cause without a message, or no cause at all
        PropertyBindingException noReason = new PropertyBindingException(new Object(), "host", "x", new RuntimeException());
        assertTrue(noReason.getMessage().endsWith("with value: x"), noReason.getMessage());
        PropertyBindingException noCause = new PropertyBindingException(new Object(), "host", "x");
        assertTrue(noCause.getMessage().endsWith("with value: x"), noCause.getMessage());
        PropertyBindingException noName = new PropertyBindingException(new Object(), reason);
        assertTrue(noName.getMessage().endsWith(": host must be an absolute URI (e.g. http://api.example.com), given:"
                                                + " `http://localhost:8080/api`"),
                noName.getMessage());
    }

    @Test
    public void messageOnACyclicCauseChain() {
        // a cause chain assembled outside initCause can loop (A -> B -> A); the walk must still end
        CyclicException a = new CyclicException("a");
        CyclicException b = new CyclicException("b");
        a.next = b;
        b.next = a;
        PropertyBindingException pbe = new PropertyBindingException(new Object(), "host", "x", a);
        assertTrue(pbe.getMessage().startsWith("Error binding property (host=x)"), pbe.getMessage());
    }

    private static final class CyclicException extends RuntimeException {
        Throwable next;

        CyclicException(String message) {
            super(message);
        }

        @Override
        public synchronized Throwable getCause() {
            return next;
        }
    }

    @Test
    public void exceptionMessageTest() {
        PropertyBindingException pbe = new PropertyBindingException(
                new Object(), "property", "value", "prefix", "property", new Throwable("The cause!"));
        assertTrue(pbe.getMessage().startsWith(EXPECTED_EXCEPTION_MESSAGE),
                "PropertyBindingException message should start with [" + EXPECTED_EXCEPTION_MESSAGE + "] while is ["
                                                                            + pbe.getMessage() + "] instead.");
        assertTrue(pbe.getMessage().endsWith(": The cause!"), pbe.getMessage());

        pbe = new PropertyBindingException(
                new Object(), "property", "value", "prefix.", "property", new Throwable("The cause!"));
        assertTrue(pbe.getMessage().startsWith(EXPECTED_EXCEPTION_MESSAGE),
                "PropertyBindingException message should start with [" + EXPECTED_EXCEPTION_MESSAGE + "] while is ["
                                                                            + pbe.getMessage() + "] instead.");
        assertTrue(pbe.getMessage().endsWith(": The cause!"), pbe.getMessage());
    }
}
