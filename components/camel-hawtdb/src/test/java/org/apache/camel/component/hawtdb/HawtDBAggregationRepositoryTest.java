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
package org.apache.camel.component.hawtdb;

import java.io.File;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangeTestSupport;
import org.apache.camel.impl.DefaultExchange;

/**
 * Tests the HawtDBAggregationRepository implementation.
 * 
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public class HawtDBAggregationRepositoryTest extends ExchangeTestSupport {
    
    private HawtDBFile hawtDBFile;

    @Override
    protected void setUp() throws Exception {
        File file = new File("target/test-data/"+getClass().getName()+"-"+getName());
        hawtDBFile = new HawtDBFile();
        hawtDBFile.setFile(file);
        hawtDBFile.start();
    }
    
    @Override
    protected void tearDown() throws Exception {
        hawtDBFile.stop();
    }
    
    public void testOperations() {
        
        HawtDBAggregationRepository<String> repo = new HawtDBAggregationRepository<String>();
        repo.setFile(hawtDBFile);
        repo.setName("repo1");
        
        // Can't get something we have not put in...
        Exchange actual = repo.get("missing");
        assertEquals(null, actual);

        // Store it..
        Exchange exchange1 = new DefaultExchange(context);
        exchange1.getIn().setBody("counter:1");
        actual = repo.add("foo", exchange1);
        assertEquals(null, actual);
        
        // Get it back..
        actual = repo.get("foo");
        assertEquals(exchange1, actual);
              
        // Change it..
        Exchange exchange2 = new DefaultExchange(context);
        exchange2.getIn().setBody("counter:2");
        actual = repo.add("foo", exchange2);
        assertEquals(exchange1, actual);
        
        // Get it back..
        actual = repo.get("foo");
        assertEquals(exchange2, actual);
    }

}
