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
package org.apache.camel.component.extension;

import java.util.Map;
import java.util.Optional;

import org.apache.camel.Exchange;
import org.apache.camel.TypeConversionException;

public interface MetaDataExtension extends ComponentExtension {
    /**
     * @param parameters
     * @return the {@link MetaData}
     */
    Optional<MetaData> meta(Map<String, Object> parameters);

    interface MetaData {
        // Common meta-data attributes
        String CONTENT_TYPE = Exchange.CONTENT_TYPE;
        String JAVA_TYPE = "Java-Type";

        /**
         * Returns an attribute associated with this meta data by name.
         *
         * @param name the attribute name
         * @return the attribute
         */
        Object getAttribute(String name);

        /**
         * @return a read-only list of attributes.
         */
        Map<String, Object> getAttributes();

        /**
         *
         * Returns an attribute associated with this meta data by name and
         * specifying the type required.
         *
         * @param name the attribute name
         * @param type the type of the attribute
         * @return the value of the given attribute or <tt>null</tt> if there is no attribute for the given name
         * @throws TypeConversionException is thrown if error during type conversion
         */
        <T> T getAttribute(String name, Class<T> type);

        /**
         * Returns the payload of the meta data as a POJO.
         *
         * @return the body, can be <tt>null</tt>
         */
        Object getPayload();

        /**
         * Returns the payload of the meta data as specified type.
         *
         * @param type the type that the payload should be converted yo.
         * @return the payload of the meta data as the specified type.
         * @throws TypeConversionException is thrown if error during type conversion
         */
        <T> T getPayload(Class<T> type);
    }
}
