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
package org.apache.camel.component.flatpack;

import net.sf.flatpack.DataSet;
import org.apache.camel.EndpointInject;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;


/**
 * @version 
 */
@ContextConfiguration
public class DelimitedErrorWithUnmarshalTest extends AbstractJUnit4SpringContextTests {

    @EndpointInject(uri = "mock:results")
    protected MockEndpoint results;

    @EndpointInject(uri = "mock:dataset")
    protected MockEndpoint dataset;

    @Test
    public void testCamel() throws Exception {
        results.expectedMessageCount(3);
        dataset.setExpectedMessageCount(1);
        results.assertIsSatisfied();
        dataset.assertIsSatisfied();

        DataSet ds = dataset.getExchanges().get(0).getIn().getBody(DataSet.class);
        assertNotNull(ds);
        assertEquals(2, ds.getErrorCount());
        assertEquals(3, ds.getRowCount());
    }
}
