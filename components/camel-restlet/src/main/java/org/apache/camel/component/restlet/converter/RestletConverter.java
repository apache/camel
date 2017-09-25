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
package org.apache.camel.component.restlet.converter;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Converter;
import org.restlet.data.MediaType;
import org.restlet.data.Method;

/**
 *
 * @version 
 */
@Converter
public final class RestletConverter {

    private RestletConverter() {
    }

    @Converter
    public static Method toMethod(String name) {
        return Method.valueOf(name.toUpperCase());
    }
    
    @Converter
    public static Method[] toMethods(String name) {
        String[] strings = name.split(",");
        List<Method> methods = new ArrayList<Method>();
        for (String string : strings) {
            methods.add(toMethod(string));
        }
        
        return methods.toArray(new Method[methods.size()]);
    }
    
    @Converter
    public static MediaType toMediaType(String name) {
        return MediaType.valueOf(name);
    }

    @Converter
    public static MediaType[] toMediaTypes(final String name) {
        final String[] strings = name.split(",");
        final List<MediaType> answer = new ArrayList<>(strings.length);
        for (int i = 0; i < strings.length; i++) {
            final MediaType mediaType = toMediaType(strings[i]);
            if (mediaType != null) {
                answer.add(mediaType);
            }
        }

        return answer.toArray(new MediaType[answer.size()]);
    }
}
