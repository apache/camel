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

import org.junit.Test;

/**
 * a test case for transform DSL
 */
public class TransformDSLTest extends GroovyRendererTestSupport {

    @Test
    public void testTransformToConstant1() throws Exception {
        String dsl = "from(\"direct:start\").transform().constant(\"London\").to(\"mock:result\")";
        String expected = "from(\"direct:start\").transform(constant(\"London\")).to(\"mock:result\")";

        assertEquals(expected, render(dsl));
    }

    @Test
    public void testTransformToConstant2() throws Exception {
        String dsl = "from(\"direct:start\").transform(constant(\"London\")).to(\"mock:result\")";
        assertEquals(dsl, render(dsl));
    }

    @Test
    public void testTransformAppend() throws Exception {
        String dsl = "from(\"direct:start\").transform(body().append(\" World!\")).to(\"mock:result\")";
        assertEquals(dsl, render(dsl));
    }

    @Test
    public void testTransformSendTo() throws Exception {
        String dsl = "from(\"direct:start\").transform(sendTo(\"direct:foo\")).to(\"mock:result\")";
        String expected = "from(\"direct:start\").transform(to(\"direct:foo\")).to(\"mock:result\")";

        assertEquals(expected, render(dsl));
    }

    @Test
    public void testTransformXpath() throws Exception {
        String dsl = "from(\"direct:start\").transform().xpath(\"//students/student\").to(\"mock:result\")";
        assertEquals(dsl, render(dsl));
    }
}
