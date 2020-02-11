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
package org.apache.camel.support.component;

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.support.EndpointHelper;

/**
 * Base class used by Camel Package Maven Plugin when it generates source code for fast
 * property configurations via {@link org.apache.camel.spi.PropertyConfigurer}.
 */
public abstract class PropertyConfigurerSupport {

    /**
     * Converts the property to the expected type
     *
     * @param camelContext   the camel context
     * @param type           the expected type
     * @param value          the value
     * @return  the value converted to the expected type
     */
    public static <T> T property(CamelContext camelContext, Class<T> type, Object value) {
        // if the type is not string based and the value is a bean reference, then we need to lookup
        // the bean from the registry
        if (value instanceof String && String.class != type) {
            Object obj = null;

            String text = value.toString();
            if (EndpointHelper.isReferenceParameter(text)) {
                // special for a list where we refer to beans which can be either a list or a single element
                // so use Object.class as type
                if (type == List.class) {
                    obj = EndpointHelper.resolveReferenceListParameter(camelContext, text, Object.class);
                } else {
                    obj = EndpointHelper.resolveReferenceParameter(camelContext, text, type);
                }
                if (obj == null) {
                    // no bean found so throw an exception
                    throw new NoSuchBeanException(text, type.getName());
                }
            }
            if (obj != null) {
                value = obj;
            }
        }

        // special for boolean values with string values as we only want to accept "true" or "false"
        if ((type == Boolean.class || type == boolean.class) && value instanceof String) {
            String text = (String) value;
            if (!text.equalsIgnoreCase("true") && !text.equalsIgnoreCase("false")) {
                throw new IllegalArgumentException("Cannot convert the String value: " + value + " to type: " + type
                        + " as the value is not true or false");
            }
        }

        return camelContext.getTypeConverter().convertTo(type, value);
    }

}
