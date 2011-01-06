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
package org.apache.camel.component.jdbc.aggregationrepository;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class JdbcGrowIssueTest extends CamelTestSupport {

    private static final int SIZE = 1024;
    private JdbcAggregationRepository repo;

    @Before
    @Override
    public void setUp() throws Exception {
        ApplicationContext applicationContext = new ClassPathXmlApplicationContext("org/apache/camel/component/jdbc/aggregationrepository/JdbcSpringDataSource.xml");
        repo = applicationContext.getBean("repo1", JdbcAggregationRepository.class);

        super.setUp();
    }

    @Test
    public void testGrowIssue() throws Exception {
        // a 1kb string for testing
        StringBuilder sb = new StringBuilder(SIZE);
        for (int i = 0; i < SIZE; i++) {
            sb.append("X");
        }
        Exchange item = new DefaultExchange(context);
        item.getIn().setBody(sb.toString(), String.class);

        // the key
        final String key = "foo";

        // we update using the same key, which means we should be able to do this within the file size limit
        for (int i = 0; i < SIZE; i++) {
            log.debug("Updating " + i);
            repo.add(context, key, item);
        }

        // get the last
        Exchange data = repo.get(context, key);
        log.info(data);

        assertTrue("Should start with 'XXX'", data.getIn().getBody(String.class).startsWith("XXX"));
        int length = data.getIn().getBody(String.class).length();
        assertEquals("Length should be 1024, was " + length, 1024, length);
    }

}
