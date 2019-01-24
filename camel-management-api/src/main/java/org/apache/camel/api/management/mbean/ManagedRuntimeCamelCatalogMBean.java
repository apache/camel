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
package org.apache.camel.api.management.mbean;

import org.apache.camel.api.management.ManagedOperation;

public interface ManagedRuntimeCamelCatalogMBean {

    @ManagedOperation(description = "Returns the component information as JSon format")
    String componentJSonSchema(String name);

    @ManagedOperation(description = "Returns the data format information as JSon format.")
    String dataFormatJSonSchema(String name);

    @ManagedOperation(description = "Returns the language information as JSon format")
    String languageJSonSchema(String name);

    @ManagedOperation(description = "Returns the model information as JSon format")
    String modelJSonSchema(String name);

}
