import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@CamelSpringBootTest
public class ApplicationTest {

	@Autowired
	private CamelContext camelContext;

	@Autowired
	private ProducerTemplate producerTemplate;

	@Test
	public void test() {

	}
}
