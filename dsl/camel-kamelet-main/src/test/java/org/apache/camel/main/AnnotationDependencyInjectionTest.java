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

package org.apache.camel.main;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.apache.camel.main.app.Bean1;
import org.apache.camel.main.app.MyNoValueSpringBean;
import org.apache.camel.main.app.MySpringBean;
import org.apache.camel.util.AnnotationHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class AnnotationDependencyInjectionTest {

    @Test
    public void testSpringBean() throws Exception {
        // no annotation
        Object value = AnnotationHelper.getAnnotationValue(Bean1.class, "org.springframework.stereotype.Component");
        Assertions.assertNull(value);

        // annotation with no value
        value = AnnotationHelper.getAnnotationValue(
                MyNoValueSpringBean.class, "org.springframework.stereotype.Component");
        Assertions.assertEquals("", value);

        // annotation with value
        value = AnnotationHelper.getAnnotationValue(MySpringBean.class, "org.springframework.stereotype.Component");
        Assertions.assertEquals("theNameHere", value);

        Field f = MySpringBean.class.getDeclaredField("camelContext");
        value = AnnotationHelper.getAnnotationValue(
                f, "org.springframework.beans.factory.annotation.Autowired", "required");
        Assertions.assertEquals(Boolean.TRUE, value);

        Method m = MySpringBean.class.getDeclaredMethod("cheese");
        String[] names = (String[])
                AnnotationHelper.getAnnotationValue(m, "org.springframework.context.annotation.Bean", "value");
        Assertions.assertEquals("a1", names[0]);
        Assertions.assertEquals("a2", names[1]);
    }
}
