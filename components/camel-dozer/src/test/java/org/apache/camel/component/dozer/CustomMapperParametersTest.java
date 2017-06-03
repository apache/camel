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
package org.apache.camel.component.dozer;

import org.apache.camel.impl.DefaultClassResolver;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class CustomMapperParametersTest {

    private CustomMapper customMapper;

    @Before
    public void setup() {
        customMapper = new CustomMapper(new DefaultClassResolver());
    }

    @Test
    public void shouldExecuteCustomFunctionWithArguments() throws Exception {
        customMapper.setParameter(MapperWithMultiParmMethod.class.getName() + ",test,java.lang.Integer=12,java.lang.Integer=20");
        Object result = customMapper.mapCustom("JeremiahWasABullfrog", String.class);
        Assert.assertEquals("Bullfrog", result);
    }

    @Test
    public void shouldExecuteCustomFunctionWithVariableArguments() throws Exception {
        customMapper.setParameter(MapperWithMultiParmMethod.class.getName() + ",add,java.lang.Integer=12,java.lang.Integer=20");
        Object result = customMapper.mapCustom("JeremiahWasABullfrog", String.class);
        Assert.assertEquals(32L, result);
    }
}

class MapperWithMultiParmMethod {

    public Object add(String source, Integer... operands) {
        long sum = 0L;
        for (Integer operand : operands) {
            sum += operand;
        }
        return sum;
    }

    public Object test(String source, Integer beginindex, Integer endindex) {
        return source.substring(beginindex.intValue(), endindex.intValue());
    }
}

