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
package org.apache.camel.bam;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spring.SpringTestSupport;
import static org.apache.camel.util.Time.seconds;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.orm.jpa.JpaTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * @version $Revision: $
 */
public class BamRouteTest extends SpringTestSupport {
    protected Object body = "<hello>world!</hello>";
    protected JpaTemplate jpaTemplate;
    protected MockEndpoint overdueEndpoint;
    protected TransactionTemplate transactionTemplate;

    public void testRoute() throws Exception {
        overdueEndpoint.expectedMessageCount(1);

        template.sendBody("direct:a", body, "foo", "a");

        overdueEndpoint.assertIsSatisfied(5000);
    }

    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/bam/spring.xml");
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        camelContext.addRoutes(createRouteBuilder());

        overdueEndpoint = (MockEndpoint) resolveMandatoryEndpoint("mock:overdue");
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        jpaTemplate = getMandatoryBean(JpaTemplate.class, "jpaTemplate");
        transactionTemplate = getMandatoryBean(TransactionTemplate.class, "transactionTemplate");

        return new ProcessBuilder(jpaTemplate, transactionTemplate) {
            public void configure() throws Exception {

                ActivityBuilder a = activity("direct:a").name("a")
                        .correlate(header("foo"));

                ActivityBuilder b = activity("direct:b").name("b")
                        .correlate(header("foo"));

                ActivityBuilder c = activity("direct:c").name("c")
                        .correlate(header("foo"));

                b.starts().after(a.completes())
                        .expectWithin(seconds(1))
                        .errorIfOver(seconds(2)).to("mock:overdue");
            }
        };
    }

    @Override
    protected int getExpectedRouteCount() {
        return 0;
    }
}
