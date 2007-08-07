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
package org.apache.camel.spring.spi;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.aopalliance.intercept.MethodInvocation;

import org.apache.camel.Converter;
import org.apache.camel.component.bean.BeanInvocation;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;


/**
 * Some Spring based
 * <a href="http://activemq.apache.org/camel/type-converter.html">Type Converters</a>
 *
 * @version $Revision$
 */
@Converter
public final class SpringConverters {
    
    private SpringConverters() {        
    }
    
    @Converter
    public static InputStream toInputStream(Resource resource) throws IOException {
        return resource.getInputStream();
    }

    @Converter
    public static File toFile(Resource resource) throws IOException {
        return resource.getFile();
    }

    @Converter
    public static URL toUrl(Resource resource) throws IOException {
        return resource.getURL();
    }

    @Converter
    public static UrlResource toResource(String uri) throws IOException {
        return new UrlResource(uri);
    }

    @Converter
    public static UrlResource toResource(URL uri) throws IOException {
        return new UrlResource(uri);
    }

    @Converter
    public static FileSystemResource toResource(File file) throws IOException {
        return new FileSystemResource(file);
    }

    @Converter
    public static ByteArrayResource toResource(byte[] data) throws IOException {
        return new ByteArrayResource(data);
    }

    @Converter
    public static BeanInvocation toBeanInvocation(MethodInvocation invocation) {
        return new BeanInvocation(invocation.getThis(), invocation.getMethod(), invocation.getArguments());
    }
}
