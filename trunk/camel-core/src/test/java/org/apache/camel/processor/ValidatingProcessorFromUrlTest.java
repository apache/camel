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

import java.net.URL;

import org.apache.camel.processor.validation.ValidatingProcessor;
import org.apache.camel.util.ObjectHelper;

/**
 * Unit test of ValidatingProcessor.
 */
public class ValidatingProcessorFromUrlTest extends ValidatingProcessorTest {

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        validating = new ValidatingProcessor();
        URL url = ObjectHelper.loadResourceAsURL("org/apache/camel/processor/ValidatingProcessor.xsd");
        validating.setSchemaUrl(url);

        // loading scheme can be forced so lets try it
        validating.loadSchema();
    }

    @Override
    public void testValidatingOptions() throws Exception {
        assertNotNull(validating.getErrorHandler());
        assertNotNull(validating.getSchema());
        assertNotNull(validating.getSchemaFactory());

        assertNull(validating.getSchemaFile());
        assertNotNull(validating.getSchemaLanguage());
        assertNotNull(validating.getSchemaUrl());

        try {
            assertNotNull(validating.getSchemaSource());
            fail("Should have thrown an exception");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

}