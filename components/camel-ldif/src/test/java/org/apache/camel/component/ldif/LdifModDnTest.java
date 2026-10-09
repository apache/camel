/*
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
package org.apache.camel.component.ldif;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.api.ldap.model.name.Rdn;
import org.apache.directory.ldap.client.api.LdapConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * RFC 2849: "modrdn" and "moddn" are the same change type. The entry gets the new RDN, and moves under the new superior
 * only when "newsuperior" is given. The connection is a stub that records the resulting DN, so this test needs no LDAP
 * server.
 */
class LdifModDnTest extends CamelTestSupport {

    private final List<String> renames = new ArrayList<>();

    @BindToRegistry("conn")
    private final LdapConnection conn = (LdapConnection) Proxy.newProxyInstance(
            LdapConnection.class.getClassLoader(), new Class<?>[] { LdapConnection.class },
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "rename" -> {
                        Dn dn = (Dn) args[0];
                        renames.add(new Dn((Rdn) args[1], dn.getParent()) + " deleteOldRdn=" + args[2]);
                    }
                    case "moveAndRename" ->
                        renames.add(args[1] + " deleteOldRdn=" + args[2]);
                    case "move" -> renames.add(new Dn(((Dn) args[0]).getRdn(), (Dn) args[1]) + " deleteOldRdn=true");
                    default -> {
                        // close and the other operations are not used
                    }
                }
                return method.getReturnType() == boolean.class ? Boolean.FALSE : null;
            });

    @BeforeEach
    void clear() {
        renames.clear();
    }

    @Test
    void modRdnWithNewSuperiorMovesTheEntry() {
        assertEquals("uid=b,ou=new,dc=example,dc=org deleteOldRdn=true",
                rename("modrdn", "1", "newsuperior: ou=new,dc=example,dc=org\n"));
    }

    @Test
    void modDnWithoutNewSuperiorKeepsTheParent() {
        assertEquals("uid=b,ou=old,dc=example,dc=org deleteOldRdn=true", rename("moddn", "1", ""));
    }

    @Test
    void modDnWithNewSuperiorMovesTheEntry() {
        assertEquals("uid=b,ou=new,dc=example,dc=org deleteOldRdn=false",
                rename("moddn", "0", "newsuperior: ou=new,dc=example,dc=org\n"));
    }

    @Test
    void modRdnWithoutNewSuperiorKeepsTheParent() {
        assertEquals("uid=b,ou=old,dc=example,dc=org deleteOldRdn=false", rename("modrdn", "0", ""));
    }

    private String rename(String changeType, String deleteOldRdn, String newSuperior) {
        String ldif = "version: 1\n"
                      + "dn: uid=a,ou=old,dc=example,dc=org\n"
                      + "changetype: " + changeType + "\n"
                      + "newrdn: uid=b\n"
                      + "deleteoldrdn: " + deleteOldRdn + "\n"
                      + newSuperior;
        List<?> result = template.requestBody("direct:ldif", ldif, List.class);
        assertEquals(List.of("success"), result);
        assertEquals(1, renames.size());
        return renames.get(0);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:ldif").to("ldif:conn");
            }
        };
    }
}
