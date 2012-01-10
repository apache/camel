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
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.profile;
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
    public static Option[] configure() {
        Option[] options = options(
            // install the spring dm profile            
            profile("spring.dm").version("1.2.0"),    
            // this is how you set the default log level when using pax logging (logProfile)
            org.ops4j.pax.exam.CoreOptions.systemProperty("org.ops4j.pax.logging.DefaultServiceLog.level").value("INFO"),
            //org.ops4j.pax.exam.CoreOptions.systemProperty("org.apache.servicemix.specs.debug").value("true"),
            //mavenBundle().groupId("net.sourceforge.serp").artifactId("com.springsource.serp").version("1.13.1"),
            // using the features to install the camel components             
            scanFeatures(getCamelKarafFeatureUrl(),                         
                          "camel-core", "camel-spring", "camel-test", "camel-jpa"),
           
            /* This the camel-jpa needed bundles 
            mavenBundle().groupId("org.apache.servicemix.specs").artifactId("org.apache.servicemix.specs.java-persistence-api-1.1.1").version("1.4-SNAPSHOT"),
            mavenBundle().groupId("org.apache.servicemix.bundles").artifactId("org.apache.servicemix.bundles.openjpa").version("1.2.1_1-SNAPSHOT"),
            mavenBundle().groupId("org.apache.geronimo.specs").artifactId("geronimo-jta_1.1_spec").version("1.1.1"),
            mavenBundle().groupId("org.apache.camel").artifactId("camel-jpa").version("2.1-SNAPSHOT"),
            mavenBundle().groupId("org.springframework").artifactId("spring-jdbc").version("2.5.6"),
            mavenBundle().groupId("org.springframework").artifactId("spring-tx").version("2.5.6"),
            mavenBundle().groupId("org.springframework").artifactId("spring-orm").version("2.5.6"),
            mavenBundle().groupId("commons-lang").artifactId("commons-lang").version("2.4"),    
            mavenBundle().groupId("commons-collections").artifactId("commons-collections").version("3.2.1"),    
            mavenBundle().groupId("org.apache.servicemix.bundles").artifactId("org.apache.servicemix.bundles.ant").version("1.7.0_1"),    
            mavenBundle().groupId("commons-pool").artifactId("commons-pool").version("1.4"),
            mavenBundle().groupId("org.apache.geronimo.specs").artifactId("geronimo-jms_1.1_spec").version("1.1.1"),*/
            mavenBundle().groupId("org.apache.derby").artifactId("derby").version("10.4.2.0"), 

            workingDirectory("target/paxrunner/"),

            felix(), equinox());
        
        return options;
    }
}