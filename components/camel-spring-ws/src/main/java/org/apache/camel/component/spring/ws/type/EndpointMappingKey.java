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
package org.apache.camel.component.spring.ws.type;

import org.springframework.xml.xpath.XPathExpression;

public class EndpointMappingKey {
    private final EndpointMappingType type;
    private final String lookupKey;
    private final XPathExpression expression;

    public EndpointMappingKey(EndpointMappingType type, String lookupKey, XPathExpression expression) {
        this.type = type;
        this.lookupKey = lookupKey;
        this.expression = expression;
    }

    public EndpointMappingType getType() {
        return type;
    }

    public String getLookupKey() {
        return lookupKey;
    }

    public XPathExpression getExpression() {
        return expression;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((lookupKey == null) ? 0 : lookupKey.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }

        EndpointMappingKey other = (EndpointMappingKey) obj;
        if (lookupKey == null) {
            if (other.lookupKey != null) {
                return false;
            }
        } else if (!lookupKey.equals(other.lookupKey)) {
            return false;
        }
        return true;
    }
}