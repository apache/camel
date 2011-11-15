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
package org.apache.camel.component.irc;

import org.junit.Ignore;

@Ignore
public class IrcsRouteTest extends IrcRouteTest {

    // TODO This test is disabled until we can find a public SSL enabled IRC 
    // server to test against. To use this you'll need to change the server 
    // information below and the username/password. 

    @Override
    protected String sendUri() {
        return "ircs://camel-prd@irc.codehaus.org:6667/#camel-test?nickname=camel-prd&password=blah";
    }

    @Override    
    protected String fromUri() {
        return "ircs://camel-con@irc.codehaus.org:6667/#camel-test?nickname=camel-con&password=blah";
    }    

}