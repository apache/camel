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
package org.apache.camel.converter;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

import org.apache.camel.CamelContext;
import org.apache.camel.Converter;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.PluginHelper;

@Converter(generateBulkLoader = true)
public final class ResourceConverter {

    /**
     * Utility classes should not have a public constructor.
     */
    private ResourceConverter() {
    }

    @Converter(order = 1)
    public static InputStream toInputStream(Resource resource) throws IOException {
        return resource.getInputStream();
    }

    @Converter(order = 2)
    public static Reader toReader(Resource resource) throws Exception {
        return resource.getReader();
    }

    @Converter(order = 3)
    public static byte[] toByteArray(Resource resource, CamelContext camelContext) throws IOException {
        InputStream is = resource.getInputStream();
        return camelContext.getTypeConverter().tryConvertTo(byte[].class, is);
    }

    @Converter(order = 4)
    public static String toString(Resource resource, CamelContext camelContext) throws IOException {
        InputStream is = resource.getInputStream();
        return camelContext.getTypeConverter().tryConvertTo(String.class, is);
    }

    @Converter(order = 5)
    public static Resource toResource(String uri, CamelContext camelContext) {
        return PluginHelper.getResourceLoader(camelContext).resolveResource(uri);
    }

}
