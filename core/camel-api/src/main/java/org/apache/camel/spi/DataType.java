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
package org.apache.camel.spi;

import org.apache.camel.util.StringHelper;

/**
 * Represents the data type URN which is used for message data type contract.
 * <p/>
 * Java class doesn't always explain the data type completely, for example XML and JSON
 * data format is sometimes serialized as a {@code String}, {@code InputStream} or etc.
 * The {@link DataTypeAware} message stores the DataType as a part of the message to carry
 * those data type information even if it's marshaled, so that it could be
 * leveraged to detect required {@link Transformer} and {@link Validator}.
 * DataType consists of two parts, 'model' and 'name'. Its string representation is
 * 'model:name' connected with colon. For example 'java:com.example.Order', 'xml:ABCOrder'
 * or 'json:XYZOrder'. These type name other than java class name allows the message to
 * carry the name of the message data structure even if it's marshaled.
 * 
 * @see DataTypeAware
 * @see Transformer
 * @see Validator
 */
public class DataType {

    public static final String JAVA_TYPE_PREFIX = "java";

    private String model;
    private String name;
    private boolean isJavaType;
    private String typeString;
    
    public DataType(String urn) {
        if (urn != null) {
            String[] split = StringHelper.splitOnCharacter(urn, ":", 2);
            model = split[0];
            isJavaType = model.equals(JAVA_TYPE_PREFIX);
            if (split.length > 1) {
                name = split[1];
            }
        }
    }
    
    public DataType(Class<?> clazz) {
        model = JAVA_TYPE_PREFIX;
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
            this.typeString = name != null && !name.isEmpty() ? model + ":" + name : model;
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
