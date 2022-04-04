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
package org.apache.camel.component.robotframework;

import org.apache.camel.spi.Metadata;

public interface RobotFrameworkCamelConstants {

    @Metadata(description = "The robot variables.", javaType = "List<String>")
    String CAMEL_ROBOT_VARIABLES = "CamelRobotVariables";
    @Metadata(description = "The return code.", javaType = "Integer")
    String CAMEL_ROBOT_RETURN_CODE = "CamelRobotReturnCode";
    @Metadata(description = "The new resource URI.", javaType = "String")
    String CAMEL_ROBOT_RESOURCE_URI = "CamelRobotResourceUri";

}
