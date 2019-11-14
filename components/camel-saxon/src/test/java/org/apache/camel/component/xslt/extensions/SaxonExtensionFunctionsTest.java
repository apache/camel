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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class SaxonExtensionFunctionsTest extends CamelTestSupport {
    private static final String XSLT_PATH = "org/apache/camel/component/xslt/extensions/extensions.xslt";
    private static final String XSLT_RESULT = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Test1>3</Test1><Test2>abccde</Test2><Test3>xyz</Test3>";

    @Test
    public void testExtensions() {
        String result = template().requestBody("direct:extensions", "<dummy/>", String.class);
        assertNotNull(result);
        assertEquals(XSLT_RESULT, result);
    }

    @Override
    protected void bindToRegistry(Registry registry) throws Exception {
        registry.bind("function1", new MyExtensionFunction1());
        registry.bind("function2", new MyExtensionFunction2());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:extensions")
                    .toF("xslt-saxon:%s?saxonExtensionFunctions=#function1,#function2", XSLT_PATH)
                        .to("log:org.apache.camel.component.xslt.extensions?level=INFO");
            }
        };
    }
}
