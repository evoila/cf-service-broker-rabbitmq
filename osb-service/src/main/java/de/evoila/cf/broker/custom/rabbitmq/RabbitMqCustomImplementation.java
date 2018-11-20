/**
 * 
 */
package de.evoila.cf.broker.custom.rabbitmq;

import de.evoila.cf.broker.bean.ExistingEndpointBean;
import de.evoila.cf.broker.model.Platform;
import de.evoila.cf.broker.model.ServiceInstance;
import de.evoila.cf.broker.model.catalog.plan.Plan;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.Charset;
import java.util.Arrays;


/**
 * @author Johannes Hiemer.
 */
@Service
public class RabbitMqCustomImplementation {

    private Logger log = LoggerFactory.getLogger(RabbitMqCustomImplementation.class);

	private ExistingEndpointBean existingEndpointBean;

	public RabbitMqCustomImplementation(ExistingEndpointBean existingEndpointBean) {
		this.existingEndpointBean = existingEndpointBean;
	}

	public RabbitMqService connection(ServiceInstance serviceInstance, Plan plan) {
		RabbitMqService rabbitMqService = new RabbitMqService();

        if(plan.getPlatform() == Platform.BOSH)
            rabbitMqService.createConnection(serviceInstance.getUsername(), serviceInstance.getPassword(),
                    "admin", serviceInstance.getHosts());
        else if (plan.getPlatform() == Platform.EXISTING_SERVICE)
            rabbitMqService.createConnection(existingEndpointBean.getUsername(), existingEndpointBean.getPassword(),
                    existingEndpointBean.getDatabase(), existingEndpointBean.getHosts());

        return  rabbitMqService;
	}

	public void addUserToVHostAndSetPermissions(RabbitMqService rabbitMqService,
                                                String newUserName, String newUserPassword, String vhostName) {

		executeRequest(rabbitMqService.getAdminApi() + "/users/" + newUserName, HttpMethod.PUT, rabbitMqService.getUsername(),
                rabbitMqService.getPassword(),"{\"password\":\"" + newUserPassword + "\", \"tags\" : \"policymaker\"}");

		executeRequest(rabbitMqService.getAdminApi() + "/permissions/" + vhostName + "/" + newUserName,
				HttpMethod.PUT, rabbitMqService.getUsername(),
                rabbitMqService.getPassword(), "{\"configure\":\".*\",\"write\":\".*\",\"read\":\".*\"}");

		//setHaPolicy(rabbitMqService, rabbitMqService.getUsername(), rabbitMqService.getPassword(), vhostName);
	}

	public void removeUser(RabbitMqService rabbitMqService, String username) {
        executeRequest(rabbitMqService.getAdminApi() + "/users/" + username, HttpMethod.DELETE, rabbitMqService.getUsername(),
                rabbitMqService.getPassword(), null);
    }

	public void removeVHosts(RabbitMqService rabbitMqService, String vhostName) {
        executeRequest(rabbitMqService.getAdminApi() + "/vhosts/" + vhostName, HttpMethod.DELETE, rabbitMqService.getUsername(),
                rabbitMqService.getPassword(), null);
	}

	public void createVHosts(RabbitMqService rabbitMqService, String vhostName) {
        executeRequest(rabbitMqService.getAdminApi() + "/vhosts/" + vhostName, HttpMethod.PUT, rabbitMqService.getUsername(),
                rabbitMqService.getPassword(), null);
    }
	
	public void setHaPolicy(RabbitMqService rabbitMqService, String username, String password, String vhostName) {
		String payload = "{\"pattern\": \".*\",\"apply-to\": \"all\",\"definition\": {\"ha-mode\": \"all\",\"ha-sync-mode\": \"automatic\"},\"priority\": 0}";
		executeRequest(rabbitMqService.getAdminApi() + "/policies/" + vhostName +"/ha-"+vhostName,
                HttpMethod.PUT, username, password, payload);
	}

	private void executeRequest(String url, HttpMethod method, String username, String password, String payload) {
		HttpHeaders headers = new HttpHeaders();
		headers.set("Authorization", buildAuthHeader(username, password));
		headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
		headers.setContentType(MediaType.APPLICATION_JSON);

		log.info("Requesting: " + url + " and method " + method.toString());

		HttpEntity<String> entity;
		if (payload == null)
			entity = new HttpEntity<>(headers);
		else
			entity = new HttpEntity<>(payload, headers);

		new RestTemplate().exchange(url, method, entity, String.class);
	}

	private String buildAuthHeader(String username, String password) {
		String auth = username + ":" + password;
		byte[] encodedAuth = Base64.encodeBase64(auth.getBytes(Charset.forName("UTF-8")));

		return "Basic " + new String(encodedAuth);
	}

}
