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
package org.apache.camel.spi;

import org.apache.camel.util.StringHelper;

/**
 * Represents the data type URN which is used for message data type contract.
 */
public class DataType {

    private String model;
    private String name;
    private boolean isJavaType;
    private String typeString;
    
    public DataType(String urn) {
        if (urn != null) {
            String split[] = StringHelper.splitOnCharacter(urn, ":", 2);
            model = split[0];
            isJavaType = model.equals("java");
            if (split.length > 1) {
                name = split[1];
            }
        }
    }
    
    public DataType(Class<?> clazz) {
        model = "java";
        isJavaType = true;
        name = clazz.getName();
    }
    
    public String getModel() {
        return model;
    }
    
    public String getName() {
        return name;
    }
    
    public boolean isJavaType() {
        return isJavaType;
    }

    @Override
    public String toString() {
        if (this.typeString == null) {
            this.typeString = model + ":" + name;
        }
        return this.typeString;
    }

    @Override
    public boolean equals(Object target) {
        if (target instanceof DataType) {
            DataType targetdt = (DataType)target;
            String targetModel = targetdt.getModel();
            String targetName = targetdt.getName();
            if (targetModel == null) {
                return false;
            } else if (targetName == null) {
                return targetModel.equals(getModel()) && getName() == null;
            } else {
                return targetModel.equals(getModel()) && targetName.equals(getName());
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }
}
