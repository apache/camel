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
package org.apache.camel.component.dozer;

import java.lang.reflect.Method;

import org.apache.camel.impl.engine.DefaultClassResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CustomMapperTest {

    private CustomMapper customMapper;

    @BeforeEach
    public void setup() {
        customMapper = new CustomMapper(new DefaultClassResolver());
    }

    @Test
    void selectMapperOneMethod() {
        customMapper.setParameter(MapperWithOneMethod.class.getName());
        assertNotNull(customMapper.selectMethod(MapperWithOneMethod.class, String.class));
    }

    @Test
    void selectMapperMultipleMethods() throws Exception {
        Method selectedMethod = customMapper.selectMethod(MapperWithTwoMethods.class, B.class);
        assertNotNull(selectedMethod);
        assertEquals(MapperWithTwoMethods.class.getMethod("convertToA", B.class), selectedMethod);
    }

    @Test
    void mapCustomFindOperation() {
        customMapper.setParameter(MapperWithTwoMethods.class.getName());
        assertNotNull(customMapper.mapCustom(new B(), B.class));
    }

    @Test
    void mapCustomDeclaredOperation() {
        customMapper.setParameter(MapperWithTwoMethods.class.getName() + ",convertToA");
        assertNotNull(customMapper.mapCustom(new B(), B.class));
    }

    @Test
    void mapCustomInvalidOperation() {
        customMapper.setParameter(MapperWithTwoMethods.class.getName() + ",convertToB");
        B b = new B();
        Exception ex = assertThrows(RuntimeException.class, () -> customMapper.mapCustom(b, B.class));
        assertTrue(ex.getCause() instanceof NoSuchMethodException);
    }

    @Test
    void mapCustomNullField() {
        customMapper.setParameter(MapperWithTwoMethods.class.getName());
        assertNotNull(customMapper.mapCustom(null, B.class));
    }
}

class A {

}

class B extends A {

}

class MapperWithOneMethod {

    public A convertToA(String val) {
        return new A();
    }
}

class MapperWithTwoMethods {

    public A convertToA(String val) {
        return new A();
    }

    public A convertToA(B val) {
        return new A();
    }

}
