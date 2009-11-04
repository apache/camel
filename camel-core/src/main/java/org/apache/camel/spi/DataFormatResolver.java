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

import org.apache.camel.CamelContext;
import org.apache.camel.model.DataFormatDefinition;

/**
 * Represents a resolver of data formats.
 *
 * @version $Revision$
 */
public interface DataFormatResolver {

    /**
     * Resolves the given data format by a reference.
     *
     * @param name the fully qualified classname
     * @param context the camel context
     * @return the data format or <tt>null</tt> if not possible to resolve
     */
    DataFormat resolveDataFormatByClassName(String name, CamelContext context);

    /**
     * Resolves the given data format by a reference.
     *
     * @param ref the reference to lookup in {@link org.apache.camel.spi.Registry}
     * @param context the camel context
     * @return the data format or <tt>null</tt> if not possible to resolve
     */
    DataFormat resolveDataFormatByRef(String ref, CamelContext context);

}
