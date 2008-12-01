/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.spring.javaconfig.test;

import org.springframework.test.context.junit38.AbstractJUnit38SpringContextTests;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.config.java.test.JavaConfigContextLoader;
import org.springframework.config.java.plugin.context.AnnotationDrivenConfig;

/**
 * @version $Revision: 1.1 $
 */
@ContextConfiguration(locations = "org.apache.camel.spring.javaconfig.test.MyConfig", loader = JavaConfigContextLoader.class)
@AnnotationDrivenConfig
public class JavaConfigWithPostProcessorTest extends AbstractJUnit38SpringContextTests implements Cheese {
    private boolean doCheeseCalled;

    public void testPostProcessorInjectsMe() throws Exception {
        assertEquals("doCheese() should be called", true, doCheeseCalled);

    }

    public void doCheese() {
        System.out.println("doCheese called!");
        doCheeseCalled = true;
    }
}
