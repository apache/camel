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
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class CustomMapperTest {
    
    private CustomMapper customMapper;
    
    @Before
    public void setup() {
        customMapper = new CustomMapper(new DefaultClassResolver());
    }
    
    @Test
    public void selectMapperOneMethod() {
        customMapper.setParameter(MapperWithOneMethod.class.getName());
        Assert.assertNotNull(customMapper.selectMethod(MapperWithOneMethod.class, String.class));
    }
    
    @Test
    public void selectMapperMultipleMethods() throws Exception {
        Method selectedMethod = customMapper.selectMethod(MapperWithTwoMethods.class, B.class);
        Assert.assertNotNull(selectedMethod);
        Assert.assertEquals(
                MapperWithTwoMethods.class.getMethod("convertToA", B.class),
                selectedMethod);
    }
    
    @Test
    public void mapCustomFindOperation() throws Exception {
        customMapper.setParameter(MapperWithTwoMethods.class.getName());
        Assert.assertNotNull(customMapper.mapCustom(new B(), B.class));
    }
    
    @Test
    public void mapCustomDeclaredOperation() throws Exception {
        customMapper.setParameter(MapperWithTwoMethods.class.getName() + ",convertToA");
        Assert.assertNotNull(customMapper.mapCustom(new B(), B.class));
    }
    
    @Test
    public void mapCustomInvalidOperation() {
        customMapper.setParameter(MapperWithTwoMethods.class.getName() + ",convertToB");
        try {
            customMapper.mapCustom(new B(), B.class);
            Assert.fail("Invalid operation should result in exception");
        } catch (RuntimeException ex) {
            Assert.assertTrue(ex.getCause() instanceof NoSuchMethodException);
        }
    }

    @Test
    public void mapCustomNullField() throws Exception {
        customMapper.setParameter(MapperWithTwoMethods.class.getName());
        Assert.assertNotNull(customMapper.mapCustom(null, B.class));
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


