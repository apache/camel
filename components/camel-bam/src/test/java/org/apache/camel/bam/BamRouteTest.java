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
package org.apache.camel.bam;

import org.apache.camel.builder.RouteBuilder;
import static org.apache.camel.builder.xml.XPathBuilder.xpath;
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
    protected MockEndpoint overdueEndpoint;
    protected int errorTimeout = 2;

    public void testBam() throws Exception {
        overdueEndpoint.expectedMessageCount(1);

        template.sendBody("direct:a", "<hello id='123'>world!</hello>");

        overdueEndpoint.assertIsSatisfied();
    }

    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/bam/spring.xml");
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        overdueEndpoint = getMockEndpoint("mock:overdue");
        overdueEndpoint.setDefaulResultWaitMillis(8000);
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        JpaTemplate jpaTemplate = getMandatoryBean(JpaTemplate.class, "jpaTemplate");
        TransactionTemplate transactionTemplate = getMandatoryBean(TransactionTemplate.class, "transactionTemplate");

        // START SNIPPET: example
        return new ProcessBuilder(jpaTemplate, transactionTemplate) {
            public void configure() throws Exception {

                // lets define some activities, correlating on an XPath on the message bodies
                ActivityBuilder a = activity("direct:a").name("a")
                        .correlate(xpath("/hello/@id"));

                ActivityBuilder b = activity("direct:b").name("b")
                        .correlate(xpath("/hello/@id"));

                // now lets add some rules
                b.starts().after(a.completes())
                        .expectWithin(seconds(1))
                        .errorIfOver(seconds(errorTimeout)).to("mock:overdue");
            }
        };
        // END SNIPPET: example
    }

    @Override
    protected int getExpectedRouteCount() {
        return 0;
    }
}
