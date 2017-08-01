/**
 * 
 */
package de.evoila.cf.broker.custom;

import java.util.List;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;

import de.evoila.cf.broker.bean.ExistingEndpointBean;
import de.evoila.cf.broker.custom.rabbitmq.RabbitMqCustomImplementation;
import de.evoila.cf.broker.custom.rabbitmq.RabbitMqService;
//import de.evoila.cf.broker.custom.mongodb.MongoDbService;
import de.evoila.cf.broker.exception.PlatformException;
import de.evoila.cf.broker.model.ServerAddress;
import de.evoila.cf.cpi.existing.CustomExistingService;
import de.evoila.cf.cpi.existing.CustomExistingServiceConnection;
import de.evoila.cf.cpi.existing.ExistingServiceFactory;

/**
 * @author sebastian boeing, evoila.
 *
 */
@Service
@ConditionalOnBean(ExistingEndpointBean.class)
public class RabbitMqExistingServiceFactory extends ExistingServiceFactory {

	private int adminPort;

	@Autowired
	private RabbitMqCustomImplementation rabbitMQ;
	
	@Autowired
	private ExistingEndpointBean existingEndpointBean;
	
	@PostConstruct
	private void initValues() {
		adminPort = existingEndpointBean.getAdminport();
	}

	public void createVHost(RabbitMqService connection, String database) throws PlatformException {
		rabbitMQ.createVHosts(connection.getHost(), adminPort, connection.getUsername(),
				connection.getPassword(), database);
	}

	public void deleteVHost(RabbitMqService connection, String database) throws PlatformException {
		rabbitMQ.removeVHosts(connection.getHost(), adminPort, connection.getUsername(),
				connection.getPassword(), database);
	}

	@Override
	protected void deleteInstance(CustomExistingServiceConnection connection, String instanceId)
			throws PlatformException {
		if (connection instanceof RabbitMqService)
			deleteVHost((RabbitMqService) connection, instanceId);

	}

	@Override
	protected CustomExistingService getCustomExistingService() {
		return rabbitMQ;
	}

	@Override
	protected void createInstance(CustomExistingServiceConnection connection, String instanceId)
			throws PlatformException {
		if (connection instanceof RabbitMqService)
			createVHost((RabbitMqService) connection, instanceId);

	}
	
	@Override
	protected List<ServerAddress> getExistingServiceHosts() {
		ServerAddress serverAddress = new ServerAddress("default", super.getHosts().get(0), super.getPort());
		ServerAddress serverApiAdress = new ServerAddress("user", super.getHosts().get(0), adminPort);
		return Lists.newArrayList(serverAddress, serverApiAdress);
	}

	public int getAdminPort() {
		return adminPort;
	}
}
