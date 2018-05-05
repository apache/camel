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
native("jvm")
module org.apache.camel.example "${project.version}" {

    shared import "ceylon.interop.java" "${ceylon.version}";
    shared import "org.apache.camel.camel-core" "${project.version}";
    shared import "org.apache.camel.camel-jetty" "${project.version}";
    // Camel module imports above are used like this to allow testing with Camel snapshot versions, in general you should use:
    // shared import maven "org.apache.camel:camel-core" "$CAMEL-VERSION";
    // shared import maven:"org.apache.camel:camel-jetty" "$CAMEL-VERSION";
}
