package org.apache.camel.test.infra.openldap.services;

import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.openldap.common.OpenldapProperties;
import org.testcontainers.containers.GenericContainer;

public class OpenLdapContainer extends GenericContainer<OpenLdapContainer> {
    public static final String CONTAINER_NAME = "openldap";
    public static final int CONTAINER_PORT_LDAP = 389;
    public static final int CONTAINER_PORT_LDAP_OVER_SSL = 636;

    public OpenLdapContainer() {
        super(LocalPropertyResolver.getProperty(OpenldapLocalContainerService.class, OpenldapProperties.OPENLDAP_CONTAINER));

        this.withExposedPorts(CONTAINER_PORT_LDAP, CONTAINER_PORT_LDAP_OVER_SSL)
                .withNetworkAliases(CONTAINER_NAME);
    }
}
