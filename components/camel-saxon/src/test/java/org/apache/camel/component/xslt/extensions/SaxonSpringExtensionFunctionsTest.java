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
package org.apache.camel.component.xslt.extensions;

import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class SaxonSpringExtensionFunctionsTest extends CamelSpringTestSupport {
    private static final String XSLT_RESULT = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Test1>3</Test1><Test2>abccde</Test2><Test3>xyz</Test3>";

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/xslt/extensions/camelXsltContext.xml");
    }

    @Test
    public void testExtensions() {
        String result = template().requestBody("direct:extensions", "<dummy/>", String.class);
        assertNotNull(result);
        assertEquals(XSLT_RESULT, result);
    }
}
