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
package org.apache.camel.management;

import javax.management.DynamicMBean;
import javax.management.MBeanException;
import javax.management.MBeanOperationInfo;
import javax.management.ReflectionException;
import javax.management.RuntimeOperationsException;
import javax.management.modelmbean.ModelMBeanInfo;
import javax.management.modelmbean.RequiredModelMBean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link javax.management.modelmbean.RequiredModelMBean} which allows us to intercept invoking operations on the MBean.
 * <p/>
 * This allows us to intercept calls to custom mbeans where allows us to mix-in the standard set of mbean attributes
 * and operations that Camel provides out of the box.
 */
@Deprecated
public class CustomRequiredModelMBean extends RequiredModelMBean {

    private static final Logger LOG = LoggerFactory.getLogger(CustomRequiredModelMBean.class);
    private ModelMBeanInfo defaultMbi;
    private DynamicMBean defaultObject;

    public CustomRequiredModelMBean() throws MBeanException, RuntimeOperationsException {
        // must have default no-arg constructor
    }

    public CustomRequiredModelMBean(ModelMBeanInfo mbi, ModelMBeanInfo defaultMbi, DynamicMBean defaultObject) throws MBeanException, RuntimeOperationsException {
        super(mbi);
        this.defaultMbi = defaultMbi;
        this.defaultObject = defaultObject;
    }

    @Override
    public Object invoke(String opName, Object[] opArgs, String[] sig) throws MBeanException, ReflectionException {
        Object answer;
        if (isDefaultOperation(opName)) {
            answer = defaultObject.invoke(opName, opArgs, sig);
        } else {
            answer = super.invoke(opName, opArgs, sig);
        }
        return answer;
    }

    protected boolean isDefaultOperation(String opName) {
        if (defaultMbi == null || defaultObject == null) {
            return false;
        }
        for (MBeanOperationInfo info : defaultMbi.getOperations()) {
            if (info.getName().equals(opName)) {
                return true;
            }
        }
        return false;
    }

}
