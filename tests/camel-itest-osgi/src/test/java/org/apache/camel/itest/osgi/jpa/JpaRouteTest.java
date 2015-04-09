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

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.itest.osgi.OSGiIntegrationTestSupport;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.util.IOHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.osgi.framework.BundleContext;


import org.springframework.osgi.context.support.OsgiBundleXmlApplicationContext;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.OptionUtils.combine;

@RunWith(PaxExam.class)
public class JpaRouteTest extends OSGiIntegrationTestSupport {
    protected static final String SELECT_ALL_STRING = "select x from " + SendEmail.class.getName() + " x";

    protected OsgiBundleXmlApplicationContext applicationContext;

    protected TransactionTemplate transactionTemplate;
    protected EntityManager entityManager;
    
    @Inject
    protected BundleContext bundleContext;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        EntityManagerFactory entityManagerFactory = applicationContext.getBean("entityManagerFactory",
                EntityManagerFactory.class);
        transactionTemplate = applicationContext.getBean("transactionTemplate", TransactionTemplate.class);
        entityManager = entityManagerFactory.createEntityManager();
        cleanupRepository();
    }


    @Test
    public void testRouteJpa() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", new SendEmail(1L, "someone@somewhere.org"));

        assertMockEndpointsSatisfied();
        assertEntityInDB(1);
    }
    
    @After
    public void tearDown() {
        try {
            super.tearDown();
            if (applicationContext != null) {                
                if (applicationContext.isActive()) {
                    IOHelper.close(applicationContext);
                }
                applicationContext = null;
            }
        } catch (Exception exception) {
            // Don't throw the exception in the tearDown method            
        }
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        setThreadContextClassLoader();
        applicationContext = new OsgiBundleXmlApplicationContext(
            new String[]{"org/apache/camel/itest/osgi/jpa/springJpaRouteContext.xml"});
        if (bundleContext != null) {
            applicationContext.setBundleContext(bundleContext);
            applicationContext.refresh();
        }
        return SpringCamelContext.springCamelContext(applicationContext);
    }

    private void assertEntityInDB(int size) throws Exception {
        List<?> list = entityManager.createQuery(SELECT_ALL_STRING).getResultList();
        assertEquals(size, list.size());

        assertIsInstanceOf(SendEmail.class, list.get(0));
    }

    private void cleanupRepository() {
        transactionTemplate.execute(new TransactionCallback<Object>() {
            public Object doInTransaction(TransactionStatus arg0) {
                entityManager.joinTransaction();
                List<?> list = entityManager.createQuery(SELECT_ALL_STRING).getResultList();
                for (Object item : list) {
                    entityManager.remove(item);
                }
                entityManager.flush();
                return Boolean.TRUE;
            }
        });
    }
    
    @Configuration
    public static Option[] configure() throws Exception {
        Option[] options = combine(

            getDefaultCamelKarafOptions(),
            // using the features to install the camel components
            loadCamelFeatures("camel-jpa"),

            // use derby as the database
            mavenBundle().groupId("org.apache.openjpa").artifactId("openjpa").version("2.3.0"),
            mavenBundle().groupId("org.apache.derby").artifactId("derby").version("10.4.2.0"));

        return options;
    }
}