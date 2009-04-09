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
package org.apache.camel.spring;

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.Routes;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class RouteRefCamelContextFactoryBeanTest extends RoutingUsingCamelContextFactoryTest {
    
    public static class MyRoutes implements Routes {
        private RouteBuilder myRouteBuilder;
        
        public MyRoutes() {
            myRouteBuilder = new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("seda:start").to("mock:result");   
                }
            };
        }

        public CamelContext getContext() {            
            return myRouteBuilder.getContext();
        }

        public List<Route> getRouteList() throws Exception {
            return myRouteBuilder.getRouteList();
        }

        public void setContext(CamelContext context) {
            myRouteBuilder.setContext(context);
        }
        
    }
    
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/routeRefCamelContextFactory.xml");
    }

}
