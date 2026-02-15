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

import java.io.File;
import java.lang.reflect.Method;

import org.apache.camel.impl.engine.DefaultCompileStrategy;
import org.apache.camel.language.groovy.DefaultGroovyScriptCompiler;
import org.apache.camel.spi.CompileStrategy;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.FileUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class DefaultGroovyCompilerTest extends CamelTestSupport {

    @Test
    public void testCompiler() throws Exception {
        try (DefaultGroovyScriptCompiler compiler = new DefaultGroovyScriptCompiler()) {
            compiler.setCamelContext(context);
            compiler.setScriptPattern("camel-groovy/*");
            compiler.start();

            Class<?> clazz = context.getClassResolver().resolveMandatoryClass("Dude");
            Object dude = ObjectHelper.newInstance(clazz);
            Assertions.assertNotNull(dude);

            Method m = clazz.getMethod("order", int.class);
            Object o = ObjectHelper.newInstance(clazz);

            Object out = m.invoke(o, 5);
            Assertions.assertEquals("I want to order 5 gauda", out);
        }
    }

    @Test
    public void testPreCompiled() throws Exception {
        FileUtil.removeDir(new File("target/compiled"));

        CompileStrategy cs = new DefaultCompileStrategy();
        cs.setWorkDir("target/compiled");
        context.getCamelContextExtension().addContextPlugin(CompileStrategy.class, cs);

        try (DefaultGroovyScriptCompiler compiler = new DefaultGroovyScriptCompiler()) {
            compiler.setCamelContext(context);
            compiler.setScriptPattern("camel-groovy/*");
            compiler.start();

            Assertions.assertEquals(0, compiler.getPreloadedCounter());
            Assertions.assertTrue(new File("target/compiled/groovy/Cheese.class").exists());
            Assertions.assertTrue(new File("target/compiled/groovy/Dude.class").exists());

            compiler.stop();
        }

        try (DefaultGroovyScriptCompiler compiler = new DefaultGroovyScriptCompiler()) {
            compiler.setCamelContext(context);
            compiler.setScriptPattern("camel-groovy/*");
            compiler.setPreloadCompiled(true);
            compiler.start();

            Assertions.assertEquals(2, compiler.getPreloadedCounter());

            Class<?> clazz = context.getClassResolver().resolveMandatoryClass("Dude");
            Object dude = ObjectHelper.newInstance(clazz);
            Assertions.assertNotNull(dude);

            Method m = clazz.getMethod("order", int.class);
            Object o = ObjectHelper.newInstance(clazz);

            Object out = m.invoke(o, 5);
            Assertions.assertEquals("I want to order 5 gauda", out);

            compiler.stop();
        }
    }

}
