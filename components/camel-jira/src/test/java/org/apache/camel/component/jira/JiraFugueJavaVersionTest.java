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
package org.apache.camel.component.jira;

import io.atlassian.fugue.Suppliers;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * CAMEL-25401: fugue 7 is compiled for Java 25, so camel-jira failed on Java 17 and 21 with
 * UnsupportedClassVersionError. The other tests mock the Jira client and never load a fugue class; this one does, so a
 * fugue (or Jira client) jar compiled for a newer Java than the build fails here.
 */
public class JiraFugueJavaVersionTest {

    @Test
    public void fugueLoadsOnTheJavaOfTheBuild() {
        assertEquals("jira", Suppliers.ofInstance("jira").get());
    }
}
