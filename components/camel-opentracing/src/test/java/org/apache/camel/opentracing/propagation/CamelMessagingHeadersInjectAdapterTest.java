package org.apache.camel.opentracing.propagation;

import static org.apache.camel.opentracing.propagation.CamelMessagingHeadersInjectAdapter.JMS_DASH;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class CamelMessagingHeadersInjectAdapterTest {

	private Map<String,Object> map;
	

	@Rule
	public ExpectedException thrown = ExpectedException.none();
	  
	@Before
	public void before() {
	    map = new HashMap<String,Object>();
	}
	
	@Test
	public void cannotGetIterator() {
		CamelMessagingHeadersInjectAdapter adapter = new CamelMessagingHeadersInjectAdapter(map);
		thrown.expect(UnsupportedOperationException.class);
		adapter.iterator();
	}

	@Test
	public void putProperties() {
		CamelMessagingHeadersInjectAdapter adapter = new CamelMessagingHeadersInjectAdapter(map);
		adapter.put("key1", "value1");
	    adapter.put("key2", "value2");
	    adapter.put("key1", "value3");
	    assertEquals("value3", map.get("key1"));
	    assertEquals("value2", map.get("key2"));
	}

	@Test
	public void propertyWithDash() {
		CamelMessagingHeadersInjectAdapter adapter = new CamelMessagingHeadersInjectAdapter(map);
		adapter.put("-key-1-", "value1");
		assertEquals("value1", map.get(JMS_DASH + "key" + JMS_DASH + "1" + JMS_DASH));
	}
}
