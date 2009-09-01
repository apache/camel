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
 * 
 */
public class SetPropertyDSLTest extends GroovyRendererTestSupport {

    @Test
    public void testSetProperty() throws Exception {
        String dsl = "from(\"direct:start\").setProperty(\"foo\", constant(\"ABC\")).to(\"mock:result\")";
        String expected = "from(\"direct:start\").setProperty(\"foo\").constant(\"ABC\").to(\"mock:result\")";

        assertEquals(expected, render(dsl));
    }

    @Test
    public void testSetPropertyXPath() throws Exception {
        String dsl = "from(\"direct:start\").unmarshal().string().setProperty(\"foo\").xpath(\"/person[@name='James']/@city\", String.class).to(\"mock:result\")";
        assertEquals(dsl, render(dsl));
    }

    @Test
    public void testSetPropertys() throws Exception {
        String dsl = "from(\"direct:start\").setProperty(\"foo\").constant(\"ABC\").setProperty(\"value\").constant(\"DEF\").to(\"mock:result\")";
        assertEquals(dsl, render(dsl));
    }
}
