package org.apache.camel.component.springldap;

import org.springframework.ldap.core.LdapOperations;

/**
 * Provides a way to invoke any method on {@link LdapOperations} when an operation is not provided out of the box by this component.
 * 
 * @param <Q>
 *            - The set of request parameters as expected by the method being invoked
 * @param <S>
 *            - The response to be returned by the method being invoked
 */
public interface LdapOperationsFunction<Q, S> {

	/**
	 * @param ldapOperations
	 *            - An instance of {@link LdapOperations}
	 * @param request
	 *            - Any object needed by the {@link LdapOperations} method being invoked
	 * @return - result of the {@link LdapOperations} method being invoked
	 */
	S apply(LdapOperations ldapOperations, Q request);

}
