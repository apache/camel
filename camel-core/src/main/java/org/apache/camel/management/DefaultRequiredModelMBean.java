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

import javax.management.Descriptor;
import javax.management.MBeanException;
import javax.management.MBeanOperationInfo;
import javax.management.ReflectionException;
import javax.management.RuntimeOperationsException;
import javax.management.modelmbean.ModelMBeanInfo;
import javax.management.modelmbean.RequiredModelMBean;

import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;

/**
 * A {@link RequiredModelMBean} which allows us to intercept invoking operations.
 * <p/>
 * For example if sanitize has been enabled on JMX, then we use this implementation
 * to hide sensitive information from the returned JMX attributes / operations.
 */
public class DefaultRequiredModelMBean extends RequiredModelMBean {

    private final boolean sanitize;

    public DefaultRequiredModelMBean(ModelMBeanInfo mbi, boolean sanitize) throws MBeanException, RuntimeOperationsException {
        super(mbi);
        this.sanitize = sanitize;
    }

    @Override
    public Object invoke(String opName, Object[] opArgs, String[] sig) throws MBeanException, ReflectionException {
        Object answer = super.invoke(opName, opArgs, sig);
        // sanitize the answer if enabled and it was a String type (we cannot sanitize other types)
        if (sanitize && answer instanceof String && ObjectHelper.isNotEmpty(answer) && isSanitizedOperation(opName)) {
            answer = URISupport.sanitizeUri((String) answer);
        }
        return answer;
    }

    protected boolean isSanitizedOperation(String opName) {
        for (MBeanOperationInfo info : getMBeanInfo().getOperations()) {
            if (info.getName().equals(opName)) {
                Descriptor desc = info.getDescriptor();
                if (desc != null) {
                    Object val = desc.getFieldValue("sanitize");
                    return val != null && "true".equals(val);
                }
            }
        }
        return false;
    }
}
