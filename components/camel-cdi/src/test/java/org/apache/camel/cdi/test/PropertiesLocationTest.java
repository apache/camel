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

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Named;

import org.apache.camel.CamelContext;
import org.apache.camel.cdi.CdiCamelExtension;
import org.apache.camel.component.properties.PropertiesComponent;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

@RunWith(Arquillian.class)
public class PropertiesLocationTest {

    @Deployment(name = "single-location")
    public static Archive<?> deployment() {
        return ShrinkWrap.create(JavaArchive.class)
            // Camel CDI
            .addPackage(CdiCamelExtension.class.getPackage())
            // Test class
            .addClass(SingleLocation.class)
            // Bean archive deployment descriptor
            .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    // TODO: reactivate when ARQ-1255 is fixed
    /*
    @Deployment(name = "multiple-locations")
    public static Archive<?> multipleLocationsDeployment() {
        return ShrinkWrap.create(JavaArchive.class)
            // Camel CDI
            .addPackage(CdiCamelExtension.class.getPackage())
            // Test classes
            .addClass(MultipleLocations.class)
            // Bean archive deployment descriptor
            .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }
    */

    @Test
    @OperateOnDeployment("single-location")
    public void resolvePropertyFromLocation(CamelContext context) throws Exception {
        assertThat("Property from classpath location does not resolve!", context.resolvePropertyPlaceholders("{{header.message}}"), is(equalTo("message from file")));
    }

    /*
    @Test
    @OperateOnDeployment("multiple-locations")
    public void resolvePropertyFromLocations(CamelContext context) throws Exception {
        assertThat("Property from classpath locations does not resolve!", context.resolvePropertyPlaceholders("{{foo.property}}"), is(equalTo("foo.value")));
        assertThat("Property from classpath locations does not resolve!", context.resolvePropertyPlaceholders("{{bar.property}}"), is(equalTo("bar.value")));
    }
    */
}

class SingleLocation {

    @Produces
    @ApplicationScoped
    @Named("properties")
    private static PropertiesComponent configuration() {
        return new PropertiesComponent("classpath:placeholder.properties");
    }
}

class MultipleLocations {

    @Produces
    @ApplicationScoped
    @Named("properties")
    private static PropertiesComponent configuration() {
        return new PropertiesComponent("classpath:foo.properties", "classpath:bar.properties");
    }
}
