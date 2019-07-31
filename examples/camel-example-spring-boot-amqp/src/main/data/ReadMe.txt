example project which connects to A-MQ 7 from Fuse 7, using remote A-MQ address

There is the code, from that project, which instantiates component, and sends message

public class CamelRoute extends RouteBuilder {

	@Override
	public void configure() throws Exception {
			JmsComponent component = createArtemisComponent();
			getContext().addComponent("artemis", component);
		
			from("timer://foo?fixedRate=true&period=60000&repeatCount=2")
				.setBody().constant("HELLO")
				.to("artemis:queue:test")
				.log("Sent --> ${body}")
			;	
	}

	private JmsComponent createArtemisComponent() {

		ActiveMQJMSConnectionFactory connectionFactory= new ActiveMQJMSConnectionFactory("tcp://localhost:61616");
		connectionFactory.setUser("admin");
		connectionFactory.setPassword("admin");

		JmsComponent component = new JmsComponent();
		component.setConnectionFactory(connectionFactory);
		
		return component;
	}
}

Please see pom file, I don't specify pom versions, because they come in the BOM

    <dependency>
    <groupId>org.apache.activemq</groupId>
    <artifactId>artemis-jms-client</artifactId>
    </dependency>
     <dependency>
      <groupId>org.apache.camel</groupId>
      <artifactId>camel-jms</artifactId>
    </dependency>


