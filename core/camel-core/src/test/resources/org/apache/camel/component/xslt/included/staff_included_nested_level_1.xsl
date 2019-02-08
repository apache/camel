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
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

    <xsl:include href="../staff_included_nested_level_2.xsl" />

    <xsl:template match="staff/programmer">
        <html>
            <body>
                <xsl:apply-templates select="age"/>
                <br/>
            </body>
        </html>
    </xsl:template>

    <xsl:template match="age">
        AGE:
        <span style="color:yellow;">
            <xsl:value-of select="."/>
        </span>
        <br/>
    </xsl:template>

</xsl:stylesheet>