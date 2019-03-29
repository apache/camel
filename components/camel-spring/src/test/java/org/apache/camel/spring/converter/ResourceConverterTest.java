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
package org.apache.camel.spring.converter;

import java.io.IOException;
import java.io.InputStream;

import org.apache.camel.CamelContext;
import org.apache.camel.TestSupport;
import org.apache.camel.TypeConverter;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

public class ResourceConverterTest extends TestSupport {

    @Test
    public void testResourceConverterRegistry() {
        Assert.assertNotNull(getResourceTypeConverter());
    }

    @Test
    public void testNonNullConversion() throws IOException {
        Resource resource = new ClassPathResource("testresource.txt", ResourceConverterTest.class);
        Assert.assertTrue(resource.exists());
        InputStream inputStream = getResourceTypeConverter().convertTo(InputStream.class, resource);
        byte[] resourceBytes = IOConverter.toBytes(resource.getInputStream());
        byte[] inputStreamBytes = IOConverter.toBytes(inputStream);
        Assert.assertArrayEquals(resourceBytes, inputStreamBytes);
    }

    private TypeConverter getResourceTypeConverter() {
        CamelContext camelContext = new DefaultCamelContext();
        TypeConverter typeConverter = camelContext.getTypeConverterRegistry().lookup(InputStream.class, Resource.class);
        return typeConverter;
    }

}
