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
package org.apache.camel.itest.osgi.mybatis;

import java.sql.Connection;
import java.sql.Statement;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mybatis.MyBatisComponent;
import org.apache.camel.component.mybatis.MyBatisEndpoint;
import org.apache.camel.itest.osgi.OSGiIntegrationTestSupport;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;

import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.OptionUtils.combine;

/**
 * @version 
 */
@RunWith(PaxExam.class)
@Ignore("Loading OSGi driver in OSGi is ***** hard")
public class MyBatisTest extends OSGiIntegrationTestSupport {

    @Test
    public void testMina() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        Account account = new Account();
        account.setId(444);
        account.setFirstName("Willem");
        account.setLastName("Jiang");
        account.setEmailAddress("Faraway@gmail.com");

        template.sendBody("mybatis:insertAccount?statementType=Insert", account);

        assertMockEndpointsSatisfied();

        dropTable();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.disableJMX();
        return context;
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() throws Exception {
                MyBatisComponent myBatis = context.getComponent("mybatis", MyBatisComponent.class);
                myBatis.setConfigurationUri("org/apache/camel/itest/osgi/mybatis/SqlMapConfig.xml");

                // create table before we start using mybatis
                createTable();

                from("mybatis:selectAllAccounts").to("mock:result");
            }
        };
    }

    private void createTable() throws Exception {
        // lets create the database...
        Connection connection = createConnection();
        Statement statement = connection.createStatement();
        statement.execute("create table ACCOUNT ( ACC_ID INTEGER , ACC_FIRST_NAME VARCHAR(255), ACC_LAST_NAME VARCHAR(255), ACC_EMAIL VARCHAR(255)  )");
        connection.close();
    }

    private void dropTable() throws Exception {
        Connection connection = createConnection();
        Statement statement = connection.createStatement();
        statement.execute("drop table ACCOUNT");
        connection.close();
    }

    private Connection createConnection() throws Exception {
        MyBatisEndpoint endpoint = resolveMandatoryEndpoint("mybatis:Account", MyBatisEndpoint.class);
        return endpoint.getSqlSessionFactory().getConfiguration().getEnvironment().getDataSource().getConnection();
    }

    @Configuration
    public static Option[] configure() throws Exception {
        Option[] options = combine(

            getDefaultCamelKarafOptions(),
            // using the features to install the camel components
            loadCamelFeatures("jetty", "camel-mybatis"),

            // use derby as the database
            mavenBundle().groupId("org.apache.derby").artifactId("derby").version("10.4.2.0"));

        return options;
    }

}
