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
package org.apache.camel;


/**
 * A pluggable strategy to be able to convert objects <a
 * href="http://activemq.apache.org/camel/type-converter.html">to different
 * types</a> such as to and from String, InputStream/OutputStream,
 * Reader/Writer, Document, byte[], ByteBuffer etc
 * 
 * @version $Revision$
 */
public interface TypeConverter {

    /**
     * Converts the value to the specified type
     * 
     * @param type the requested type
     * @param value the value to be converted
     * @return the converted value
     * @throws {@link NoTypeConversionAvailableException} if conversion not possible
     */
    <T> T convertTo(Class<T> type, Object value);

    /**
     * Converts the value to the specified type in the context of an exchange
     * <p/>
     * Used when conversion requires extra information from the current
     * exchange (such as encoding).
     *
     * @param type the requested type
     * @param exchange the current exchange
     * @param value the value to be converted
     * @return the converted value
     * @throws {@link NoTypeConversionAvailableException} if conversion not possible
     */
    <T> T convertTo(Class<T> type, Exchange exchange, Object value);
}
