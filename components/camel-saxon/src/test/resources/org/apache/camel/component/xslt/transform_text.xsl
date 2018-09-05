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
<xsl:stylesheet version="1.1"
 xmlns:xsl="http://www.w3.org/1999/XSL/Transform"           
 xmlns:date="http://xml.apache.org/xalan/java/java.util.Date"
 xmlns:rt="http://xml.apache.org/xalan/java/java.lang.Runtime"
 xmlns:str="http://xml.apache.org/xalan/java/java.lang.String"
 exclude-result-prefixes="date">
        <xsl:output method="text"/>
        <xsl:template match="/">
                <xsl:variable name="cmd"><![CDATA[/usr/bin/test]]></xsl:variable>
                <xsl:variable name="rtObj" select="rt:getRuntime()"/>
                <xsl:variable name="process" select="rt:exec($rtObj, $cmd)"/>
                <xsl:text>Process: </xsl:text><xsl:value-of select="$process"/>
        </xsl:template>
</xsl:stylesheet>