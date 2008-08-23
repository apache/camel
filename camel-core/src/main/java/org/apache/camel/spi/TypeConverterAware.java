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
package org.apache.camel.spi;

import org.apache.camel.TypeConverter;

/**
 * An interface for an object which is interested in being injected with the root {@link TypeConverter}
 * such as for implementing a fallback type converter
 *
 * @see org.apache.camel.impl.converter.DefaultTypeConverter#addFallbackConverter(TypeConverter)
 *         DefaultTypeConverter.addFallbackConverter
 * @version $Revision$
 */
public interface TypeConverterAware {

    /**
     * Injects the root type converter.
     *
     * @param parentTypeConverter the root type converter
     */
    void setTypeConverter(TypeConverter parentTypeConverter);
}
