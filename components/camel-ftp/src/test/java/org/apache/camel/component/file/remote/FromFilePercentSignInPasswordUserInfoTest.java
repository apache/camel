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
package org.apache.camel.component.file.remote;

/**
 * @version 
 */
public class FromFilePercentSignInPasswordUserInfoTest extends FromFilePercentSignInPasswordTest {

    protected String getFtpUrl() {
        // the user info is not encoded, but we should be forgiving and allow the user to use
        // the user name and password out of the box as is
        return "ftp://us@r:t%st@localhost:" + getPort() + "/tmp3/camel?consumer.initialDelay=3000";
    }

}