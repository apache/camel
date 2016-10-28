package org.apache.camel.component.bonita.api;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.MultivaluedHashMap;

import org.apache.camel.component.bonita.api.filter.BonitaAuthFilter;
import org.apache.camel.component.bonita.api.util.BonitaAPIConfig;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
public class BonitaAuthFilterTest {
	
	@Mock
	private ClientRequestContext requestContext;
	
	@Before
	public void setup() {
		Map<String,Cookie> resultCookies = new HashMap<>();
		Mockito.when(requestContext.getCookies()).thenReturn(resultCookies);

	}

	
	@Test(expected=IllegalArgumentException.class)
	public void testBonitaAuthFilterUsernameEmpty() throws IOException {
		BonitaAPIConfig bonitaApiConfig = new BonitaAPIConfig("localhost", "port", "", "password");
		BonitaAuthFilter bonitaAuthFilter = new BonitaAuthFilter(bonitaApiConfig);
		bonitaAuthFilter.filter(requestContext);

	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testBonitaAuthFilterPasswordEmpty() throws IOException {
		BonitaAPIConfig bonitaApiConfig = new BonitaAPIConfig("localhost", "port", "username", "");
		BonitaAuthFilter bonitaAuthFilter = new BonitaAuthFilter(bonitaApiConfig);
		bonitaAuthFilter.filter(requestContext);
	}

}
