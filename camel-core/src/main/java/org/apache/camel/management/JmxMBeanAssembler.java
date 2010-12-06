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

import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.modelmbean.InvalidTargetObjectTypeException;
import javax.management.modelmbean.ModelMBeanInfo;
import javax.management.modelmbean.RequiredModelMBean;

import org.apache.camel.management.mbean.ManagedCustomProcessor;
import org.springframework.jmx.export.annotation.AnnotationJmxAttributeSource;
import org.springframework.jmx.export.assembler.MetadataMBeanInfoAssembler;

/**
 * An assembler to assemble a {@link javax.management.modelmbean.RequiredModelMBean} which can be used
 * to register the object in JMX. The assembler is capable of using the Spring JMX annotations to
 * gather the list of JMX operations and attributes.
 *
 * @version $Revision$
 */
public class JmxMBeanAssembler {

    private final MetadataMBeanInfoAssembler assembler;
    private final MBeanServer server;

    public JmxMBeanAssembler(MBeanServer server) {
        this.server = server;
        this.assembler = new MetadataMBeanInfoAssembler();
        this.assembler.setAttributeSource(new AnnotationJmxAttributeSource());
    }

    public RequiredModelMBean assemble(Object obj, ObjectName name) throws JMException {
        ModelMBeanInfo mbi;

        if (obj instanceof ManagedCustomProcessor) {
            Object custom = ((ManagedCustomProcessor) obj).getManagedObject();
            // get the mbean info from the custom managed object
            mbi = assembler.getMBeanInfo(custom, name.toString());
            // and let the custom object be registered in JMX
            obj = custom;
        } else {
            mbi = assembler.getMBeanInfo(obj, name.toString());
        }

        RequiredModelMBean mbean = (RequiredModelMBean) server.instantiate(RequiredModelMBean.class.getName());
        mbean.setModelMBeanInfo(mbi);

        try {
            mbean.setManagedResource(obj, "ObjectReference");
        } catch (InvalidTargetObjectTypeException itotex) {
            throw new JMException(itotex.getMessage());
        }

        return mbean;
    }

}
