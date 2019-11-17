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
package org.apache.camel.component.flatpack;

import java.util.List;
import java.util.Map;

import org.apache.camel.EndpointInject;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.CastUtils;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

import static org.junit.Assert.assertEquals;

/**
 * Unit test to verify that splitRows=false option.
 */
@ContextConfiguration
public class NoSplitRowsTest extends AbstractJUnit4SpringContextTests {
    
    private static final Logger LOG = LoggerFactory.getLogger(NoSplitRowsTest.class);

    @EndpointInject("mock:results")
    protected MockEndpoint results;

    protected String[] expectedFirstName = {"JOHN", "JIMMY", "JANE", "FRED"};

    @Test
    public void testHeaderAndTrailer() throws Exception {
        results.expectedMessageCount(1);
        results.message(0).body().isInstanceOf(List.class);
        results.message(0).header("camelFlatpackCounter").isEqualTo(6);

        results.assertIsSatisfied();

        List<Map<String, String>> data = CastUtils.cast(results.getExchanges().get(0).getIn().getBody(List.class));

        // assert header
        Map<String, String> header = data.get(0);
        assertEquals("HBT", header.get("INDICATOR"));
        assertEquals("20080817", header.get("DATE"));

        // assert body
        int counter = 0;
        for (Map<String, String> row : data.subList(1, 5)) {
            assertEquals("FIRSTNAME", expectedFirstName[counter], row.get("FIRSTNAME"));
            LOG.info("Result: " + counter + " = " + row);
            counter++;
        }

        // assert trailer
        Map<String, String> trailer = data.get(5);
        assertEquals("FBT", trailer.get("INDICATOR"));
        assertEquals("SUCCESS", trailer.get("STATUS"));
    }

}
