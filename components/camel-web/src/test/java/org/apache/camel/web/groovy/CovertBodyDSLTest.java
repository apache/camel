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

package org.apache.camel.web.groovy;

/**
 * a test case for convertBody DSL: from().convertBodyTo().to()
 */
public class CovertBodyDSLTest extends GroovyRendererTestSupport {

    public void testConvertBody() throws Exception {
        String dsl = "from(\"direct:start\").convertBodyTo(Integer.class).to(\"mock:result\")";
        String expectedDSL = "from(\"direct:start\").convertBodyTo(java.lang.Integer.class).to(\"mock:result\")";

        assertEquals(expectedDSL, render(dsl));
    }

    public void testConvertBodyWithEncoding() throws Exception {
        String dsl = "from(\"direct:start\").convertBodyTo(byte[].class, \"iso-8859-1\").to(\"mock:result\")";
        String expectedDSL = dsl;

        assertEquals(expectedDSL, render(dsl));
    }
}
