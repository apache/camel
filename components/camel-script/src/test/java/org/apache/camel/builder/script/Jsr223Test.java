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
package org.apache.camel.builder.script;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import junit.framework.TestCase;

/**
 * @version $Revision$
 */
public class Jsr223Test extends TestCase {
    private String [] scriptNames = {"beanshell", "groovy", "js", "python", "ruby", "javascript"};

    public void testLanguageNames() throws Exception {
        // ruby scripting does not work on IBM's JDK
        // see http://jira.codehaus.org/browse/JRUBY-3073
        ArrayList<String> scriptNamesAsList = new ArrayList<String>(Arrays.asList(scriptNames));       
        if ("IBM Corporation".equals(System.getProperty("java.vendor"))) {
            scriptNamesAsList.remove("ruby");
        }
        
        ScriptEngineManager manager = new ScriptEngineManager();
        for (String scriptName : scriptNamesAsList) {
            ScriptEngine engine = manager.getEngineByName(scriptName);
            assertNotNull("We should get the script engine for " + scriptName , engine);
        }
    }
}
