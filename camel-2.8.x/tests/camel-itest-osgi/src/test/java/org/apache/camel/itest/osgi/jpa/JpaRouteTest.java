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
package org.apache.camel.itest.osgi.jpa;

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.itest.osgi.OSGiIntegrationTestSupport;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.karaf.testing.Helper;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Inject;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.Configuration;
import org.ops4j.pax.exam.junit.JUnit4TestRunner;
import org.osgi.framework.BundleContext;
import org.springframework.orm.jpa.JpaTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.osgi.context.support.OsgiBundleXmlApplicationContext;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.ops4j.pax.exam.CoreOptions.equinox;
import static org.ops4j.pax.exam.CoreOptions.felix;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.OptionUtils.combine;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.scanFeatures;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.workingDirectory;

@RunWith(JUnit4TestRunner.class)
@Ignore("TODO: fix me")
public class JpaRouteTest extends OSGiIntegrationTestSupport {
    protected static final String SELECT_ALL_STRING = "select x from " + SendEmail.class.getName() + " x";

    protected OsgiBundleXmlApplicationContext applicationContext;
    
    @Inject
    protected BundleContext bundleContext;
    protected JpaTemplate jpaTemplate;

    @Test
    public void testRouteJpa() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", new SendEmail("someone@somewhere.org"));

        assertMockEndpointsSatisfied();
        assertEntityInDB();
    }
    
    @After
    public void tearDown() {
        try {
            super.tearDown();
            if (applicationContext != null) {                
                if (applicationContext.isActive()) {
                    applicationContext.destroy();
                }
                applicationContext = null;
            }
        } catch (Exception exception) {
            // Don't throw the exception in the tearDown method            
        }
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        applicationContext = new OsgiBundleXmlApplicationContext(
            new String[]{"org/apache/camel/itest/osgi/jpa/springJpaRouteContext.xml"});
        if (bundleContext != null) {
            applicationContext.setBundleContext(bundleContext);
            applicationContext.refresh();
        }
        cleanupRepository();
        return SpringCamelContext.springCamelContext(applicationContext);
    }

    private void assertEntityInDB() throws Exception {
        // must type cast with Spring 2.x
        jpaTemplate = (JpaTemplate) applicationContext.getBean("jpaTemplate");

        List list = jpaTemplate.find(SELECT_ALL_STRING);
        assertEquals(1, list.size());
        
        assertIsInstanceOf(SendEmail.class, list.get(0));
    }

    protected void cleanupRepository() {
        // must type cast with Spring 2.x
        jpaTemplate = (JpaTemplate) applicationContext.getBean("jpaTemplate");

        TransactionTemplate transactionTemplate = new TransactionTemplate();
        transactionTemplate.setTransactionManager(new JpaTransactionManager(jpaTemplate.getEntityManagerFactory()));
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);

        transactionTemplate.execute(new TransactionCallback() {
            public Object doInTransaction(TransactionStatus arg0) {
                List list = jpaTemplate.find(SELECT_ALL_STRING);
                for (Object item : list) {
                    jpaTemplate.remove(item);
                }
                jpaTemplate.flush();
                return Boolean.TRUE;
            }
        });
    }
    
    @Configuration
    public static Option[] configure() throws Exception {
        Option[] options = combine(
            // Default karaf environment
            Helper.getDefaultOptions(
            // this is how you set the default log level when using pax logging (logProfile)
                Helper.setLogLevel("WARN")),
                
            // install the spring, http features first
            scanFeatures(getKarafFeatureUrl(), "spring", "spring-dm", "jetty"),
            // using the features to install the camel components             
            scanFeatures(getCamelKarafFeatureUrl(),                         
                          "camel-core", "camel-spring", "camel-test", "camel-jpa"),
            
            mavenBundle().groupId("org.apache.derby").artifactId("derby").version("10.4.2.0"), 

            workingDirectory("target/paxrunner/"),

            felix(), equinox());
        
        return options;
    }
}