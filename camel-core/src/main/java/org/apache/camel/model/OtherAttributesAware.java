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
package org.apache.camel.model;

import java.util.Map;
import javax.xml.namespace.QName;

/**
 * Models can support being configured with any other attributes to shadow existing options to be used for property placeholders.
 * <p/>
 * For example to override attributes that are configured as a boolean or integer type. Then the any attributes can be used to
 * override those existing attributes and supporting property placeholders.
 */
public interface OtherAttributesAware {

    /**
     * Adds optional attribute to use as property placeholder
     */
    Map<QName, Object> getOtherAttributes();

    /**
     * Adds optional attribute to use as property placeholder
     */
    void setOtherAttributes(Map<QName, Object> otherAttributes);

}
