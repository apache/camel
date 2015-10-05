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
package org.apache.camel.dataformat.bindy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.support.ServiceSupport;

public abstract class BindyAbstractDataFormat extends ServiceSupport implements DataFormat, DataFormatName {
    private String locale;
    private BindyAbstractFactory modelFactory;
    private Class<?> classType;

    public BindyAbstractDataFormat() {
    }
    
    protected BindyAbstractDataFormat(Class<?> classType) {
        this.classType = classType;
    }

    public Class<?> getClassType() {
        return classType;
    }

    public void setClassType(Class<?> classType) {
        this.classType = classType;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }
    
    public BindyAbstractFactory getFactory() throws Exception {
        if (modelFactory == null) {
            modelFactory = createModelFactory();
            modelFactory.setLocale(locale);
        }
        return modelFactory;
    }
    
    public void setModelFactory(BindyAbstractFactory modelFactory) {
        this.modelFactory = modelFactory;
    }
    
    protected abstract BindyAbstractFactory createModelFactory() throws Exception;

    protected Object extractUnmarshalResult(List<Map<String, Object>> models) {
        if (getClassType() != null) {
            // we expect to find this type in the models, and grab only that type
            List<Object> answer = new ArrayList<Object>();
            for (Map<String, Object> entry : models) {
                Object data = entry.get(getClassType().getName());
                if (data != null) {
                    answer.add(data);
                }
            }
            // if there is only 1 then dont return a list
            if (answer.size() == 1) {
                return answer.get(0);
            } else {
                return answer;
            }
        } else {
            return models;
        }
    }

    @Override
    protected void doStart() throws Exception {
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }
}
