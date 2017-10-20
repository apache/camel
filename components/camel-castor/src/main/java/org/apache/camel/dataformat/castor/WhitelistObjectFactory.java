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
package org.apache.camel.dataformat.castor;

import org.apache.camel.util.EndpointHelper;
import org.exolab.castor.util.DefaultObjectFactory;

public class WhitelistObjectFactory extends DefaultObjectFactory {

    private String allowClasses;
    private String denyClasses;

    public String getAllowClasses() {
        return allowClasses;
    }

    public void setAllowClasses(String allowClasses) {
        this.allowClasses = allowClasses;
    }

    public String getDenyClasses() {
        return denyClasses;
    }

    public void setDenyClasses(String denyClasses) {
        this.denyClasses = denyClasses;
    }

    @Override
    public Object createInstance(Class type) throws IllegalAccessException, InstantiationException {
        if (allowCreate(type)) {
            return super.createInstance(type);
        } else {
            throw new IllegalAccessException("Not allowed to create class of type: " + type);
        }
    }

    @Override
    public Object createInstance(Class type, Object[] args) throws IllegalAccessException, InstantiationException {
        if (allowCreate(type)) {
            return super.createInstance(type, args);
        } else {
            throw new IllegalAccessException("Not allowed to create class of type: " + type);
        }
    }

    @Override
    public Object createInstance(Class type, Class[] argTypes, Object[] args) throws IllegalAccessException, InstantiationException {
        if (allowCreate(type)) {
            return super.createInstance(type, argTypes, args);
        } else {
            throw new IllegalAccessException("Not allowed to create class of type: " + type);
        }
    }

    private boolean allowCreate(Class type) {
        String name = type.getName();

        // deny takes precedence
        if (denyClasses != null) {
            String[] arr = denyClasses.split(",");
            for (String key : arr) {
                if (EndpointHelper.matchPattern(name, key)) {
                    return false;
                }
            }
        }

        // deny takes precedence
        if (allowClasses != null) {
            String[] arr = allowClasses.split(",");
            for (String key : arr) {
                if (EndpointHelper.matchPattern(name, key)) {
                    return true;
                }
            }
        }

        // deny by default
        return false;
    }
}
