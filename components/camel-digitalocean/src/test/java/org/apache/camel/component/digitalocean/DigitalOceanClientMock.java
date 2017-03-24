package org.apache.camel.component.digitalocean;

import com.myjeeva.digitalocean.exception.DigitalOceanException;
import com.myjeeva.digitalocean.exception.RequestUnsuccessfulException;
import com.myjeeva.digitalocean.impl.DigitalOceanClient;
import com.myjeeva.digitalocean.pojo.Account;

/**
 * Created by thomas on 17/03/2017.
 */
public class DigitalOceanClientMock extends DigitalOceanClient {



    public DigitalOceanClientMock() {
        super("token");
    }

    @Override
    public Account getAccountInfo() throws DigitalOceanException, RequestUnsuccessfulException {
        Account account = new Account();
        account.setEmail("camel@apache.org");

        return account;
    }
}
