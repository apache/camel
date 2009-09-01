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
public class TryCatchFinallyDSLTest extends GroovyRendererTestSupport {

    @Test
    public void testTryCatch() throws Exception {
        String dsl = "from(\"direct:start\").doTry().to(\"mock:result\")"
            + ".doCatch(IOException.class).handled(false).to(\"mock:io\")"
            + ".doCatch(Exception.class).to(\"mock:error\").end()";
        String expected = "from(\"direct:start\").doTry().to(\"mock:result\")"
            + ".doCatch(IOException.class).handled(false).to(\"mock:io\")"
            + ".doCatch(Exception.class).to(\"mock:error\")";

        assertEquals(expected, render(dsl));
    }

    @Test
    public void testTryCatchFinally() throws Exception {
        String dsl = "from(\"direct:start\").doTry().to(\"mock:result\")"
            + ".doCatch(IOException.class).handled(false).to(\"mock:io\")"
            + ".doCatch(Exception.class).to(\"mock:error\")"
            + ".doFinally().to(\"mock:finally\").end()"
            + ".to(\"mock:last\").end()";
        String expected = "from(\"direct:start\").doTry().to(\"mock:result\")"
            + ".doCatch(IOException.class).handled(false).to(\"mock:io\")"
            + ".doCatch(Exception.class).to(\"mock:error\")"
            + ".doFinally().to(\"mock:finally\").end()"
            + ".to(\"mock:last\")";

        assertEquals(expected, render(dsl));
    }
}
