<#--

    Licensed to the Apache Software Foundation (ASF) under one or more
    contributor license agreements.  See the NOTICE file distributed with
    this work for additional information regarding copyright ownership.
    The ASF licenses this file to You under the Apache License, Version 2.0
    (the "License"); you may not use this file except in compliance with
    the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

-->
<?xml version="1.0" encoding="UTF-8"?>
<routes xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xmlns="http://camel.apache.org/schema/xml-io"
        xsi:schemaLocation="
            http://camel.apache.org/schema/xml-io
            https://camel.apache.org/schema/xml-io/camel-xml-io.xsd">

    <route>
        <from uri="timer:xml?period=1000"/>
        <setBody>
            <simple>Hello Camel from ${routeId}</simple>
        </setBody>
        <log message="${body}"/>
    </route>

</routes>
