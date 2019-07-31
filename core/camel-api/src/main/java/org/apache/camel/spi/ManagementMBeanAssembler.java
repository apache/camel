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

import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.modelmbean.ModelMBean;

import org.apache.camel.StaticService;

/**
 * An assembler to assemble a {@link javax.management.modelmbean.RequiredModelMBean} which can be used
 * to register the object in JMX.
 */
public interface ManagementMBeanAssembler extends StaticService {

    /**
     * Assemble the {@link javax.management.modelmbean.ModelMBean}.
     *
     * @param mBeanServer the mbean server
     * @param obj         the object
     * @param name        the object name to use in JMX
     * @return the assembled {@link javax.management.modelmbean.ModelMBean}, or <tt>null</tt> if not possible to assemble an MBean
     * @throws JMException is thrown if error assembling the mbean
     */
    ModelMBean assemble(MBeanServer mBeanServer, Object obj, ObjectName name) throws JMException;

}
