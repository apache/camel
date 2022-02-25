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
package org.apache.camel.test.main.junit5.common;

import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelConfiguration;
import org.apache.camel.CamelContext;
import org.apache.camel.PropertyInject;

/**
 * Class to configure the Camel application.
 */
public class MyConfiguration implements CamelConfiguration {

    @BindToRegistry
    public Greetings myGreetings(@PropertyInject("name") String name) {
        // this will create an instance of this bean and bind it using the name of the method as name (eg myGreetings)
        return new Greetings(name);
    }

    @Override
    public void configure(CamelContext camelContext) {
        // this method is optional and can be removed if no additional configuration is needed.
    }

}
