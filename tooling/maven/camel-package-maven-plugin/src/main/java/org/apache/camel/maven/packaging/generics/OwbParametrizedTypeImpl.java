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
package org.apache.camel.maven.packaging.generics;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;

/**
 * Custom parametrized type implementation.
 *
 * @version $Rev: 1621935 $ $Date: 2014-09-02 09:07:32 +0200 (Tue, 02 Sep 2014)
 *          $
 */
public class OwbParametrizedTypeImpl implements ParameterizedType {
    /**
     * Owner type
     */
    private final Type owner;

    /**
     * Raw type
     */
    private final Type rawType;

    /**
     * Actual type arguments
     */
    private final Type[] types;

    /**
     * New instance.
     *
     * @param owner owner
     * @param raw raw
     */
    public OwbParametrizedTypeImpl(Type owner, Type raw, Type... types) {
        this.owner = owner;
        rawType = raw;
        this.types = types;
    }

    @Override
    public Type[] getActualTypeArguments() {
        return types.clone();
    }

    @Override
    public Type getOwnerType() {
        return owner;
    }

    @Override
    public Type getRawType() {
        return rawType;
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return Arrays.hashCode(types) ^ (owner == null ? 0 : owner.hashCode()) ^ (rawType == null ? 0 : rawType.hashCode());
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj instanceof ParameterizedType) {
            ParameterizedType that = (ParameterizedType)obj;
            Type thatOwnerType = that.getOwnerType();
            Type thatRawType = that.getRawType();
            return (owner == null ? thatOwnerType == null : owner.equals(thatOwnerType)) && (rawType == null ? thatRawType == null : rawType.equals(thatRawType))
                   && Arrays.equals(types, that.getActualTypeArguments());
        } else {
            return false;
        }

    }

    @Override
    public String toString() {
        StringBuilder buffer = new StringBuilder();
        buffer.append(((Class<?>)rawType).getName());
        Type[] actualTypes = getActualTypeArguments();
        if (actualTypes.length > 0) {
            buffer.append("<");
            int length = actualTypes.length;
            for (int i = 0; i < length; i++) {
                if (actualTypes[i] instanceof Class) {
                    buffer.append(((Class<?>)actualTypes[i]).getSimpleName());
                } else {
                    buffer.append(actualTypes[i].toString());
                }
                if (i != actualTypes.length - 1) {
                    buffer.append(", ");
                }
            }

            buffer.append(">");
        }

        return buffer.toString();
    }
}
