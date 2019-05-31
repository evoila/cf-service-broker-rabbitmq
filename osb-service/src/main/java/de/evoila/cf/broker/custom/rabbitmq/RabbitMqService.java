/**
 * 
 */
package de.evoila.cf.broker.custom.rabbitmq;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import de.evoila.cf.broker.exception.PlatformException;
import de.evoila.cf.broker.exception.ServiceBrokerException;
import de.evoila.cf.broker.model.catalog.ServerAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * @author Johannes Hiemer
 *
 */
public class RabbitMqService {

    private static String HTTP_SCHEME = "http";

    private static String HTTPS_SCHEME = "https";

    private static int ADMIN_HTTP_PORT = 15672;

    private static int ADMIN_HTTPS_PORT = 15671;

    private ServerAddress serverAddress;

    private String username;

    private String password;

    private Logger log = LoggerFactory.getLogger(getClass());

	private Connection connection;

	public boolean isConnected() {
	    return connection != null && connection.isOpen();
	}

	public boolean createConnection(String username, String password, String vhostName, List<ServerAddress> serverAddresses, boolean tlsEnabled) throws PlatformException {
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
            if (tlsEnabled) {
                connectionFactory.useSslProtocol();
            }
            connection = connectionFactory.newConnection();
        } catch (NoSuchAlgorithmException | KeyManagementException | IOException | TimeoutException e) {
            log.info("Could not establish connection", e);
            throw new PlatformException(e);
        }
        return true;
	}

	public void closeConnection() throws IOException {
	    this.connection.close();
    }

	public String getAdminApi(boolean tlsEnabled) {
	    if (tlsEnabled)
	        return HTTPS_SCHEME + "://" + this.serverAddress.getIp() + ":" +  ADMIN_HTTPS_PORT + "/api";
        else
            return HTTP_SCHEME + "://" + this.serverAddress.getIp() + ":" +  ADMIN_HTTP_PORT + "/api";
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
