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
package org.apache.camel.processor.groovy;

import java.lang.reflect.Method;

import org.apache.camel.language.groovy.GroovyScriptCompiler;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class GroovyCompilerTest extends CamelTestSupport {

    @Test
    public void testCompiler() throws Exception {
        GroovyScriptCompiler compiler = new GroovyScriptCompiler();
        compiler.setCamelContext(context);
        compiler.setFolder("src/test/resources/myscript");
        compiler.start();

        Class<?> clazz = compiler.loadClass("Dude");
        Object dude = ObjectHelper.newInstance(clazz);
        Method m = clazz.getMethod("order", int.class);
        Object o = ObjectHelper.newInstance(clazz);
        Object out = m.invoke(o, 5);

        Assertions.assertEquals("I want to order 5 gauda", out);
    }

}
