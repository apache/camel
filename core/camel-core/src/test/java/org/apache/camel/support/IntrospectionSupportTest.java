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
package org.apache.camel.support;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.support.jndi.ExampleBean;
import org.apache.camel.util.AnotherExampleBean;
import org.apache.camel.util.OtherExampleBean;
import org.junit.Test;

/**
 * Unit test for IntrospectionSupport
 */
public class IntrospectionSupportTest extends ContextTestSupport {

    @Test
    public void testOverloadSetterChooseStringSetter() throws Exception {
        MyOverloadedBean overloadedBean = new MyOverloadedBean();
        IntrospectionSupport.setProperty(context.getTypeConverter(), overloadedBean, "bean", "James");
        assertEquals("James", overloadedBean.getName());
    }

    @Test
    public void testOverloadSetterChooseBeanSetter() throws Exception {
        MyOverloadedBean overloadedBean = new MyOverloadedBean();
        ExampleBean bean = new ExampleBean();
        bean.setName("Claus");
        IntrospectionSupport.setProperty(context.getTypeConverter(), overloadedBean, "bean", bean);
        assertEquals("Claus", overloadedBean.getName());
    }

    @Test
    public void testOverloadSetterChooseUsingTypeConverter() throws Exception {
        MyOverloadedBean overloadedBean = new MyOverloadedBean();
        Object value = "Willem".getBytes();
        // should use byte[] -> String type converter and call the
        // setBean(String) setter method
        IntrospectionSupport.setProperty(context.getTypeConverter(), overloadedBean, "bean", value);
        assertEquals("Willem", overloadedBean.getName());
    }

    @Test
    public void testPassword() throws Exception {
        MyPasswordBean passwordBean = new MyPasswordBean();
        IntrospectionSupport.setProperty(context.getTypeConverter(), passwordBean, "oldPassword", "Donald");
        IntrospectionSupport.setProperty(context.getTypeConverter(), passwordBean, "newPassword", "Duck");
        assertEquals("Donald", passwordBean.getOldPassword());
        assertEquals("Duck", passwordBean.getNewPassword());
    }

    public class MyPasswordBean {
        private String oldPassword;
        private String newPassword;

        public String getOldPassword() {
            return oldPassword;
        }

        public void setOldPassword(String oldPassword) {
            this.oldPassword = oldPassword;
        }

        public String getNewPassword() {
            return newPassword;
        }

        public void setNewPassword(String newPassword) {
            this.newPassword = newPassword;
        }
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

    public class MyBuilderBean {
        private String name;

        public String getName() {
            return name;
        }

        public MyBuilderBean setName(String name) {
            this.name = name;

            return this;
        }
    }

    public class MyOtherBuilderBean extends MyBuilderBean {
    }

    public class MyOtherOtherBuilderBean extends MyOtherBuilderBean {

        @Override
        public MyOtherOtherBuilderBean setName(String name) {
            super.setName(name);
            return this;
        }
    }

    @Test
    public void testBuilderPatternWith() throws Exception {
        MyBuilderPatternWithBean builderBean = new MyBuilderPatternWithBean();
        IntrospectionSupport.setProperty(context.getTypeConverter(), builderBean, "name", "Donald");
        IntrospectionSupport.setProperty(context.getTypeConverter(), builderBean, "age", "33");
        IntrospectionSupport.setProperty(context.getTypeConverter(), builderBean, "gold-customer", "true");
        assertEquals("Donald", builderBean.getName());
        assertEquals(33, builderBean.getAge());
        assertTrue(builderBean.isGoldCustomer());
    }

    public class MyBuilderPatternWithBean {
        private String name;
        private int age;
        private boolean goldCustomer;

        public MyBuilderPatternWithBean withName(String name) {
            this.name = name;
            return this;
        }

        public MyBuilderPatternWithBean withAge(int age) {
            this.age = age;
            return this;
        }

        public MyBuilderPatternWithBean withGoldCustomer(boolean goldCustomer) {
            this.goldCustomer = goldCustomer;
            return this;
        }

        public String getName() {
            return name;
        }

        public int getAge() {
            return age;
        }

        public boolean isGoldCustomer() {
            return goldCustomer;
        }
    }

    @Test
    public void testBuilderPattern() throws Exception {
        MyBuilderPatternBean builderBean = new MyBuilderPatternBean();
        IntrospectionSupport.setProperty(context.getTypeConverter(), builderBean, "name", "Goofy");
        IntrospectionSupport.setProperty(context.getTypeConverter(), builderBean, "age", "34");
        IntrospectionSupport.setProperty(context.getTypeConverter(), builderBean, "gold-customer", "true");
        assertEquals("Goofy", builderBean.getName());
        assertEquals(34, builderBean.getAge());
        assertTrue(builderBean.isGoldCustomer());
    }

