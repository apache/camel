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
package org.apache.camel.processor.validation;

import javax.xml.transform.sax.SAXResult;
import javax.xml.validation.Schema;
import javax.xml.validation.Validator;
import javax.xml.validation.ValidatorHandler;

import org.xml.sax.Locator;
import org.xml.sax.SAXParseException;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultExchange;

/**
 * @version 
 */
public class DefaultValidationErrorHandlerTest extends ContextTestSupport {

    public void testWarning() throws Exception {
        DefaultValidationErrorHandler eh = new DefaultValidationErrorHandler();

        eh.warning(new SAXParseException("foo", createLocator(1, 2)));

        // just a warning so should be valid
        assertEquals(true, eh.isValid());
    }

    public void testError() throws Exception {
        DefaultValidationErrorHandler eh = new DefaultValidationErrorHandler();

        eh.error(new SAXParseException("foo", createLocator(3, 5)));

        assertEquals(false, eh.isValid());
    }

    public void testFatalError() throws Exception {
        DefaultValidationErrorHandler eh = new DefaultValidationErrorHandler();

        eh.fatalError(new SAXParseException("foo", createLocator(5, 8)));

        assertEquals(false, eh.isValid());
    }

    public void testReset() throws Exception {
        DefaultValidationErrorHandler eh = new DefaultValidationErrorHandler();

        eh.fatalError(new SAXParseException("foo", createLocator(5, 8)));

        assertEquals(false, eh.isValid());

        eh.reset();

        assertEquals(true, eh.isValid());
    }

    public void testHandleErrors() throws Exception {
        DefaultValidationErrorHandler eh = new DefaultValidationErrorHandler();

        eh.error(new SAXParseException("foo", createLocator(3, 5)));
        eh.error(new SAXParseException("bar", createLocator(9, 12)));
        eh.fatalError(new SAXParseException("cheese", createLocator(13, 17)));

        assertEquals(false, eh.isValid());

        Exchange exchange = new DefaultExchange(context);
        try {
            eh.handleErrors(exchange, createScheme());
            fail("Should have thrown an exception");
        } catch (SchemaValidationException e) {
            assertEquals(2, e.getErrors().size());
            assertEquals(1, e.getFatalErrors().size());
            assertEquals(0, e.getWarnings().size());
            assertNotNull(e.getSchema());
            assertNotNull(e.getExchange());

            assertTrue(e.getMessage().startsWith("Validation failed for: org.apache.camel.processor.validation.DefaultValidationErrorHandlerTest"));
            assertTrue(e.getMessage().contains("fatal errors: ["));
            assertTrue(e.getMessage().contains("org.xml.sax.SAXParseException: cheese, Line : 13, Column : 17"));
            assertTrue(e.getMessage().contains("errors: ["));
            assertTrue(e.getMessage().contains("org.xml.sax.SAXParseException: foo, Line : 3, Column : 5"));
            assertTrue(e.getMessage().contains("org.xml.sax.SAXParseException: bar, Line : 9, Column : 12"));
            assertTrue(e.getMessage().contains("Exchange[]"));
        }
    }

    public void testHandleErrorsResult() throws Exception {
        DefaultValidationErrorHandler eh = new DefaultValidationErrorHandler();

        eh.error(new SAXParseException("foo", createLocator(3, 5)));
        eh.error(new SAXParseException("bar", createLocator(9, 12)));

        assertEquals(false, eh.isValid());

        Exchange exchange = new DefaultExchange(context);
        try {
            eh.handleErrors(exchange, createScheme(), new SAXResult());
            fail("Should have thrown an exception");
        } catch (SchemaValidationException e) {
            assertEquals(2, e.getErrors().size());
            assertEquals(0, e.getFatalErrors().size());
            assertEquals(0, e.getWarnings().size());
            assertNotNull(e.getSchema());
            assertNotNull(e.getExchange());

            assertTrue(e.getMessage().startsWith("Validation failed for: org.apache.camel.processor.validation.DefaultValidationErrorHandlerTest"));
            assertTrue(e.getMessage().contains("errors: ["));
            assertTrue(e.getMessage().contains("org.xml.sax.SAXParseException: foo, Line : 3, Column : 5"));
            assertTrue(e.getMessage().contains("org.xml.sax.SAXParseException: bar, Line : 9, Column : 12"));
            assertTrue(e.getMessage().contains("Exchange[]"));
        }
    }

    private Schema createScheme() {
        return new Schema() {
            @Override
            public Validator newValidator() {
                return null;
            }

            @Override
            public ValidatorHandler newValidatorHandler() {
                return null;
            }
        };
    }

    private Locator createLocator(final int line, final int column) {
        return new Locator() {
            public String getSystemId() {
                return null;
            }

            public String getPublicId() {
                return null;
            }

            public int getLineNumber() {
                return line;
            }

            public int getColumnNumber() {
                return column;
            }
        };
    }
}
