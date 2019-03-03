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
package org.apache.camel.core.osgi.other;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.FallbackConverter;
import org.apache.camel.TypeConverter;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.spi.TypeConverterRegistry;

@Converter
public final class MyOtherTypeConverter {

    /**
     * Utility classes should not have a public constructor.
     */
    private MyOtherTypeConverter() {
    }
    
    /**
     * Converts the given value to a boolean, handling strings or Boolean
     * objects; otherwise returning false if the value could not be converted to
     * a boolean
     */
    @Converter
    public static boolean toBool(Object value) {
        Boolean answer = null;    
        if (value instanceof String) {
            answer = Boolean.valueOf((String)value);
        } 
        if (value instanceof Boolean) {
            answer = (Boolean) value;
        }
        if (answer != null) {
            return answer.booleanValue();
        }
        return false;
    }
    
    @FallbackConverter
    public static Object convertTo(Class<?> type, Exchange exchange, Object value, TypeConverterRegistry registry) {
        // use a fallback type converter so we can convert the embedded body if the value is GenericFile
        if (GenericFile.class.isAssignableFrom(value.getClass())) {
            GenericFile<?> file = (GenericFile<?>) value;
            Class<?> from = file.getBody().getClass();

            // maybe from is already the type we want
            if (from.isAssignableFrom(type)) {
                return file.getBody();
            }
            // no then try to lookup a type converter
            TypeConverter tc = registry.lookup(type, from);
            if (tc != null) {
                Object body = file.getBody();
                return tc.convertTo(type, exchange, body);
            }
        }
        
        return null;
    }
    

}
