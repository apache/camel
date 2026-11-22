<?xml version="1.0" encoding="utf-8"?>
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
<!-- Reads the resource given by the resourceUri parameter with the XSLT 3.0 function given by the function parameter. -->
<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:output method="text"/>

    <xsl:param name="function"/>
    <xsl:param name="resourceUri"/>

    <!-- A stylesheet run with fn:transform() that reads the resource given by its u parameter with unparsed-text(). -->
    <xsl:variable name="child" as="xs:string" xmlns:xs="http://www.w3.org/2001/XMLSchema"><![CDATA[
        <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
            <xsl:param name="u"/>
            <xsl:template name="xsl:initial-template">
                <out><xsl:value-of select="unparsed-text($u)"/></out>
            </xsl:template>
        </xsl:stylesheet>]]></xsl:variable>

    <!-- A classpath include, resolved relative to this stylesheet by Camel's URI resolver when it is compiled. -->
    <xsl:include href="resource-functions-included.xsl"/>

    <xsl:template match="/">
        <xsl:call-template name="prefix"/>
        <xsl:choose>
            <xsl:when test="$function = 'unparsed-text'">
                <xsl:value-of select="unparsed-text($resourceUri)"/>
            </xsl:when>
            <xsl:when test="$function = 'unparsed-text-lines'">
                <xsl:value-of select="unparsed-text-lines($resourceUri)"/>
            </xsl:when>
            <xsl:when test="$function = 'unparsed-text-available'">
                <xsl:value-of select="unparsed-text-available($resourceUri)"/>
            </xsl:when>
            <xsl:when test="$function = 'json-doc'">
                <xsl:value-of select="json-doc($resourceUri)?marker"/>
            </xsl:when>
            <xsl:when test="$function = 'collection'">
                <xsl:value-of select="collection($resourceUri)//marker"/>
            </xsl:when>
            <xsl:when test="$function = 'transform'">
                <xsl:value-of select="transform(map {
                        'stylesheet-text': $child,
                        'initial-template': QName('http://www.w3.org/1999/XSL/Transform', 'initial-template'),
                        'stylesheet-params': map { QName('', 'u'): $resourceUri } })?output"/>
            </xsl:when>
        </xsl:choose>
    </xsl:template>

</xsl:stylesheet>
