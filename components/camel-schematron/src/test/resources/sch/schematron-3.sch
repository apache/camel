<?xml version="1.0"?>
<!--

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
<sch:schema xmlns:sch="http://purl.oclc.org/dsdl/schematron"
            queryBinding="xslt2">

    <sch:title>Sample Schematron using XPath 2.0</sch:title>
    <sch:ns prefix="p" uri="http://www.apache.org/camel/schematron"/>
    <sch:ns prefix="xs" uri="http://www.w3.org/2001/XMLSchema"/>

    <!-- Your constraints go here -->
    <sch:pattern>
        <sch:include href="title-rules.sch"/>
        <sch:rule context="chapter">
            <sch:let name="numOfTitles" value="count(title)"/>
            <sch:assert test="title">A chapter should have a title</sch:assert>
            <sch:report test="count(title) > 1">
                        'chapter' element has more than one title present
             </sch:report>
        </sch:rule>
    </sch:pattern>

</sch:schema>
