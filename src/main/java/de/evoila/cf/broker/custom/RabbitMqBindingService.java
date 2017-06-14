/**
 * 
 */
package de.evoila.cf.broker.custom;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import de.evoila.cf.broker.custom.rabbitmq.RabbitMqCustomImplementation;
import de.evoila.cf.broker.custom.rabbitmq.RabbitMqService;
import de.evoila.cf.broker.exception.ServiceBrokerException;
import de.evoila.cf.broker.model.Plan;
import de.evoila.cf.broker.model.Platform;
import de.evoila.cf.broker.model.RouteBinding;
import de.evoila.cf.broker.model.ServerAddress;
import de.evoila.cf.broker.model.ServiceInstance;
import de.evoila.cf.broker.model.ServiceInstanceBinding;
import de.evoila.cf.broker.repository.ServiceDefinitionRepository;
import de.evoila.cf.broker.service.impl.BindingServiceImpl;
import jersey.repackaged.com.google.common.collect.Lists;

/**
 * @author Johannes Hiemer.
 *
 */
@Service
public class RabbitMqBindingService extends BindingServiceImpl {

	private static final String DOUBLE_URL_VALUE = "%d";

	private static final String STRING_URL_VALUE = "%s";

	private static final String PATH_SEPARATOR = "/";

	private static final String AMQP = "amqp://";

	private static final String USER_PASSWORD_SEPARATOR = ":";

	private static final String CREDENTIAL_IP_SEPARATOR = "@";

	private static final String HTTP = "http://";

	private static final String API = "/api";

	private static final String IP_PORT_SEPARATOR = ":";

	private static final String AMQP_PORT_KEY = "default";

	private static final String API_PORT_KEY = "user";

	private static final String URL_PATTERN = AMQP + STRING_URL_VALUE + USER_PASSWORD_SEPARATOR + STRING_URL_VALUE
			+ CREDENTIAL_IP_SEPARATOR + STRING_URL_VALUE + IP_PORT_SEPARATOR + DOUBLE_URL_VALUE + PATH_SEPARATOR
			+ STRING_URL_VALUE;

	private static final String API_URL_PATTERN = HTTP + STRING_URL_VALUE + USER_PASSWORD_SEPARATOR + STRING_URL_VALUE
			+ CREDENTIAL_IP_SEPARATOR + STRING_URL_VALUE + IP_PORT_SEPARATOR + DOUBLE_URL_VALUE;

	private Logger log = LoggerFactory.getLogger(getClass());
	
	@Value("${existing.endpoint.username:}")
	private String username;
	
	@Value("${existing.endpoint.password:}")
	private String password;
	
	@Autowired
	private RabbitMqCustomImplementation rabbitMqCustomImplementation;
	
	@Autowired
	private ServiceDefinitionRepository serviceDefinitionRepository;

	private RabbitMqService connection(ServiceInstance serviceInstance, String vhostName, String userName,
			String password) throws IOException, TimeoutException {
		ServerAddress host = serviceInstance.getHosts().get(0);
		log.info("Opening connection to " + host.getIp() + host.getPort());
		RabbitMqService rabbitMqService = new RabbitMqService();
		rabbitMqService.createConnection(host.getIp(), host.getPort(), vhostName, userName,
				password);
		return rabbitMqService;
	}

	protected Map<String, Object> createCredentials(String bindingId, ServiceInstance serviceInstance,
			List<ServerAddress> hosts) throws ServiceBrokerException {

		
		Plan plan = serviceDefinitionRepository.getPlan(serviceInstance.getPlanId());
		
		ServerAddress amqpHost = null, apiHost = null;
		for (ServerAddress serverAddress : hosts) {
			String name = serverAddress.getName();
			if (name.equals(AMQP_PORT_KEY))
				amqpHost = serverAddress;
			if (name.equals(API_PORT_KEY))
				apiHost = serverAddress;
		}

		if (amqpHost == null) {
			log.debug(hosts.toString());
			throw new ServiceBrokerException("No valid default port was provided");
		}

		if (apiHost == null) {
			throw new ServiceBrokerException("No valid api port was provided");
		}

		String amqpHostAddress = amqpHost.getIp();
		int amqpPort = amqpHost.getPort();
		String vhostName = serviceInstance.getId();

		String userName = bindingId;
		SecureRandom random = new SecureRandom();
		String password = new BigInteger(130, random).toString(32);

		String apiHostAddress = apiHost.getIp();
		int apiPort = apiHost.getPort();
		
		String adminUsername = getAdminUser(serviceInstance);
		String adminPassword = getAdminPassword(serviceInstance);
		
		if(plan.getPlatform().equals(Platform.OPENSTACK)) {
			adminPassword = serviceInstance.getId();
			adminUsername = serviceInstance.getId();
		}

		
		
		rabbitMqCustomImplementation.addUserToVHostAndSetPermissions(adminUsername, adminPassword, userName, password, apiHostAddress, apiPort,
				vhostName );

		String rabbitMqUrl = String.format(URL_PATTERN, userName, password, amqpHostAddress, amqpHost.getPort(),
				vhostName);

		String apiUrl = String.format(API_URL_PATTERN, userName, password, apiHostAddress, apiPort, vhostName);

		try {
			connection(serviceInstance, vhostName, userName, password);
		} catch (IOException | TimeoutException e) {
			throw new ServiceBrokerException("Could not open RabbitMQ connection", e);
		}

		Map<String, Object> credentials = new HashMap<String, Object>();
		credentials.put("uri", rabbitMqUrl);
		credentials.put("api_uri", apiUrl);
		credentials.put("username", userName);
		credentials.put("password", password);
		credentials.put("amqpHost", amqpHostAddress);
		credentials.put("amqpPort", amqpPort);
		credentials.put("apiHost", apiHostAddress);
		credentials.put("apiPort", apiPort);
		credentials.put("vhost", vhostName);
		
		return credentials;
	}