    public class MyBuilderPatternBean {
        private String name;
        private int age;
        private boolean goldCustomer;

        public MyBuilderPatternBean name(String name) {
            this.name = name;
            return this;
        }

        public MyBuilderPatternBean age(int age) {
            this.age = age;
            return this;
        }

        public MyBuilderPatternBean goldCustomer(boolean goldCustomer) {
            this.goldCustomer = goldCustomer;
            return this;
        }

        public String getName() {
            return name;
        }

        public int getAge() {
            return age;
        }

        public boolean isGoldCustomer() {
            return goldCustomer;
        }
    }

    @Test
    public void testIsSetterBuilderPatternSupport() throws Exception {
        Method setter = MyBuilderBean.class.getMethod("setName", String.class);
        Method setter2 = MyOtherBuilderBean.class.getMethod("setName", String.class);
        Method setter3 = MyOtherOtherBuilderBean.class.getMethod("setName", String.class);

        assertFalse(IntrospectionSupport.isSetter(setter, false));
        assertTrue(IntrospectionSupport.isSetter(setter, true));

        assertFalse(IntrospectionSupport.isSetter(setter2, false));
        assertTrue(IntrospectionSupport.isSetter(setter2, true));

        assertFalse(IntrospectionSupport.isSetter(setter3, false));
        assertTrue(IntrospectionSupport.isSetter(setter3, true));
    }

    @Test
    public void testHasProperties() throws Exception {
        Map<String, Object> empty = Collections.emptyMap();
        assertFalse(IntrospectionSupport.hasProperties(empty, null));
        assertFalse(IntrospectionSupport.hasProperties(empty, ""));
        assertFalse(IntrospectionSupport.hasProperties(empty, "foo."));

        Map<String, Object> param = new HashMap<>();
        assertFalse(IntrospectionSupport.hasProperties(param, null));
        assertFalse(IntrospectionSupport.hasProperties(param, ""));
        assertFalse(IntrospectionSupport.hasProperties(param, "foo."));

        param.put("name", "Claus");
        assertTrue(IntrospectionSupport.hasProperties(param, null));
        assertTrue(IntrospectionSupport.hasProperties(param, ""));
        assertFalse(IntrospectionSupport.hasProperties(param, "foo."));

        param.put("foo.name", "Hadrian");
        assertTrue(IntrospectionSupport.hasProperties(param, null));
        assertTrue(IntrospectionSupport.hasProperties(param, ""));
        assertTrue(IntrospectionSupport.hasProperties(param, "foo."));
    }

    @Test
    public void testGetProperties() throws Exception {
        ExampleBean bean = new ExampleBean();
        bean.setName("Claus");
        bean.setPrice(10.0);

        Map<String, Object> map = new HashMap<>();
        IntrospectionSupport.getProperties(bean, map, null);
        assertEquals(3, map.size());

        assertEquals("Claus", map.get("name"));
        String price = map.get("price").toString();
        assertTrue(price.startsWith("10"));

        assertEquals(null, map.get("id"));
    }

    @Test
    public void testAnotherGetProperties() throws Exception {
        AnotherExampleBean bean = new AnotherExampleBean();
        bean.setId("123");
        bean.setName("Claus");
        bean.setPrice(10.0);
        Date date = new Date(0);
        bean.setDate(date);
        bean.setGoldCustomer(true);
        bean.setLittle(true);
        Collection<?> children = new ArrayList<>();
        bean.setChildren(children);

        Map<String, Object> map = new HashMap<>();
        IntrospectionSupport.getProperties(bean, map, null);
        assertEquals(7, map.size());

        assertEquals("Claus", map.get("name"));
        String price = map.get("price").toString();
        assertTrue(price.startsWith("10"));
        assertSame(date, map.get("date"));
        assertSame(children, map.get("children"));
        assertEquals(Boolean.TRUE, map.get("goldCustomer"));
        assertEquals(Boolean.TRUE, map.get("little"));
        assertEquals("123", map.get("id"));
    }

    @Test
    public void testGetPropertiesOptionPrefix() throws Exception {
        ExampleBean bean = new ExampleBean();
        bean.setName("Claus");
        bean.setPrice(10.0);
        bean.setId("123");

        Map<String, Object> map = new HashMap<>();
        IntrospectionSupport.getProperties(bean, map, "bean.");
        assertEquals(3, map.size());

        assertEquals("Claus", map.get("bean.name"));
        String price = map.get("bean.price").toString();
        assertTrue(price.startsWith("10"));
        assertEquals("123", map.get("bean.id"));
    }

