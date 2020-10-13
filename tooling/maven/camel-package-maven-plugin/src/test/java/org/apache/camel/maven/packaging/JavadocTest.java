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
package org.apache.camel.maven.packaging;

import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.JavaType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class JavadocTest {

    // CHECKSTYLE:OFF
    public static final String SOURCE_CLASS_1 =
            "    /**\n" +
            "     * Path to the template.\n" +
            "     * <p/>\n" +
            "     * The following is supported by the default URIResolver.\n" +
            "     * You can prefix with: classpath, file, http, ref, or bean.\n" +
            "     * classpath, file and http loads the resource using these protocols (classpath is default).\n" +
            "     * ref will lookup the resource in the registry.\n" +
            "     * bean will call a method on a bean to be used as the resource.\n" +
            "     * For bean you can specify the method name after dot, eg bean:myBean.myMethod\n" +
            "     *\n" +
            "     * @param resourceUri  the resource path\n" +
            "     */\n" +
            "class Test {\n" +
            "}";
    public static final String JAVADOC_1 =
            "Path to the template.\n<p/>\nThe following is supported by the default URIResolver.\n" +
            "You can prefix with: classpath, file, http, ref, or bean.\nclasspath, file and http " +
            "loads the resource using these protocols (classpath is default).\nref will lookup the " +
            "resource in the registry.\nbean will call a method on a bean to be used as the " +
            "resource.\nFor bean you can specify the method name after dot, eg bean:myBean.myMethod";
    public static final String SOURCE_CLASS_2 =
            "        /**\n" +
            "         * Sets how requests and responses will be mapped to/from Camel. Two values are possible:\n" +
            "         * <ul>\n" +
            "         *     <li>SimpleConsumer: This binding style processes request parameters, multiparts, etc. and maps them to IN headers, IN attachments and to the message body.\n" +
            "         *                         It aims to eliminate low-level processing of {@link org.apache.cxf.message.MessageContentsList}.\n" +
            "         *                         It also also adds more flexibility and simplicity to the response mapping.\n" +
            "         *                         Only available for consumers.\n" +
            "         *     </li>\n" +
            "         *     <li>Default: The default style. For consumers this passes on a MessageContentsList to the route, requiring low-level processing in the route.\n" +
            "         *                  This is the traditional binding style, which simply dumps the {@link org.apache.cxf.message.MessageContentsList} coming in from the CXF stack\n" +
            "         *                  onto the IN message body. The user is then responsible for processing it according to the contract defined by the JAX-RS method signature.\n" +
            "         *     </li>\n" +
            "         *     <li>Custom: allows you to specify a custom binding through the binding option.</li>\n" +
            "         * </ul>\n" +
            "         */\n" +
            "class Test {\n" +
            "}";
    public static final String JAVADOC_2 =
            "Sets how requests and responses will be mapped to/from Camel. Two values are possible:\n" +
            "<ul>\n<li>SimpleConsumer: This binding style processes request parameters, multiparts, etc. " +
            "and maps them to IN headers, IN attachments and to the message body.\nIt aims to eliminate " +
            "low-level processing of {@link org.apache.cxf.message.MessageContentsList}.\nIt also also adds more " +
            "flexibility and simplicity to the response mapping.\nOnly available for consumers.\n</li>\n" +
            "<li>Default: The default style. For consumers this passes on a MessageContentsList to the " +
            "route, requiring low-level processing in the route.\nThis is the traditional binding style, " +
            "which simply dumps the {@link org.apache.cxf.message.MessageContentsList} coming in from the CXF " +
            "stack\nonto the IN message body. The user is then responsible for processing it according " +
            "to the contract defined by the JAX-RS method signature.\n</li>\n<li>Custom: allows you to " +
            "specify a custom binding through the binding option.</li>\n</ul>";
    public static final String SOURCE_CLASS_3 =
            "    /**\n" +
            "     * Sets the alias used to query the KeyStore for keys and {@link java.security.cert.Certificate Certificates}\n" +
            "     * to be used in signing and verifying exchanges. This value can be provided at runtime via the message header\n" +
            "     * {@link org.apache.camel.component.crypto.DigitalSignatureConstants#KEYSTORE_ALIAS}\n" +
            "     */\n" +
            "class Test {\n" +
            "}";
    public static final String JAVADOC_3 =
            "Sets the alias used to query the KeyStore for keys and " +
            "{@link java.security.cert.Certificate Certificates}\n" +
            "to be used in signing and verifying exchanges. This value can be provided at runtime " +
            "via the message header\n" +
            "{@link org.apache.camel.component.crypto.DigitalSignatureConstants#KEYSTORE_ALIAS}";
    public static final String SOURCE_CLASS_4 =
            "/**\n" +
            " * Messaging with AMQP protocol using Apache QPid Client.\n" +
            " *\n" +
            " * This class extends JmsEndpoint because it uses Apache Qpid JMS-compatible client for\n" +
            " * performing the AMQP connectivity.\n" +
            " */\n" +
            "class Test {\n" +
            "}";
    public static final String JAVADOC_4 =
            "Messaging with AMQP protocol using Apache QPid Client.\n\nThis class extends JmsEndpoint " +
            "because it uses Apache Qpid JMS-compatible client for\nperforming the AMQP connectivity.";
    // CHECKSTYLE:ON

    @Test
    public void testJavadoc1() {
        doTestJavadoc(SOURCE_CLASS_1, JAVADOC_1);
    }

    @Test
    public void testJavaDoc2() {
        doTestJavadoc(SOURCE_CLASS_2, JAVADOC_2);
    }

    @Test
    public void testJavaDoc3() {
        doTestJavadoc(SOURCE_CLASS_3, JAVADOC_3);
    }

    @Test
    public void testJavadoc4() {
        doTestJavadoc(SOURCE_CLASS_4, JAVADOC_4);
    }

    private void doTestJavadoc(String sourceClass1, String javadoc1) {
        JavaType<?> javaType = Roaster.parse(sourceClass1);
        String javaDoc = EndpointSchemaGeneratorMojo.getJavaDocText(sourceClass1, javaType);
        Assertions.assertEquals(javadoc1, javaDoc);
    }

}
