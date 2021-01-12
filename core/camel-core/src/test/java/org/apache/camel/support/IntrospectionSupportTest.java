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
import org.junit.jupiter.api.Test;

import static org.apache.camel.support.IntrospectionSupport.extractProperties;
import static org.apache.camel.support.IntrospectionSupport.findSetterMethodsOrderedByParameterType;
import static org.apache.camel.support.IntrospectionSupport.getProperties;
import static org.apache.camel.support.IntrospectionSupport.getProperty;
import static org.apache.camel.support.IntrospectionSupport.getPropertyGetter;
import static org.apache.camel.support.IntrospectionSupport.getPropertySetter;
import static org.apache.camel.support.IntrospectionSupport.hasProperties;
import static org.apache.camel.support.IntrospectionSupport.isGetter;
import static org.apache.camel.support.IntrospectionSupport.isSetter;
import static org.apache.camel.support.IntrospectionSupport.setProperty;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for IntrospectionSupport
 *
 * The deprecation warnings are a false positive here because this class test IntrospectionSupport (which is, indeed,
 * deprecated)
 */

public class IntrospectionSupportTest extends ContextTestSupport {

    @Test
    public void testOverloadSetterChooseStringSetter() throws Exception {
        MyOverloadedBean overloadedBean = new MyOverloadedBean();
        setProperty(context.getTypeConverter(), overloadedBean, "bean", "James");
        assertEquals("James", overloadedBean.getName());
    }

    @Test
    public void testOverloadSetterChooseBeanSetter() throws Exception {
        MyOverloadedBean overloadedBean = new MyOverloadedBean();
        ExampleBean bean = new ExampleBean();
        bean.setName("Claus");
        setProperty(context.getTypeConverter(), overloadedBean, "bean", bean);
        assertEquals("Claus", overloadedBean.getName());
    }

    @Test
    public void testOverloadSetterChooseUsingTypeConverter() throws Exception {
        MyOverloadedBean overloadedBean = new MyOverloadedBean();
        Object value = "Willem".getBytes();
        // should use byte[] -> String type converter and call the
        // setBean(String) setter method
        setProperty(context.getTypeConverter(), overloadedBean, "bean", value);
        assertEquals("Willem", overloadedBean.getName());
    }

    @Test
    public void testPassword() throws Exception {
        MyPasswordBean passwordBean = new MyPasswordBean();
        setProperty(context.getTypeConverter(), passwordBean, "oldPassword", "Donald");
        setProperty(context.getTypeConverter(), passwordBean, "newPassword", "Duck");
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
        setProperty(context.getTypeConverter(), builderBean, "name", "Donald");
        setProperty(context.getTypeConverter(), builderBean, "age", "33");
        setProperty(context.getTypeConverter(), builderBean, "gold-customer", "true");
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
        setProperty(context.getTypeConverter(), builderBean, "name", "Goofy");
        setProperty(context.getTypeConverter(), builderBean, "age", "34");
        setProperty(context.getTypeConverter(), builderBean, "gold-customer", "true");
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

        assertFalse(isSetter(setter, false));
        assertTrue(isSetter(setter, true));

        assertFalse(isSetter(setter2, false));
        assertTrue(isSetter(setter2, true));

        assertFalse(isSetter(setter3, false));
        assertTrue(isSetter(setter3, true));
    }

    @Test
    public void testHasProperties() throws Exception {
        Map<String, Object> empty = Collections.emptyMap();
        assertFalse(hasProperties(empty, null));
        assertFalse(hasProperties(empty, ""));
        assertFalse(hasProperties(empty, "foo."));

        Map<String, Object> param = new HashMap<>();
        assertFalse(hasProperties(param, null));
        assertFalse(hasProperties(param, ""));
        assertFalse(hasProperties(param, "foo."));

        param.put("name", "Claus");
        assertTrue(hasProperties(param, null));
        assertTrue(hasProperties(param, ""));
        assertFalse(hasProperties(param, "foo."));

        param.put("foo.name", "Hadrian");
        assertTrue(hasProperties(param, null));
        assertTrue(hasProperties(param, ""));
        assertTrue(hasProperties(param, "foo."));
    }

    @Test
    public void testGetProperties() throws Exception {
        ExampleBean bean = new ExampleBean();
        bean.setName("Claus");
        bean.setPrice(10.0);

        Map<String, Object> map = new HashMap<>();
        getProperties(bean, map, null);
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
        getProperties(bean, map, null);
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
        getProperties(bean, map, "bean.");
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
        getProperties(bean, map, null, false);
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

        Object name = getProperty(bean, "name");
        assertEquals("Claus", name);
    }

