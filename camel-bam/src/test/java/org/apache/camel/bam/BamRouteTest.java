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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.CamelContext;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.spring.SpringTestSupport;
import org.apache.camel.builder.RouteBuilder;
import static org.apache.camel.builder.xml.XPathBuilder.xpath;
import static org.apache.camel.util.Time.seconds;
import org.springframework.context.ApplicationContext;
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

                /*
        expect(b.starts().after(10).minutes().from(a.starts());




        process.activity("direct:a").name("a")
                .correlate(header("foo"))
                .expect(seconds(10)).afterProcess().starts();
                .expectedAfter(10).minutes();
                .errorAfter(30).minutes();


        process.activity("direct:b").name("b")
                .correlate(header("foo"))
                .expect(minutes(10)).after("a").completes();


        BamBuilder bam = BamBuilder.monitor(this, "direct:a", "direct:b", "direct:c");

        bam.process("direct:b",).expectedMesageCount(1)
                .expectedAfter().minutes(10)
                .errorAfter().minutes(30);

        bam.expects("direct:c").expectedMesageCount(1)
                .expectedAfter().minutes(10)
                .errorAfter().minutes(30);

                */
            }
        };
    }

    @Override
    protected int getExpectedRouteCount() {
        return 0;
    }
}
