/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.camel.example.restlet.jdbc;

import org.apache.camel.builder.RouteBuilder;

public class MyRouteConfig extends RouteBuilder {

    @Override
    public void configure() {

        rest("/persons")
            .post().to("direct:postPersons")
            .get().to("direct:getPersons")
            .get("/{personId}").to("direct:getPersonId")
            .put("/{personId}").to("direct:putPersonId")
            .delete("/{personId}").to("direct:deletePersonId");
        
        from("direct:postPersons")
            .setBody(simple("insert into person(firstName, lastName) values('${header.firstName}','${header.lastName}')"))
            .to("jdbc:dataSource")
            .setBody(simple("select * from person where id in (select max(id) from person)"))
            .to("jdbc:dataSource");

        from("direct:getPersons")
            .setBody(simple("select * from person"))
            .to("jdbc:dataSource");
        
        from("direct:getPersonId")
            .setBody(simple("select * from person where id = ${header.personId}"))  
            .to("jdbc:dataSource");
        
        from("direct:putPersonId")            
            .setBody(simple("update person set firstName='${header.firstName}', lastName='${header.lastName}' where id = ${header.personId}"))              
            .to("jdbc:dataSource");

        from("direct:deletePersonId")
            .setBody(simple("delete from person where id = ${header.personId}"))
            .to("jdbc:dataSource");
        
    }
}
