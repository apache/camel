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
package org.apache.camel.util;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.util.jndi.ExampleBean;

/**
 * Unit test for IntrospectionSupport 
 */
public class IntrospectionSupportTest extends ContextTestSupport {

    public void testOverloadSetterChooseStringSetter() throws Exception {
        MyOverloadedBean overloadedBean = new MyOverloadedBean();
        IntrospectionSupport.setProperty(context.getTypeConverter(), overloadedBean, "bean", "James");
        assertEquals("James", overloadedBean.getName());
    }

    public void testOverloadSetterChooseBeanSetter() throws Exception {
        MyOverloadedBean overloadedBean = new MyOverloadedBean();
        ExampleBean bean = new ExampleBean();
        bean.setName("Claus");
        IntrospectionSupport.setProperty(context.getTypeConverter(), overloadedBean, "bean", bean);
        assertEquals("Claus", overloadedBean.getName());
    }

    public void testOverloadSetterChooseUsingTypeConverter() throws Exception {
        MyOverloadedBean overloadedBean = new MyOverloadedBean();
        Object value = "Willem".getBytes();
        // should use byte[] -> String type converter and call the setBean(String) setter method 
        IntrospectionSupport.setProperty(context.getTypeConverter(), overloadedBean, "bean", value);
        assertEquals("Willem", overloadedBean.getName());
    }

    public class MyOverloadedBean {
        private ExampleBean bean;

        public void setBean(ExampleBean bean) {
            this.bean = bean;
        }

        public void setBean(String name) {
            bean = new ExampleBean();
            bean.setName(name);
        }

        public String getName() {
            return bean.getName();
        }
    }

}

