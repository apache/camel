package org.apache.camel.component.redis.processor.idempotent;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import javax.annotation.Resource;


@Ignore
public class RedisStringIdempotentRepositoryIntegrationTest extends CamelTestSupport {

  @Produce(uri = "direct:start")
  private ProducerTemplate producer;

  @EndpointInject(uri = "mock:result")
  private MockEndpoint mockResult;

  @Resource
  private RedisTemplate redisTemplate;

  protected RedisStringIdempotentRepository idempotentRepository;

  private static final JedisConnectionFactory CONNECTION_FACTORY = new JedisConnectionFactory();

  static {
    CONNECTION_FACTORY.afterPropertiesSet();
  }

  @Override
  protected JndiRegistry createRegistry() throws Exception {
    JndiRegistry registry = super.createRegistry();
    redisTemplate = new RedisTemplate();
    redisTemplate.setConnectionFactory(CONNECTION_FACTORY);
    redisTemplate.afterPropertiesSet();

    registry.bind("redisTemplate", redisTemplate);
    return registry;
  }

  @Override
  protected RouteBuilder createRouteBuilder() throws Exception {
    idempotentRepository = new RedisStringIdempotentRepository(redisTemplate,
                                                               "redis-idempotent-repository");
    RouteBuilder rb = new RouteBuilder() {
      @Override
      public void configure() throws Exception {
        from("direct:start").idempotentConsumer(body(), idempotentRepository).to("mock:result");
      }
    };
    return rb;
  }

  @Override
  protected CamelContext createCamelContext() throws Exception {
    CamelContext context = super.createCamelContext();
    context.setTracing(true);
    return context;
  }

  @Test
  public void blockDoubleSubmission() throws Exception {
    mockResult.expectedMessageCount(3);
    mockResult.setResultWaitTime(5000);
    producer.sendBody("abc");
    producer.sendBody("bcd");
    producer.sendBody("abc");
    producer.sendBody("xyz");

    assertTrue(idempotentRepository.contains("abc"));
    assertTrue(idempotentRepository.contains("bcd"));
    assertTrue(idempotentRepository.contains("xyz"));
    assertFalse(idempotentRepository.contains("mustNotContain"));
    mockResult.assertIsSatisfied();

  }

  @Test
  public void clearIdempotentRepository() {
    for (int i = 0; i < 10000; i++) {
      redisTemplate.opsForValue().set("key4711", "value4711");
    }
    assertEquals("value4711", redisTemplate.opsForValue().get("key4711"));
    producer.sendBody("abc");
    producer.sendBody("bcd");
    redisTemplate.opsForValue().set("redis1", "1");
    redisTemplate.opsForValue().set("different:xyz", "2");
    assertTrue(idempotentRepository.contains("abc"));
    assertTrue(idempotentRepository.contains("bcd"));
    idempotentRepository.clear();
    assertFalse(idempotentRepository.contains("abc"));
    assertFalse(idempotentRepository.contains("bcd"));
    assertFalse(idempotentRepository.contains("redis1"));
    assertFalse(idempotentRepository.contains("different:xyz"));

    assertEquals("1", redisTemplate.opsForValue().get("redis1"));
    assertEquals("2", redisTemplate.opsForValue().get("different:xyz"));
  }

  @Test
  public void expireIdempotent() throws Exception {
    idempotentRepository.setExpiry(5L);
    producer.sendBody("abc");
    assertTrue(idempotentRepository.contains("abc"));
    Thread.sleep(5000);
    assertFalse(idempotentRepository.contains("abc"));
  }
}

