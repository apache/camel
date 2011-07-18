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
package org.apache.camel.converter.myconverter;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.FallbackConverter;
import org.apache.camel.converter.MyCoolBean;
import org.apache.camel.spi.TypeConverterRegistry;

/**
 * @version 
 */
@Converter
public class MyFallbackPromoteConverter {

    @FallbackConverter(canPromote = true)
    public Object convertTo(Class<?> type, Exchange exchange, Object value, TypeConverterRegistry registry) {
        if (MyCoolBean.class.isAssignableFrom(value.getClass())) {
            return "This is cool: " + value.toString();
        }
        return null;
    }
}
