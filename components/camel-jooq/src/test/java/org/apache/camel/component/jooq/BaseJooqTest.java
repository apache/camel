package org.apache.camel.component.jooq;

import java.nio.file.Files;
import java.sql.Connection;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.jooq.DSLContext;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/jooq-spring.xml"})
public abstract class BaseJooqTest extends CamelTestSupport {

    @Autowired
    DSLContext create;

    @Value("classpath:db-hsql.sql")
    Resource ddlScriptFile;

    @Before
    public void init() throws Exception {
        String sql = new String(Files.readAllBytes(ddlScriptFile.getFile().toPath()));
        Connection conn = create.configuration().connectionProvider().acquire();
        conn.createStatement().execute(sql);
    }
}