    @Test
    public void testSetProperty() throws Exception {
        ExampleBean bean = new ExampleBean();
        bean.setId("123");
        bean.setName("Claus");
        bean.setPrice(10.0);

        setProperty(context, bean, "name", "James");
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

        setProperty(context, bean, "name", "James");
        setProperty(context, bean, "gold-customer", "false");
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

        Object name = getProperty(bean, "name");
        assertEquals("Claus", name);
        assertSame(date, getProperty(bean, "date"));
        assertSame(children, getProperty(bean, "children"));
        assertEquals(Boolean.TRUE, getProperty(bean, "goldCustomer"));
        assertEquals(Boolean.TRUE, getProperty(bean, "gold-customer"));
        assertEquals(Boolean.TRUE, getProperty(bean, "little"));
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

            Object name = getProperty(bean, "name");
            Object id = getProperty(bean, "id");
            Object price = getProperty(bean, "price");

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

        Method name = getPropertyGetter(ExampleBean.class, "name");
        assertEquals("getName", name.getName());

        try {
            getPropertyGetter(ExampleBean.class, "xxx");
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

        Method name = getPropertySetter(ExampleBean.class, "name");
        assertEquals("setName", name.getName());

        try {
            getPropertySetter(ExampleBean.class, "xxx");
            fail("Should have thrown exception");
        } catch (NoSuchMethodException e) {
            assertEquals("org.apache.camel.support.jndi.ExampleBean.setXxx", e.getMessage());
        }
    }

    @Test
    public void testIsGetter() throws Exception {
        ExampleBean bean = new ExampleBean();

        Method name = bean.getClass().getMethod("getName", (Class<?>[]) null);
        assertEquals(true, isGetter(name));
        assertEquals(false, isSetter(name));

        Method price = bean.getClass().getMethod("getPrice", (Class<?>[]) null);
        assertEquals(true, isGetter(price));
        assertEquals(false, isSetter(price));
    }

    @Test
    public void testIsSetter() throws Exception {
        ExampleBean bean = new ExampleBean();

        Method name = bean.getClass().getMethod("setName", String.class);
        assertEquals(false, isGetter(name));
        assertEquals(true, isSetter(name));

        Method price = bean.getClass().getMethod("setPrice", double.class);
        assertEquals(false, isGetter(price));
        assertEquals(true, isSetter(price));
    }

    @Test
    public void testOtherIsGetter() throws Exception {
        OtherExampleBean bean = new OtherExampleBean();

        Method customerId = bean.getClass().getMethod("getCustomerId", (Class<?>[]) null);
        assertEquals(true, isGetter(customerId));
        assertEquals(false, isSetter(customerId));

        Method goldCustomer = bean.getClass().getMethod("isGoldCustomer", (Class<?>[]) null);
        assertEquals(true, isGetter(goldCustomer));
        assertEquals(false, isSetter(goldCustomer));

        Method silverCustomer = bean.getClass().getMethod("isSilverCustomer", (Class<?>[]) null);
        assertEquals(true, isGetter(silverCustomer));
        assertEquals(false, isSetter(silverCustomer));

        Method company = bean.getClass().getMethod("getCompany", (Class<?>[]) null);
        assertEquals(true, isGetter(company));
        assertEquals(false, isSetter(company));

        Method setupSomething = bean.getClass().getMethod("setupSomething", Object.class);
        assertEquals(false, isGetter(setupSomething));
        assertEquals(false, isSetter(setupSomething));
    }

    @Test
    public void testOtherIsSetter() throws Exception {
        OtherExampleBean bean = new OtherExampleBean();

        Method customerId = bean.getClass().getMethod("setCustomerId", int.class);
        assertEquals(false, isGetter(customerId));
        assertEquals(true, isSetter(customerId));

        Method goldCustomer = bean.getClass().getMethod("setGoldCustomer", boolean.class);
        assertEquals(false, isGetter(goldCustomer));
        assertEquals(true, isSetter(goldCustomer));

        Method silverCustomer = bean.getClass().getMethod("setSilverCustomer", Boolean.class);
        assertEquals(false, isGetter(silverCustomer));
        assertEquals(true, isSetter(silverCustomer));

        Method company = bean.getClass().getMethod("setCompany", String.class);
        assertEquals(false, isGetter(company));
        assertEquals(true, isSetter(company));

        Method setupSomething = bean.getClass().getMethod("setupSomething", Object.class);
        assertEquals(false, isGetter(setupSomething));
        assertEquals(false, isSetter(setupSomething));
    }

    @Test
    public void testExtractProperties() throws Exception {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("foo.name", "Camel");
        params.put("foo.age", 5);
        params.put("bar", "yes");

        // extract all "foo." properties
        // and their keys should have the prefix removed
        Map<String, Object> foo = extractProperties(params, "foo.");
        assertEquals(2, foo.size());
        assertEquals("Camel", foo.get("name"));
        assertEquals(5, foo.get("age"));

        // the extracted properties should be removed from original
        assertEquals(1, params.size());
        assertEquals("yes", params.get("bar"));
    }

    @Test
    public void testFindSetterMethodsOrderedByParameterType() throws Exception {
        List<Method> setters = findSetterMethodsOrderedByParameterType(MyOverloadedBean.class, "bean",
                false, false, false);

        assertNotNull(setters);
        assertEquals(2, setters.size());

        assertEquals(ExampleBean.class, setters.get(0).getParameterTypes()[0]);
        assertEquals(String.class, setters.get(1).getParameterTypes()[0]);
    }

    @Test
    public void testArray() throws Exception {
        MyBeanWithArray target = new MyBeanWithArray();
        setProperty(context.getTypeConverter(), target, "names[0]", "James");
        setProperty(context.getTypeConverter(), target, "names[1]", "Claus");
        assertEquals("James", target.getNames()[0]);
        assertEquals("Claus", target.getNames()[1]);

        setProperty(context.getTypeConverter(), target, "names[0]", "JamesX");
        assertEquals("JamesX", target.getNames()[0]);

        setProperty(context.getTypeConverter(), target, "names[2]", "Andrea");
        assertEquals("JamesX", target.getNames()[0]);
        assertEquals("Claus", target.getNames()[1]);
        assertEquals("Andrea", target.getNames()[2]);
    }

    public class MyBeanWithArray {
        private String[] names = new String[10];

        public String[] getNames() {
            return names;
        }

        public void setNames(String[] names) {
            this.names = names;
        }
    }
}
