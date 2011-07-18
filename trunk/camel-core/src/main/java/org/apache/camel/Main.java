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

import java.util.HashMap;
import java.util.Map;
import javax.xml.bind.JAXBException;

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.MainSupport;
import org.apache.camel.view.ModelFileGenerator;

/**
 * A command line tool for booting up a CamelContext
 *
 * @version 
 */
public class Main extends MainSupport {
    protected static Main instance;

    public Main() {
    }

    public static void main(String... args) throws Exception {
        Main main = new Main();
        instance = main;
        main.enableHangupSupport();
        main.run(args);
    }

    /**
     * Returns the currently executing main
     *
     * @return the current running instance
     */
    public static Main getInstance() {
        return instance;
    }
    
    // Implementation methods
    // -------------------------------------------------------------------------

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        postProcessContext();
        getCamelContexts().get(0).start();
    }

    protected void doStop() throws Exception {
        super.doStop();
        getCamelContexts().get(0).stop();
    }

    protected ProducerTemplate findOrCreateCamelTemplate() {
        return getCamelContexts().get(0).createProducerTemplate();
    }

    protected Map<String, CamelContext> getCamelContextMap() {
        Map<String, CamelContext> answer = new HashMap<String, CamelContext>();
        answer.put("camel-1", new DefaultCamelContext());
        return answer;
    }

    protected ModelFileGenerator createModelFileGenerator() throws JAXBException {
        return null;
    }
}
