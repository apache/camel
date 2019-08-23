/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.spi;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.util.function.TriConsumer;

/**
 * Property configurer for Camel {@link org.apache.camel.Endpoint} or {@link org.apache.camel.Component}
 * which allows fast configurations without using Java reflection.
 */
public interface TriPropertyConfigurer extends PropertyConfigurer {

    /**
     * To update properties using the tri-function.
     * 
     * The key in the map is the property name.
     * The 1st parameter in the tri-function is {@link CamelContext}
     * The 2nd parameter in the tri-function is the target object
     * The 3rd parameter in the tri-function is the value
     */
    Map<String, TriConsumer<CamelContext, Object, Object>> getWriteOptions(CamelContext camelContext);

}
