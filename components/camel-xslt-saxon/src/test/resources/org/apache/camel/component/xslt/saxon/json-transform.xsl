<?xml version="1.0" encoding="UTF-8"?>
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
<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xf="http://www.w3.org/2005/xpath-functions">
    
    <xsl:output method="xml" indent="yes"/>
    
    <xsl:template match="/">
        <Person>
            <Name><xsl:value-of select="xf:map/xf:string[@key='name']" /></Name>
            <Age><xsl:value-of select="xf:map/xf:number[@key='age']" /></Age>
            <Email><xsl:value-of select="xf:map/xf:string[@key='email']" /></Email>
            <Address>
                <Street><xsl:value-of select="xf:map/xf:map[@key='address']/xf:string[@key='street']" /></Street>
                <City><xsl:value-of select="xf:map/xf:map[@key='address']/xf:string[@key='city']" /></City>
                <Zipcode><xsl:value-of select="xf:map/xf:map[@key='address']/xf:string[@key='zipcode']" /></Zipcode>
            </Address>
            <Hobbies>
                <xsl:for-each select="xf:map/xf:array[@key='hobbies']/xf:string">
                    <Hobby><xsl:value-of select="." /></Hobby>
                </xsl:for-each>
            </Hobbies>
        </Person>
    </xsl:template>
</xsl:stylesheet>
