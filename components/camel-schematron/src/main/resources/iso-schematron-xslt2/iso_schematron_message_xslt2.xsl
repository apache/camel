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
<?xml version="1.0" ?><?xar XSLT?> 
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
<!-- Implementation for the Schematron XML Schema Language. 
     Generates simple text output messages using XSLT2 engine
-->
<!--
Open Source Initiative OSI - The MIT License:Licensing
[OSI Approved License]

This source code was previously available under the zlib/libpng license. 
Attribution is polite.

The MIT License

Copyright (c)  2000-2010 Rick Jellife and Academia Sinica Computing Centre, Taiwan.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
-->

<!-- Schematron message -->

<xsl:stylesheet
   version="2.0"
   xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
   xmlns:axsl="http://www.w3.org/1999/XSL/TransformAlias">

<xsl:import href="iso_schematron_skeleton_for_saxon.xsl"/>

<xsl:template name="process-prolog">
   <axsl:output method="text" />
</xsl:template>

<!-- use default rule for process-root:  copy contens / ignore title -->
<!-- use default rule for process-pattern: ignore name and see -->
<!-- use default rule for process-name:  output name -->
<!-- use default rule for process-assert and process-report:
     call process-message -->

<xsl:template name="process-message">
   <xsl:param name="pattern" />
   <xsl:param name="role" />
   <axsl:message>
      <xsl:apply-templates mode="text"  
      /> (<xsl:value-of select="$pattern" />
      <xsl:if test="$role"> / <xsl:value-of select="$role" />
      </xsl:if>)</axsl:message>
</xsl:template>

</xsl:stylesheet>