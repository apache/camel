package org.apache.camel.itest.security;

import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(locations = {"CxfMessageCamelContext.xml"})
public class GreeterClientCxfMessageTest extends GreeterClientTest {

}
