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
package org.apache.camel.component.context;

import org.apache.camel.CamelContext;
import org.apache.camel.Converter;

/**
 * Converts from a {@link CamelContext} to a {@link LocalContextComponent} so we can easily refer to
 * external {@link CamelContext}s in the {@link org.apache.camel.spi.Registry} as a {@link org.apache.camel.Component}
 */
@Converter
public final class ContextConverters {

    private ContextConverters() {
    }

    @Converter
    public static LocalContextComponent toComponent(CamelContext localContext) {
        return new LocalContextComponent(localContext);
    }

}
