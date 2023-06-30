#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

*** Test Cases ***
Set Variable Camel Exchange Test Case
     ${myvar} =    Set Variable    ${body}
     Should Be Equal    ${myvar}    ${body}
     ${myvar} =    Set Variable    ${headers.stringKey}
     Should Be Equal    ${myvar}    ${headers.stringKey}
     ${myvar} =    Set Variable    ${headers.numericIntKey}
     Should Be Equal    ${myvar}    ${headers.numericIntKey}
     ${myvar} =    Set Variable    ${headers.numericBigDecimalKey}
     Should Be Equal    ${myvar}    ${headers.numericBigDecimalKey}
     ${myvar} =    Set Variable    ${headers.inner.innerStringKey}
     Should Be Equal    ${myvar}    ${headers.inner.innerStringKey}
     ${myvar} =    Set Variable    ${headers.inner.innerNumericIntKey}
     Should Be Equal    ${myvar}    ${headers.inner.innerNumericIntKey}
     ${myvar} =    Set Variable    ${headers.inner.innerNumericBigDecimalKey}
     Should Be Equal    ${myvar}    ${headers.inner.innerNumericBigDecimalKey}
     ${myvar} =    Set Variable    ${properties.stringKey}
     Should Be Equal    ${myvar}    ${properties.stringKey}
     ${myvar} =    Set Variable    ${properties.numericIntKey}
     Should Be Equal    ${myvar}    ${properties.numericIntKey}
     ${myvar} =    Set Variable    ${properties.numericBigDecimalKey}
     Should Be Equal    ${myvar}    ${properties.numericBigDecimalKey}
     ${myvar} =    Set Variable    ${properties.inner.innerStringKey}
     Should Be Equal    ${myvar}    ${properties.inner.innerStringKey}
     ${myvar} =    Set Variable    ${properties.inner.innerNumericIntKey}
     Should Be Equal    ${myvar}    ${properties.inner.innerNumericIntKey}
     ${myvar} =    Set Variable    ${properties.inner.innerNumericBigDecimalKey}
     Should Be Equal    ${myvar}    ${properties.inner.innerNumericBigDecimalKey}
