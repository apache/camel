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
<xsl:stylesheet
  xmlns:xsl='http://www.w3.org/1999/XSL/Transform'
  xmlns:xs='http://www.w3.org/2001/XMLSchema'
  version='1.0'>

  <xsl:output method="xml" indent="yes" encoding="ISO-8859-1"/>


  <xsl:template match="/">
    <transformed>

      <!-- adjust-dateTime-to-timezone not known to the XSLT parser,
           will cause it raise an exception but no error logged by
           camel and deployment will proceed instead of failing.
      -->
      <xsl:value-of select="adjust-dateTime-to-timezone(xs:dateTime($datetimemod), xs:dayTimeDuration('-PT6H'))" />

      <xsl:copy>
        <xsl:copy-of select="attribute::*"/>
        <xsl:apply-templates/>
      </xsl:copy>
    </transformed>
  </xsl:template>

  <xsl:template match="node() | @*">
    <xsl:copy>
      <xsl:apply-templates select="node() | @*"/>
    </xsl:copy>
  </xsl:template>

</xsl:stylesheet>
