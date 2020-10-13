## ------------------------------------------------------------------------
## Licensed to the Apache Software Foundation (ASF) under one or more
## contributor license agreements.  See the NOTICE file distributed with
## this work for additional information regarding copyright ownership.
## The ASF licenses this file to You under the Apache License, Version 2.0
## (the "License"); you may not use this file except in compliance with
## the License.  You may obtain a copy of the License at
##
## http://www.apache.org/licenses/LICENSE-2.0
##
## Unless required by applicable law or agreed to in writing, software
## distributed under the License is distributed on an "AS IS" BASIS,
## WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
## See the License for the specific language governing permissions and
## limitations under the License.
## ------------------------------------------------------------------------
package ${package};

import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.ApiMethod;
import org.apache.camel.spi.ApiParam;
import org.apache.camel.spi.ApiParams;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@ApiParams(apiName = "DEFAULT", description = "This is greeter",
        apiMethods = {
            @ApiMethod(methodName = "greetMe", signatures={"String greetMe(String name)"}),
            @ApiMethod(methodName = "greetUs", signatures={"String greetUs(String name1, String name2)"}),
            @ApiMethod(methodName = "sayHi", signatures={"String sayHi()"})},
        aliases = {})
@UriParams
@Configurer
public class ${name}Configuration {

    @ApiParam(optional = false, apiMethods = {@ApiMethod(methodName = "greetMe")})
    private String name;

    @ApiParam(optional = false, apiMethods = {@ApiMethod(methodName = "greetUs")})
    private String name1;

    @ApiParam(optional = false, apiMethods = {@ApiMethod(methodName = "greetUs")})
    private String name2;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName1() {
        return name1;
    }

    public void setName1(String name1) {
        this.name1 = name1;
    }

    public String getName2() {
        return name2;
    }

    public void setName2(String name2) {
        this.name2 = name2;
    }

}
