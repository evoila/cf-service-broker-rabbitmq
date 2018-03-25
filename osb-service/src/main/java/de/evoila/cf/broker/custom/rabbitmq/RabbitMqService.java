/**
 * 
 */
package de.evoila.cf.broker.custom.rabbitmq;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import de.evoila.cf.broker.model.ServerAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * @author Johannes Hiemer
 *
 */
public class RabbitMqService {

    private static String SCHEME = "http";

    private static int ADMIN_PORT = 15672;

    private ServerAddress serverAddress;

    private String username;

    private String password;

    private Logger log = LoggerFactory.getLogger(getClass());

	private Connection connection;

	public boolean isConnected() {
	    return connection != null && connection.isOpen();
	}

	public boolean createConnection(String username, String password, String vhostName, List<ServerAddress> serverAddresses) {
	    this.serverAddress = serverAddresses.get(0);
	    this.username = username;
        this.password = password;
	    try {
            ConnectionFactory connectionFactory = new ConnectionFactory();
            connectionFactory.setHost(serverAddress.getIp());
            connectionFactory.setPort(serverAddress.getPort());
            connectionFactory.setVirtualHost(vhostName);
            connectionFactory.setUsername(username);
            connectionFactory.setPassword(password);

            connection = connectionFactory.newConnection();
        } catch (IOException | TimeoutException e) {
            log.info("Could not establish connection", e);
            return false;
        }
        return true;
	}

	public String getAdminApi() {
	    return SCHEME + "://" + this.serverAddress.getIp() + ":" +  ADMIN_PORT + "/api";
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
