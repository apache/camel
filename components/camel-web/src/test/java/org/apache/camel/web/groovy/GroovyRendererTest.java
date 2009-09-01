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

import java.io.BufferedReader;
import java.io.FileReader;

import org.junit.Test;

/**
 * Test for GroovyRenderer
 */
public class GroovyRendererTest extends GroovyRendererTestSupport {

    @Test
    public void testRoute() throws Exception {
        FileReader reader = new FileReader("src/test/resources/route.txt");
        BufferedReader br = new BufferedReader(reader);
        String dsl = null;
        while ((dsl = br.readLine()) != null) {
            System.out.println("dsl: " + dsl);
            System.out.println("after rendered: \n" + render(dsl));
            System.out.println();
        }
    }

    @Test
    public void testRoutes() throws Exception {
        FileReader reader = new FileReader("src/test/resources/routes.txt");
        BufferedReader br = new BufferedReader(reader);
        String dsl = null;
        while ((dsl = br.readLine()) != null) {
            System.out.println("dsl: " + dsl);
            System.out.println("after rendered: \n" + renderRoutes(dsl));
            System.out.println();
        }
    }
}
