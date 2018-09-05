<?xml version="1.0" encoding="ISO-8859-1"?>
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
<xsl:stylesheet xmlns:date="http://exslt.org/dates-and-times" version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <date:months>
        <date:month length="31" abbr="Jan">January</date:month>
        <date:month length="28" abbr="Feb">February</date:month>
    </date:months>
    <xsl:template match="root">
        <xsl:variable name="month-node" select="document('')/*/date:months/date:month[number(2)]"/>
        <xsl:element name="MyDate">
            <xsl:value-of select="$month-node"/>
        </xsl:element>
    </xsl:template>
</xsl:stylesheet>
