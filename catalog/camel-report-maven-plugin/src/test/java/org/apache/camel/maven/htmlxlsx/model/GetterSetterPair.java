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
package org.apache.camel.maven.htmlxlsx.model;

import java.lang.reflect.Method;

/**
 * A utility class which holds a related getter and setter method.
 */
public class GetterSetterPair {

    /** The get method. */
    private Method getter;

    /** The set method. */
    private Method setter;

    /**
     * Returns the get method.
     *
     * @return The get method.
     */
    public Method getGetter() {

        return getter;
    }

    /**
     * Returns the set method.
     *
     * @return The set method.
     */
    public Method getSetter() {

        return setter;
    }

    /**
     * Returns if this has a getter and setting method set.
     *
     * @return If this has a getter and setting method set.
     */
    public boolean hasGetterAndSetter() {

        return this.getter != null && this.setter != null;
    }

    /**
     * Sets the get Method.
     *
     * @param getter The get Method.
     */
    public void setGetter(Method getter) {

        this.getter = getter;
    }

    /**
     * Sets the set Method.
     *
     * @param setter The set Method.
     */
    public void setSetter(Method setter) {

        this.setter = setter;
    }
}
