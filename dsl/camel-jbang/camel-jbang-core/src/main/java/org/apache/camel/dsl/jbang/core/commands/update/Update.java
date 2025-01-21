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
package org.apache.camel.dsl.jbang.core.commands.update;

import java.util.List;

import org.apache.camel.util.FileUtil;

/**
 * Interface defining the contract for generating the command to update Camel artifacts. This interface provides common
 * methods to Camel, Camel Spring Boot and Camel Quarkus applications.
 *
 * @see CamelUpdateException
 */
public sealed interface Update permits CamelUpdate, CamelQuarkusUpdate {

    String debug();

    String runMode();

    /**
     * Returns the command to execute that updates Apache Camel.
     *
     * @return                      a list of strings representing the command to execute.
     * @throws CamelUpdateException if an error occurs while generating the command.
     */
    List<String> command() throws CamelUpdateException;

    String getArtifactCoordinates();

    default String mvnProgramCall() {
        String mvnProgramCall;
        if (FileUtil.isWindows()) {
            mvnProgramCall = "cmd /c mvn";
        } else {
            mvnProgramCall = "mvn";
        }

        return mvnProgramCall;
    }
}
