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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.Response;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.easymock.classextension.EasyMock;
import org.junit.Assert;
import org.junit.Test;

public class ConverterTest extends Assert {
    
    @Test
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
    
    @Test
    public void testToArray() throws Exception {
        List<String> testList = new ArrayList<String>();
        testList.add("string 1");
        testList.add("string 2");
        
        Object[] array = CxfConverter.toArray(testList);
        assertNotNull("The array should not be null", array);
        assertEquals("The array size should not be wrong", 2, array.length);
    }
    
    @Test
    public void testToInputStream() throws Exception {
        CamelContext context = new DefaultCamelContext();
        Exchange exchange = new DefaultExchange(context);
        
        Response response = EasyMock.createMock(Response.class);
        InputStream is = EasyMock.createMock(InputStream.class);
        
        response.getEntity();
        EasyMock.expectLastCall().andReturn(is);
        
        EasyMock.replay(response);
        InputStream result = CxfConverter.toInputStream(response, exchange);
        assertEquals("We should get the inputStream here ", is, result);
        EasyMock.verify(response);
        
        EasyMock.reset(response);
        response.getEntity();
        EasyMock.expectLastCall().andReturn("Hello World");
        EasyMock.replay(response);
        result = CxfConverter.toInputStream(response, exchange);
        assertTrue("We should get the inputStream here ", result instanceof ByteArrayInputStream);
        EasyMock.verify(response);
        
    }
    
    

}