	private String getAdminPassword(ServiceInstance serviceInstance) {
		return (this.password == null || this.password.equals("")) ? serviceInstance.getId() : this.password;
	}

	private String getAdminUser(ServiceInstance serviceInstance) {
		return (this.username == null || this.username.equals("")) ? serviceInstance.getId() : this.username;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * de.evoila.cf.broker.service.impl.BindingServiceImpl#bindServiceKey(java.
	 * lang.String, de.evoila.cf.broker.model.ServiceInstance,
	 * de.evoila.cf.broker.model.Plan, java.util.List)
	 */
	@Override
	protected ServiceInstanceBinding bindServiceKey(String bindingId, ServiceInstance serviceInstance, Plan plan,
			List<ServerAddress> externalAddresses) throws ServiceBrokerException {

		log.debug("bind service key");

		Map<String, Object> credentials = createCredentials(bindingId, serviceInstance, externalAddresses);

		ServiceInstanceBinding serviceInstanceBinding = new ServiceInstanceBinding(bindingId, serviceInstance.getId(),
				credentials, null);
		serviceInstanceBinding.setExternalServerAddresses(externalAddresses);
		return serviceInstanceBinding;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * de.evoila.cf.broker.service.impl.BindingServiceImpl#bindService(java.lang
	 * .String, de.evoila.cf.broker.model.ServiceInstance,
	 * de.evoila.cf.broker.model.Plan)
	 */
	@Override
	protected ServiceInstanceBinding bindService(String bindingId, ServiceInstance serviceInstance, Plan plan)
			throws ServiceBrokerException {

		log.debug("bind service");

		List<ServerAddress> hosts = serviceInstance.getHosts();
		Map<String, Object> credentials = createCredentials(bindingId, serviceInstance, hosts);

		return new ServiceInstanceBinding(bindingId, serviceInstance.getId(), credentials, null);
	}

	

	@Override
	protected void deleteBinding(String bindingId, ServiceInstance serviceInstance) throws ServiceBrokerException {
		ServiceInstanceBinding binding = bindingRepository.findOne(bindingId);

		deleteUserFromVhost(binding, serviceInstance);
	}

	private void deleteUserFromVhost(ServiceInstanceBinding binding, ServiceInstance serviceInstance)
			throws ServiceBrokerException {

		ServerAddress apiHost = null;
		for (ServerAddress serverAddress : serviceInstance.getHosts()) {
			String name = serverAddress.getName();
			if (name.equals(API_PORT_KEY))
				apiHost = serverAddress;
		}

		if (apiHost == null) {
			throw new ServiceBrokerException("No valid api port exists");
		}
		
		

		executeRequest(getAmqpApi(apiHost.getIp(), apiHost.getPort()) + "/users/" + binding.getId(), HttpMethod.DELETE, getAdminUser(serviceInstance), getAdminPassword(serviceInstance),
				null);
	}

	private String getAmqpApi(String amqpHostAddress, int port) {
		return HTTP + amqpHostAddress + ":" + port + API;
	}

	@Override
	public ServiceInstanceBinding getServiceInstanceBinding(String id) {
		throw new UnsupportedOperationException();
	}

	private void executeRequest(String url, HttpMethod method, String username, String password, String payload) {
		HttpHeaders headers = new HttpHeaders();
		headers.set("Authorization", buildAuthHeader(username, password));
		headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
		headers.setContentType(MediaType.APPLICATION_JSON);

		log.info("Requesting: " + url + " and method " + method.toString());

		HttpEntity<String> entity = null;
		if (payload == null)
			entity = new HttpEntity<String>(headers);
		else
			entity = new HttpEntity<String>(payload, headers);

		new RestTemplate().exchange(url, method, entity, String.class);
	}

	private String buildAuthHeader(String username, String password) {
		String auth = username + ":" + password;
		byte[] encodedAuth = Base64.encodeBase64(auth.getBytes(Charset.forName("UTF-8")));

		return "Basic " + new String(encodedAuth);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * de.evoila.cf.broker.service.impl.BindingServiceImpl#createCredentials(
	 * java.lang.String, de.evoila.cf.broker.model.ServiceInstance,
	 * de.evoila.cf.broker.model.ServerAddress)
	 */
	@Override
	protected Map<String, Object> createCredentials(String bindingId, ServiceInstance serviceInstance,
			ServerAddress host) throws ServiceBrokerException {
		log.warn("de.evoila.cf.broker.custom.RabbitMqBindingService#createCredentials( java.lang.String, "
				+ "de.evoila.cf.broker.model.ServiceInstance, de.evoila.cf.broker.model.ServerAddress) "
				+ "was used instead of de.evoila.cf.broker.custom.RabbitMqBindingService#createCredentials( "
				+ "java.lang.String, de.evoila.cf.broker.model.ServiceInstance, "
				+ "java.util.List<de.evoila.cf.broker.model.ServerAddress>)");

		return createCredentials(bindingId, serviceInstance, Lists.newArrayList(host));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * de.evoila.cf.broker.service.impl.BindingServiceImpl#bindRoute(de.evoila.
	 * cf.broker.model.ServiceInstance, java.lang.String)
	 */
	@Override
	protected RouteBinding bindRoute(ServiceInstance serviceInstance, String route) {
		throw new UnsupportedOperationException();
	}
}
