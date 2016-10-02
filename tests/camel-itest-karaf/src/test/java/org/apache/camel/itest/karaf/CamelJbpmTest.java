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
package org.apache.camel.itest.karaf;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.CoreOptions;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;

@RunWith(PaxExam.class)
public class CamelJbpmTest extends BaseKarafTest {

    public static final String COMPONENT = extractName(CamelJbpmTest.class);
    
    @Configuration
    public static Option[] configure() {
        Option[] baseOptions = BaseKarafTest.configure();
        Option[] additionalOptions = CoreOptions.options(
            CoreOptions.systemPackage("com.sun.tools.xjc")
        );

        Option[] options = new Option[baseOptions.length + additionalOptions.length];
        System.arraycopy(baseOptions, 0, options, 0, baseOptions.length);
        System.arraycopy(additionalOptions, 0, options, baseOptions.length, additionalOptions.length);

        return options;    
    }

    @Test
    public void test() throws Exception {
        testComponent(COMPONENT);
    }

}