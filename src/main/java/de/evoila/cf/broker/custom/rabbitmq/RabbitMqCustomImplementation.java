/**
 * 
 */
package de.evoila.cf.broker.custom.rabbitmq;

import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import de.evoila.cf.broker.exception.ServiceBrokerException;
import de.evoila.cf.broker.model.ServerAddress;
import de.evoila.cf.broker.model.ServiceInstance;
import de.evoila.cf.cpi.existing.CustomExistingService;
import de.evoila.cf.cpi.existing.CustomExistingServiceConnection;
import jersey.repackaged.com.google.common.collect.Lists;


/**
 * @author sebastian boeing, evoila.
 *
 */
@Service
public class RabbitMqCustomImplementation implements CustomExistingService {
	
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

	@Value("${existing.endpoint.adminport}")
	private int adminPort;
	
	private Logger log = LoggerFactory.getLogger(RabbitMqCustomImplementation.class);
	
	/* (non-Javadoc)
	 * @see de.evoila.cf.cpi.existing.CustomExistingService#connection(java.lang.String, int, java.lang.String, java.lang.String, java.lang.String)
	 */

	@Override
	public CustomExistingServiceConnection connection(String host, int port, String database, String username,
			String password) throws Exception {
		
		log.info("Opening connection to " + host + ":" + port);
		RabbitMqService rabbitMqService = new RabbitMqService();
		
		try{
		rabbitMqService.createConnection(host, port, database, username,
				password);
		} catch (UnknownHostException e){
			log.info("Could not establish connection", e);
			throw new ServiceBrokerException("Could not establish connection", e);
		}
		return rabbitMqService;
	}
	
	private RabbitMqService connection(ServiceInstance serviceInstance, String vhostName, String userName,
			String password) throws IOException, TimeoutException {
		ServerAddress host = serviceInstance.getHosts().get(0);
		log.info("Opening connection to " + host.getIp() + host.getPort());
		RabbitMqService rabbitMqService = new RabbitMqService();
		rabbitMqService.createConnection(host.getIp(), host.getPort(), vhostName, userName,
				password);
		return rabbitMqService;
	}
	

	/* (non-Javadoc)
	 * @see de.evoila.cf.cpi.existing.CustomExistingService#bindRoleToInstanceWithPassword(de.evoila.cf.cpi.existing.CustomExistingServiceConnection, java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public void bindRoleToInstanceWithPassword(CustomExistingServiceConnection connection, String database,
			String username, String password) throws Exception {
		if(connection instanceof RabbitMqService) {
			RabbitMqService rabbitConnection = (RabbitMqService) connection;
			addUserToVHostAndSetPermissions(rabbitConnection.getUsername(), rabbitConnection.getPassword(), username, password, rabbitConnection.getHost(),adminPort, database);
		}
	}

	
	private void addUserToVHostAndSetPermissions(String adminname, String adminpassword, String newUserName, String newUserPassword, String amqpHostAddress, int port, String vhostName) {

		executeRequest(getAmqpApi(amqpHostAddress, port) + "/users/" + newUserName, HttpMethod.PUT, adminname, adminpassword,
				"{\"password\":\"" + newUserPassword + "\", \"tags\" : \"none\"}");

		executeRequest(getAmqpApi(amqpHostAddress, port) + "/permissions/" + vhostName + PATH_SEPARATOR + newUserName,
				HttpMethod.PUT, adminname, adminpassword, "{\"configure\":\".*\",\"write\":\".*\",\"read\":\".*\"}");
	}
	
	private String getAmqpApi(String amqpHostAddress, int port) {
		return HTTP + amqpHostAddress + ":" + port + API;
	}
	
	public void removeVHosts(String amqpHostAddress, int port, String username, 
			String password, String vhostName) {
		String payload = null;
		executeRequest(getAmqpApi(amqpHostAddress, port) + "/vhosts/" + vhostName, HttpMethod.DELETE, username, password, payload);
	}
	
	public void createVHosts(String amqpHostAddress, int port, String username, 
			String password, String vhostName) {
		String payload = null;
		executeRequest(getAmqpApi(amqpHostAddress, port) + "/vhosts/" + vhostName, HttpMethod.PUT, username, password, payload);
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

	
}
