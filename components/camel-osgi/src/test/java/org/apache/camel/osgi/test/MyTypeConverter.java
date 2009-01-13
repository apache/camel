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
package org.apache.camel.osgi.test;

import java.util.Collection;

import org.apache.camel.Converter;
import org.apache.camel.util.ObjectHelper;

@Converter
public final class MyTypeConverter {

    /**
     * Utility classes should not have a public constructor.
     */
    private MyTypeConverter() {
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
    

}
