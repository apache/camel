package org.apache.camel.component.vertx.kafka;

import java.util.Properties;

import org.apache.camel.test.infra.kafka.services.KafkaService;
import org.apache.camel.test.infra.kafka.services.KafkaServiceFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.KafkaAdminClient;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseEmbeddedKafkaTest extends CamelTestSupport {
    @RegisterExtension
    public static KafkaService service = KafkaServiceFactory.createService();

    protected static AdminClient kafkaAdminClient;

    private static final Logger LOG = LoggerFactory.getLogger(BaseEmbeddedKafkaTest.class);

    @BeforeAll
    public static void beforeClass() {
        LOG.info("### Embedded Kafka cluster broker list: " + service.getBootstrapServers());
        System.setProperty("bootstrapServers", service.getBootstrapServers());
    }

    @BeforeEach
    public void setKafkaAdminClient() {
        if (kafkaAdminClient == null) {
            kafkaAdminClient = createAdminClient();
        }
    }

    private static AdminClient createAdminClient() {
        final Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, service.getBootstrapServers());

        return KafkaAdminClient.create(properties);
    }
}
