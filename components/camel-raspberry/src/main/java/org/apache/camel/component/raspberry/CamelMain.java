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
package org.apache.camel.component.raspberry;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A simple application to run RaspberryPi Camel
 * 
 */
public final class CamelMain {
    
    private static final Logger LOG = LoggerFactory.getLogger(CamelMain.class);
    
    private CamelMain() { }

    public static class CommandLineRouteBuilder extends RouteBuilder {

        String[] args;

        CommandLineRouteBuilder(String[] args) {
            this.args = args;
        }

        @Override
        public void configure() throws Exception {
            from(args[0]).id(RaspberryConstants.CAMEL_ID_ROUTE).to(RaspberryConstants.LOG_COMPONENT).to(args[1]);
        }
    }

   
    public static void main(String[] args) throws Exception {
        LOG.info("main");
        
        for (int i = 0; i < args.length; i++) {
            LOG.info("args[" + i + "] =" + args[i]);
        }

        CamelContext context = new DefaultCamelContext();

        context.addRoutes(new CommandLineRouteBuilder(args));

        context.start();
        Thread.sleep(600000);
        context.stop();
        System.exit(0);
    }

}
