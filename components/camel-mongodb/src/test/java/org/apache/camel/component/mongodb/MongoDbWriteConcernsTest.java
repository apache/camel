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
package org.apache.camel.component.mongodb;

import com.mongodb.WriteResult;

import org.apache.camel.builder.RouteBuilder;

import org.junit.Ignore;
import org.junit.Test;

public class MongoDbWriteConcernsTest extends AbstractMongoDbTest {
    
    @Test
    @Ignore
    public void testDynamicWriteConcernSafe() throws Exception {
        assertEquals(0, testCollection.count());
        
        // test with object first
        Object result = template.requestBody("direct:noWriteConcern", "{\"scientist\":\"newton\"}");
        assertTrue("Result is not of type WriteResult", result instanceof WriteResult);
        WriteResult wr = (WriteResult) result;
        assertTrue(wr.wasAcknowledged());
        
        // same behaviour should be reproduced with String 'SAFE'
        result = template.requestBody("direct:noWriteConcern", "{\"scientist\":\"newton\"}");
        assertTrue("Result is not of type WriteResult", result instanceof WriteResult);
        wr = (WriteResult) result;
        assertTrue(wr.wasAcknowledged());
    }
    
    @Test
    @Ignore
    public void testDynamicWriteConcernUnknown() throws Exception {
        assertEquals(0, testCollection.count());
        
        try {
            template.requestBodyAndHeader("direct:noWriteConcern", "{\"scientist\":\"newton\"}", MongoDbConstants.WRITECONCERN, "Random");
            fail("Should have thrown an exception");
        } catch (Exception e) {
            extractAndAssertCamelMongoDbException(e, "WriteConcern specified in the " + MongoDbConstants.WRITECONCERN + " header");
        }
    }
        
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                
                from("direct:noWriteConcern").to("mongodb:myDb?database=test&collection=camelTest&operation=insert");
                from("direct:writeConcernParam").to("mongodb:myDb?database=test&collection=camelTest&operation=insert&writeConcern=SAFE");
                //from("direct:writeConcernRef").to("mongodb:myDb?database=test&collection=camelTest&operation=insert&writeConcernRef=customWriteConcern");
                from("direct:noWriteConcernWithCallGetLastError").to("mongodb:myDb?database=test&collection=camelTest&operation=insert");


            }
        };
    }
}
