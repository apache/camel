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
package org.apache.camel.cdi.test;

import java.util.Properties;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.inject.Named;

import org.apache.camel.cdi.CdiCamelExtension;
import org.apache.camel.cdi.bean.BeanInjectBean;
import org.apache.camel.cdi.bean.NamedCamelBean;
import org.apache.camel.cdi.bean.PropertyInjectBean;
import org.apache.camel.component.properties.PropertiesComponent;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

@RunWith(Arquillian.class)
public class BeanInjectTest {

    @Inject
    private BeanInjectBean bean;

    @Produces
    @ApplicationScoped
    @Named("properties")
    private static PropertiesComponent configuration() {
        Properties properties = new Properties();
        properties.put("property", "value");
        PropertiesComponent component = new PropertiesComponent();
        component.setInitialProperties(properties);
        return component;
    }

    @Deployment
    public static Archive<?> deployment() {
        return ShrinkWrap.create(JavaArchive.class)
            // Camel CDI
            .addPackage(CdiCamelExtension.class.getPackage())
            // Test classes
            .addClasses(BeanInjectBean.class, PropertyInjectBean.class, NamedCamelBean.class)
            // Bean archive deployment descriptor
            .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    @Test
    public void beanInjectField() {
        assertThat(bean.getInjectBeanField(), is(notNullValue()));
        assertThat(bean.getInjectBeanField().getProperty(), is(equalTo("value")));
    }

    @Test
    public void beanInjectMethod() {
        assertThat(bean.getInjectBeanMethod(), is(notNullValue()));
        assertThat(bean.getInjectBeanMethod().getProperty(), is(equalTo("value")));
    }

    @Test
    public void beanInjectNamed() {
        assertThat(bean.getInjectBeanNamed(), is(notNullValue()));
    }
}