    @Test
    public void testGetPropertiesSkipNull() throws Exception {
        ExampleBean bean = new ExampleBean();
        bean.setName("Claus");
        bean.setPrice(10.0);
        bean.setId(null);

        Map<String, Object> map = new HashMap<>();
        IntrospectionSupport.getProperties(bean, map, null, false);
        assertEquals(2, map.size());

        assertEquals("Claus", map.get("name"));
        String price = map.get("price").toString();
        assertTrue(price.startsWith("10"));
    }

    @Test
    public void testGetProperty() throws Exception {
        ExampleBean bean = new ExampleBean();
        bean.setId("123");
        bean.setName("Claus");
        bean.setPrice(10.0);

        Object name = IntrospectionSupport.getProperty(bean, "name");
        assertEquals("Claus", name);
    }

    @Test
    public void testSetProperty() throws Exception {
        ExampleBean bean = new ExampleBean();
        bean.setId("123");
        bean.setName("Claus");
        bean.setPrice(10.0);

        IntrospectionSupport.setProperty(context, bean, "name", "James");
        assertEquals("James", bean.getName());
    }

    @Test
    public void testSetPropertyDash() throws Exception {
        AnotherExampleBean bean = new AnotherExampleBean();
        bean.setName("Claus");
        bean.setPrice(10.0);
        Date date = new Date(0);
        bean.setDate(date);
        bean.setGoldCustomer(true);
        bean.setLittle(true);

        IntrospectionSupport.setProperty(context, bean, "name", "James");
        IntrospectionSupport.setProperty(context, bean, "gold-customer", "false");
        assertEquals("James", bean.getName());
        assertEquals(false, bean.isGoldCustomer());
    }

    @Test
    public void testAnotherGetProperty() throws Exception {
        AnotherExampleBean bean = new AnotherExampleBean();
        bean.setName("Claus");
        bean.setPrice(10.0);
        Date date = new Date(0);
        bean.setDate(date);
        bean.setGoldCustomer(true);
        bean.setLittle(true);
        Collection<?> children = new ArrayList<>();
        bean.setChildren(children);

        Object name = IntrospectionSupport.getProperty(bean, "name");
        assertEquals("Claus", name);
        assertSame(date, IntrospectionSupport.getProperty(bean, "date"));
        assertSame(children, IntrospectionSupport.getProperty(bean, "children"));
        assertEquals(Boolean.TRUE, IntrospectionSupport.getProperty(bean, "goldCustomer"));
        assertEquals(Boolean.TRUE, IntrospectionSupport.getProperty(bean, "gold-customer"));
        assertEquals(Boolean.TRUE, IntrospectionSupport.getProperty(bean, "little"));
    }

    @Test
    public void testGetPropertyLocaleIndependent() throws Exception {
        Locale oldLocale = Locale.getDefault();
        Locale.setDefault(new Locale("tr", "TR"));

        try {
            ExampleBean bean = new ExampleBean();
            bean.setName("Claus");
            bean.setPrice(10.0);
            bean.setId("1");

            Object name = IntrospectionSupport.getProperty(bean, "name");
            Object id = IntrospectionSupport.getProperty(bean, "id");
            Object price = IntrospectionSupport.getProperty(bean, "price");

            assertEquals("Claus", name);
            assertEquals(10.0, price);
            assertEquals("1", id);
        } finally {
            Locale.setDefault(oldLocale);
        }
    }

    @Test
    public void testGetPropertyGetter() throws Exception {
        ExampleBean bean = new ExampleBean();
        bean.setName("Claus");
        bean.setPrice(10.0);

        Method name = IntrospectionSupport.getPropertyGetter(ExampleBean.class, "name");
        assertEquals("getName", name.getName());

        try {
            IntrospectionSupport.getPropertyGetter(ExampleBean.class, "xxx");
            fail("Should have thrown exception");
        } catch (NoSuchMethodException e) {
            assertEquals("org.apache.camel.support.jndi.ExampleBean.getXxx()", e.getMessage());
        }
    }

    @Test
    public void testGetPropertySetter() throws Exception {
        ExampleBean bean = new ExampleBean();
        bean.setName("Claus");
        bean.setPrice(10.0);

        Method name = IntrospectionSupport.getPropertySetter(ExampleBean.class, "name");
        assertEquals("setName", name.getName());

        try {
            IntrospectionSupport.getPropertySetter(ExampleBean.class, "xxx");
            fail("Should have thrown exception");
        } catch (NoSuchMethodException e) {
            assertEquals("org.apache.camel.support.jndi.ExampleBean.setXxx", e.getMessage());
        }
    }

