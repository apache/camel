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
<xsl:stylesheet version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:func="http://mytest/" 
    exclude-result-prefixes="func">

	<xsl:output method="xml" encoding="UTF-8" indent="no" />

	<xsl:template match="/">
		<Test1>
            <xsl:value-of select="func:myExtensionFunction1(1, 2)"/>
        </Test1>
        <Test2>
            <xsl:value-of select="func:myExtensionFunction2('abc', 'cde')"/>
        </Test2>
        <Test3>
            <xsl:value-of select="func:myExtensionFunction2('xyz')"/>
        </Test3>
	</xsl:template>

</xsl:stylesheet>
