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
package org.apache.camel;

import org.apache.camel.impl.DefaultExchange;

public class ExchangePropertyTest extends ExchangeTestSupport {
    protected static final String P1_NAME = "org.apache.myproject.mypackage.myproperty1";
    protected static final String P2_NAME = "org.apache.myproject.mypackage.myproperty2";
    
    protected Exchange exchange;

    public void testExchangePropertyRegistry() throws Exception {
        ExchangeProperty<Boolean> myProperty1 = 
            new ExchangeProperty<Boolean>("myProperty1", P1_NAME, Boolean.class);
        
        assertEquals(ExchangeProperty.get("myProperty1"), myProperty1);
        assertEquals(ExchangeProperty.values().length, 1); 
        assertEquals(ExchangeProperty.values()[0], myProperty1);

        ExchangeProperty<Boolean> myProperty2 = 
            new ExchangeProperty<Boolean>("myProperty2", P2_NAME, Boolean.class);
        
        assertEquals(ExchangeProperty.get("myProperty2"), myProperty2);
        assertEquals(ExchangeProperty.values().length, 2); 
        assertEquals(ExchangeProperty.values()[1], myProperty2);

        try {
            ExchangeProperty<Boolean> rejectedProperty = 
                new ExchangeProperty<Boolean>("myProperty2", P2_NAME, Boolean.class);
            fail("Expected RuntimeCamelException to be thrown due to duplicate property "
                 + " registration attempt");
        } catch (RuntimeCamelException e) {
            assertEquals(ExchangeProperty.values().length, 2);
        } catch (Throwable t) {
            fail("Expected RuntimeCamelException to be thrown due to duplicate propery "
                    + " registration attempt");
        }
        ExchangeProperty.deregister(myProperty1);
        assertEquals(ExchangeProperty.get("myProperty1"), null);
        ExchangeProperty.deregister("myProperty2");
        assertEquals(ExchangeProperty.get("myProperty2"), null);
        assertEquals(ExchangeProperty.values().length, 0);
    }
    
    public void testExchangePropertySetterGetter() throws Exception {
        Exchange exchange = createExchange();

        ExchangeProperty<Boolean> myProperty1 = 
            new ExchangeProperty<Boolean>("myProperty1", P1_NAME, Boolean.class);

        ExchangeProperty<String> myProperty2 = 
            new ExchangeProperty<String>("myProperty2", P2_NAME, String.class);
        
        myProperty1.set(exchange, Boolean.TRUE);
        assertTrue("Unexpected property value", 
                    myProperty1.get(exchange) == Boolean.TRUE);
        assertTrue("Unexpected property value", 
                    ExchangeProperty.get("myProperty1").get(exchange) == Boolean.TRUE);
        
        myProperty2.set(exchange, "camel");
        assertTrue("Unexpected property value", 
                    myProperty2.get(exchange).equals("camel"));
        assertTrue("Unexpected property value", 
                    ExchangeProperty.get("myProperty2").get(exchange).equals("camel"));
        
        ExchangeProperty.deregister(myProperty1);
        assertEquals(ExchangeProperty.get("myProperty1"), null);
        ExchangeProperty.deregister("myProperty2");
        assertEquals(ExchangeProperty.get("myProperty2"), null);
        assertEquals(ExchangeProperty.values().length, 0);
    }

    public void testExchangePropertyTypeSafety() throws Exception {
        Exchange exchange = createExchange();
        ExchangeProperty<Boolean> myProperty1 = 
            new ExchangeProperty<Boolean>("myProperty1", P1_NAME, Boolean.class);
        try {
            exchange.setProperty(P1_NAME, "camel");
            fail("Expected RuntimeCamelException to be thrown due to property value type cast violation");
        } catch (RuntimeCamelException e) {
            // complete
        } catch (Throwable t) {
            fail("Expected RuntimeCamelException to be thrown due to property value type cast violation");
        }
        
        myProperty1.set(exchange, Boolean.TRUE);
        
        assertTrue("Unexpected property value", 
                myProperty1.get(exchange) == Boolean.TRUE);
        assertTrue("Unexpected property value", 
                ExchangeProperty.get("myProperty1").get(exchange) == Boolean.TRUE);

        ExchangeProperty.deregister(myProperty1);
        assertEquals(ExchangeProperty.get("myProperty1"), null);
        assertEquals(ExchangeProperty.values().length, 0);
    }
}
