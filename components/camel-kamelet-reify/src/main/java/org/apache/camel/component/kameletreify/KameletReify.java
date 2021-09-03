/*
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
package org.apache.camel.component.kameletreify;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.ExtendedCamelContext;

public final class KameletReify {
    public static final String SCHEME = "kamelet-reify";

    private KameletReify() {
    }

    public static Component newComponentInstance(CamelContext context, String scheme) throws Exception {
        // first check if there's an instance of the given component in the registry
        Component answer = context.getRegistry().lookupByNameAndType(scheme, Component.class);
        if (answer != null) {
            // and then create a new instance using it's class
            return context.getInjector().newInstance(answer.getClass());
        }

        // if not, fallback to the factory finder way
        answer = context.adapt(ExtendedCamelContext.class).getComponentResolver().resolveComponent(scheme, context);
        if (answer == null) {
            throw new IllegalStateException("Unable to create an instance of the component with scheme: " + scheme);
        }

        return answer;
    }
}
