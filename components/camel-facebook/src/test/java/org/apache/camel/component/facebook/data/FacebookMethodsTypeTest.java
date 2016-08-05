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
package org.apache.camel.component.facebook.data;

import java.lang.reflect.Method;

import facebook4j.Facebook;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;


/**
 * Test that all *Methods methods are mapped in {@link FacebookMethodsType}.
 */
public class FacebookMethodsTypeTest {

    @Test
    public void areAllMethodsMapped() throws Exception {
        final Class<?>[] interfaces = Facebook.class.getInterfaces();
        for (Class<?> clazz : interfaces) {
            if (clazz.getName().endsWith("Methods")) {
                // check all methods of this *Methods interface
                for (Method method : clazz.getDeclaredMethods()) {
                    final FacebookMethodsType methodsType = FacebookMethodsType.findMethod(method.getName(), method.getParameterTypes());
                    assertNotNull("Expected method mapping not found:" + method.getName(), methodsType);
                    assertEquals("Methods are not equal", method, methodsType.getMethod());
                }
            }
        }
    }

}
