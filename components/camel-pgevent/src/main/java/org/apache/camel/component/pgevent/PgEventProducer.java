package org.apache.camel.component.pgevent;

import com.impossibl.postgres.api.jdbc.PGConnection;
import com.impossibl.postgres.api.jdbc.PGNotificationListener;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultAsyncProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The PgEvent producer.
 */
public class PgEventProducer extends DefaultAsyncProducer {
    private static final Logger LOG = LoggerFactory.getLogger(PgEventProducer.class);
    private final PgEventEndpoint endpoint;
    private PGConnection dbConnection;
    private PGNotificationListener listener;

    public PgEventProducer(PgEventEndpoint endpoint) throws Exception {
        super(endpoint);
        this.endpoint = endpoint;
        dbConnection = endpoint.initJdbc();
    }

    protected final void initJdbc() throws ClassNotFoundException, SQLException {
        if (endpoint.getDatasource()!=null) {
            dbConnection = (PGConnection)endpoint.getDatasource().getConnection();
        } else {
            System.setProperty("pgjdbc.test.user", endpoint.getUser());
            System.setProperty("pgjdbc.test.password", endpoint.getPass());
            
            Class.forName("com.impossibl.postgres.jdbc.PGDriver");
            dbConnection = (PGConnection)DriverManager.getConnection(
                "jdbc:pgsql://"+endpoint.getHost()+":"+endpoint.getPort()+"/"+
                endpoint.getDatabase());
        }
    }

    @Override
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        
        try {
            if (dbConnection.isClosed()) {
                dbConnection = endpoint.initJdbc();
            }
        } catch (Exception e) {
            throw new InvalidStateException("Database connection closed "+
                "and could not be re-opened.", e);
        }
        
        boolean retVal;
        try {
            dbConnection.createStatement().execute("NOTIFY "+
                endpoint.getChannel()+", '"+
                exchange.getOut().getBody(String.class)+
                "'");
            retVal = true;
        } catch (SQLException e) {
            retVal = false;
        }
        callback.done(retVal);
        return retVal;
    }

    @Override
    protected void doShutdown() throws Exception {
        super.doShutdown();
        dbConnection.close();
    }

    
}