    @Test
    public void testIsGetter() throws Exception {
        ExampleBean bean = new ExampleBean();

        Method name = bean.getClass().getMethod("getName", (Class<?>[])null);
        assertEquals(true, IntrospectionSupport.isGetter(name));
        assertEquals(false, IntrospectionSupport.isSetter(name));

        Method price = bean.getClass().getMethod("getPrice", (Class<?>[])null);
        assertEquals(true, IntrospectionSupport.isGetter(price));
        assertEquals(false, IntrospectionSupport.isSetter(price));
    }

    @Test
    public void testIsSetter() throws Exception {
        ExampleBean bean = new ExampleBean();

        Method name = bean.getClass().getMethod("setName", String.class);
        assertEquals(false, IntrospectionSupport.isGetter(name));
        assertEquals(true, IntrospectionSupport.isSetter(name));

        Method price = bean.getClass().getMethod("setPrice", double.class);
        assertEquals(false, IntrospectionSupport.isGetter(price));
        assertEquals(true, IntrospectionSupport.isSetter(price));
    }

    @Test
    public void testOtherIsGetter() throws Exception {
        OtherExampleBean bean = new OtherExampleBean();

        Method customerId = bean.getClass().getMethod("getCustomerId", (Class<?>[])null);
        assertEquals(true, IntrospectionSupport.isGetter(customerId));
        assertEquals(false, IntrospectionSupport.isSetter(customerId));

        Method goldCustomer = bean.getClass().getMethod("isGoldCustomer", (Class<?>[])null);
        assertEquals(true, IntrospectionSupport.isGetter(goldCustomer));
        assertEquals(false, IntrospectionSupport.isSetter(goldCustomer));

        Method silverCustomer = bean.getClass().getMethod("isSilverCustomer", (Class<?>[])null);
        assertEquals(true, IntrospectionSupport.isGetter(silverCustomer));
        assertEquals(false, IntrospectionSupport.isSetter(silverCustomer));

        Method company = bean.getClass().getMethod("getCompany", (Class<?>[])null);
        assertEquals(true, IntrospectionSupport.isGetter(company));
        assertEquals(false, IntrospectionSupport.isSetter(company));

        Method setupSomething = bean.getClass().getMethod("setupSomething", Object.class);
        assertEquals(false, IntrospectionSupport.isGetter(setupSomething));
        assertEquals(false, IntrospectionSupport.isSetter(setupSomething));
    }

    @Test
    public void testOtherIsSetter() throws Exception {
        OtherExampleBean bean = new OtherExampleBean();

        Method customerId = bean.getClass().getMethod("setCustomerId", int.class);
        assertEquals(false, IntrospectionSupport.isGetter(customerId));
        assertEquals(true, IntrospectionSupport.isSetter(customerId));

        Method goldCustomer = bean.getClass().getMethod("setGoldCustomer", boolean.class);
        assertEquals(false, IntrospectionSupport.isGetter(goldCustomer));
        assertEquals(true, IntrospectionSupport.isSetter(goldCustomer));

        Method silverCustomer = bean.getClass().getMethod("setSilverCustomer", Boolean.class);
        assertEquals(false, IntrospectionSupport.isGetter(silverCustomer));
        assertEquals(true, IntrospectionSupport.isSetter(silverCustomer));

        Method company = bean.getClass().getMethod("setCompany", String.class);
        assertEquals(false, IntrospectionSupport.isGetter(company));
        assertEquals(true, IntrospectionSupport.isSetter(company));

        Method setupSomething = bean.getClass().getMethod("setupSomething", Object.class);
        assertEquals(false, IntrospectionSupport.isGetter(setupSomething));
        assertEquals(false, IntrospectionSupport.isSetter(setupSomething));
    }

    @Test
    public void testExtractProperties() throws Exception {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("foo.name", "Camel");
        params.put("foo.age", 5);
        params.put("bar", "yes");

        // extract all "foo." properties
        // and their keys should have the prefix removed
        Map<String, Object> foo = IntrospectionSupport.extractProperties(params, "foo.");
        assertEquals(2, foo.size());
        assertEquals("Camel", foo.get("name"));
        assertEquals(5, foo.get("age"));

        // the extracted properties should be removed from original
        assertEquals(1, params.size());
        assertEquals("yes", params.get("bar"));
    }

    @Test
    public void testFindSetterMethodsOrderedByParameterType() throws Exception {
        List<Method> setters = IntrospectionSupport.findSetterMethodsOrderedByParameterType(MyOverloadedBean.class, "bean", false, false, false);

        assertNotNull(setters);
        assertEquals(2, setters.size());

        assertEquals(ExampleBean.class, setters.get(0).getParameterTypes()[0]);
        assertEquals(String.class, setters.get(1).getParameterTypes()[0]);
    }
}
