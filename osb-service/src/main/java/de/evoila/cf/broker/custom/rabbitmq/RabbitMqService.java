/**
 * 
 */
package de.evoila.cf.broker.custom.rabbitmq;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import de.evoila.cf.cpi.existing.CustomExistingServiceConnection;

/**
 * @author Johannes Hiemer
 *
 */
public class RabbitMqService implements CustomExistingServiceConnection {

	private String host;

	private int port;

	private Connection connection;

	private String vhost;

	private String username;

	private String password;

	public boolean isConnected() {
		if (connection == null)
			return false;
		return connection.isOpen();
	}

	public void createConnection(String host, int port, String vhostName, String userName, String password)
			throws IOException, TimeoutException {
		ConnectionFactory connectionFactory = new ConnectionFactory();
		connectionFactory.setHost(host);
		connectionFactory.setPort(port);
		connectionFactory.setVirtualHost(vhostName);
		connectionFactory.setUsername(userName);
		connectionFactory.setPassword(password);

		this.host = host;
		this.port = port;
		this.vhost = vhostName;
		this.username = userName;
		this.password = password;

		connection = connectionFactory.newConnection();
	}

	public String getHost() {
		return host;
	}

	public int getPort() {
		return port;
	}

	public Connection rabbitmqClient() {
		return connection;
	}

	public String getVhost() {
		return vhost;
	}

	public String getUsername() {
		return username;
	}

	public String getPassword() {
		return password;
	}
}
