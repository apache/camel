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

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.util.ObjectHelper;

public final class ComponentExtensionHelper {
    private ComponentExtensionHelper() {
    }

    /**
     * @deprecated use {@link ObjectHelper#trySetCamelContext(Object, CamelContext)}
     */
    @Deprecated
    public static <T> T trySetCamelContext(T object, CamelContext camelContext) {
        return ObjectHelper.trySetCamelContext(object, camelContext);
    }

    /**
     * @deprecated use {@link ObjectHelper#trySetComponent(Object, Component)}
     */
    @Deprecated
    public static <T> T trySetComponent(T object, Component component) {
        return ObjectHelper.trySetComponent(object, component);
    }
}
