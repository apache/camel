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
package org.apache.camel.maven;

import java.io.FileInputStream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test JavaSourceParser.
 */
public class JavaSourceParserTest {

    @Test
    public void testGetMethodsAddress() throws Exception {
        final JavaSourceParser parser = new JavaSourceParser();

        parser.parse(JavaSourceParserTest.class.getResourceAsStream("/AddressGateway.java"));
        assertEquals(4, parser.getMethods().size());

        assertEquals(
                "public com.braintreegateway.Result create(String customerId, com.braintreegateway.AddressRequest request)",
                parser.getMethods().get(0));
        assertEquals(2, parser.getParameters().get("create").size());
        assertEquals("The id of the Customer", parser.getParameters().get("create").get("customerId"));
        assertEquals("The request object", parser.getParameters().get("create").get("request"));

        parser.reset();

    }

    @Test
    public void testGetMethodsCustomer() throws Exception {
        final JavaSourceParser parser = new JavaSourceParser();

        parser.parse(JavaSourceParserTest.class.getResourceAsStream("/CustomerGateway.java"));
        assertEquals(7, parser.getMethods().size());

        assertEquals(
                "public com.braintreegateway.Result create(com.braintreegateway.CustomerRequest request)",
                parser.getMethods().get(1));
        assertEquals(1, parser.getParameters().get("create").size());
        assertEquals("The request", parser.getParameters().get("create").get("request"));

        parser.reset();

    }

    @Test
    public void testGetMethodsDispute() throws Exception {
        final JavaSourceParser parser = new JavaSourceParser();

        parser.parse(JavaSourceParserTest.class.getResourceAsStream("/DisputeGateway.java"));
        assertEquals(9, parser.getMethods().size());

        assertEquals(
                "public com.braintreegateway.Result addFileEvidence(String disputeId, String documentId)",
                parser.getMethods().get(1));
        assertEquals(3, parser.getParameters().get("addFileEvidence").size());
        assertEquals("The dispute id to add text evidence to", parser.getParameters().get("addFileEvidence").get("disputeId"));
        assertEquals("The document id of a previously uploaded document",
                parser.getParameters().get("addFileEvidence").get("documentId"));

    }

    @Test
    public void testWildcard() throws Exception {
        final JavaSourceParser parser = new JavaSourceParser();

        parser.parse(new FileInputStream("src/test/java/org/apache/camel/component/test/TestProxy.java"));
        assertEquals(11, parser.getMethods().size());

        // varargs is transformed to an array type as that is what works
        assertEquals(
                "public java.lang.String greetWildcard(String[] names)",
                parser.getMethods().get(7));
        parser.reset();
    }

}
