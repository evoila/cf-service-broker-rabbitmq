/**
 * 
 */
package de.evoila.cf.broker.custom.rabbitmq;

import java.io.IOException;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

/**
 * @author Johannes Hiemer
 *
 */
public class RabbitMqService {

	private String host;

	private int port;

	private Connection connection;

	public boolean isConnected() {
		if (connection == null)
			return false;
		return connection.isOpen();
	}

	public void createConnection(String id, String host, int port, String vhostName, String userName, String password)
			throws IOException {
		ConnectionFactory connectionFactory = new ConnectionFactory();
		connectionFactory.setHost(host);
		connectionFactory.setPort(port);
		connectionFactory.setVirtualHost(id);
		connectionFactory.setUsername(id);
		connectionFactory.setPassword(id);

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

}
