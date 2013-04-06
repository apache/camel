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
package org.apache.camel.component.stax;

import java.util.Map;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import org.apache.camel.util.LRUSoftCache;

public final class StAXUtil {
    private static final Map<Class<?>, String> TAG_NAMES = new LRUSoftCache<Class<?>, String>(1000);

    private StAXUtil() {
        // no-op
    }

    public static String getTagName(Class<?> handled) {
        if (TAG_NAMES.containsKey(handled)) {
            return TAG_NAMES.get(handled);
        }

        XmlType xmlType = handled.getAnnotation(XmlType.class);
        if (xmlType != null && xmlType.name() != null && xmlType.name().trim().length() > 0) {
            TAG_NAMES.put(handled, xmlType.name());
            return xmlType.name();
        } else {
            XmlRootElement xmlRoot = handled.getAnnotation(XmlRootElement.class);
            if (xmlRoot != null && xmlRoot.name() != null && xmlRoot.name().trim().length() > 0) {
                TAG_NAMES.put(handled, xmlRoot.name());
                return xmlRoot.name();
            }
        }
        throw new IllegalArgumentException("XML name not found for " + handled.getName());
    }
}
