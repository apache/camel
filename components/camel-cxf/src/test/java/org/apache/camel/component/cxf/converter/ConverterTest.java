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
package org.apache.camel.component.cxf.converter;

import java.util.List;

import junit.framework.TestCase;

public class ConverterTest extends TestCase {
    
    public void testToClassesList() throws Exception {
        String classString = "java.lang.String, "
            + "org.apache.camel.component.cxf.converter.ConverterTest ;"
            + "java.lang.StringBuffer";
        List<Class> classList = CxfConverter.toClassList(classString);
        assertEquals("Get the wrong number of classes", classList.size(), 3);
        assertEquals("Get the wrong the class", classList.get(0), String.class);
        assertEquals("Get the wrong the class", classList.get(1), ConverterTest.class);
        assertEquals("Get the wrong the class", classList.get(2), StringBuffer.class);
    }
    
    

}
