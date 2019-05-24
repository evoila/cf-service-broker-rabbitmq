/**
 * 
 */
package de.evoila.cf.broker.custom.rabbitmq;

import de.evoila.cf.broker.bean.ExistingEndpointBean;
import de.evoila.cf.broker.exception.PlatformException;
import de.evoila.cf.broker.exception.ServiceBrokerException;
import de.evoila.cf.broker.model.Platform;
import de.evoila.cf.broker.model.ServiceInstance;
import de.evoila.cf.broker.model.catalog.ServerAddress;
import de.evoila.cf.broker.model.catalog.plan.Plan;
import de.evoila.cf.broker.model.credential.UsernamePasswordCredential;
import de.evoila.cf.broker.util.ServiceInstanceUtils;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpRequest;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.SSLContexts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.nio.charset.Charset;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;


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

    public RabbitMqService connection(ServiceInstance serviceInstance, Plan plan, UsernamePasswordCredential usernamePasswordCredential, boolean tlsEnabled) throws PlatformException {
        RabbitMqService rabbitMqService = new RabbitMqService();

        if(plan.getPlatform() == Platform.BOSH) {
            List<ServerAddress> serverAddresses = serviceInstance.getHosts();

            if (plan.getMetadata().getIngressInstanceGroup() != null &&
                    plan.getMetadata().getIngressInstanceGroup().length() > 0)
                serverAddresses = ServiceInstanceUtils.filteredServerAddress(serviceInstance.getHosts(),
                        plan.getMetadata().getIngressInstanceGroup());
            try {
                rabbitMqService.createConnection(usernamePasswordCredential.getUsername(), usernamePasswordCredential.getPassword(),
                        "/", serverAddresses, tlsEnabled);
            } catch (PlatformException e) {

            }
        } else if (plan.getPlatform() == Platform.EXISTING_SERVICE)
            try {
                rabbitMqService.createConnection(existingEndpointBean.getUsername(), existingEndpointBean.getPassword(),
                        existingEndpointBean.getDatabase(), existingEndpointBean.getHosts(), tlsEnabled);
            } catch (PlatformException e){

            }

        return rabbitMqService;
    }

    public void closeConnection(RabbitMqService rabbitMqService) throws PlatformException {
	    try {
            rabbitMqService.closeConnection();
        } catch (IOException ex) {
            throw new PlatformException("Could not close connection", ex);
        }
    }

	public void addUserToVHostAndSetPermissions(RabbitMqService rabbitMqService,
                                                String newUserName, String newUserPassword, String vhostName, boolean tlsEnabled) {

		executeRequest(rabbitMqService.getAdminApi(tlsEnabled) + "/users/" + newUserName, HttpMethod.PUT, rabbitMqService.getUsername(),
                rabbitMqService.getPassword(),"{\"password\":\"" + newUserPassword + "\", \"tags\" : \"policymaker\"}", tlsEnabled);

		executeRequest(rabbitMqService.getAdminApi(tlsEnabled) + "/permissions/" + vhostName + "/" + newUserName,
				HttpMethod.PUT, rabbitMqService.getUsername(),
                rabbitMqService.getPassword(), "{\"configure\":\".*\",\"write\":\".*\",\"read\":\".*\"}", tlsEnabled);

		//setHaPolicy(rabbitMqService, rabbitMqService.getUsername(), rabbitMqService.getPassword(), vhostName);
	}

	public void removeUser(RabbitMqService rabbitMqService, String username, boolean tlsEnabled) {
        executeRequest(rabbitMqService.getAdminApi(tlsEnabled) + "/users/" + username, HttpMethod.DELETE, rabbitMqService.getUsername(),
                rabbitMqService.getPassword(), null, tlsEnabled);
    }

	public void removeVHosts(RabbitMqService rabbitMqService, String vhostName, boolean tlsEnabled) {
        executeRequest(rabbitMqService.getAdminApi(tlsEnabled) + "/vhosts/" + vhostName, HttpMethod.DELETE, rabbitMqService.getUsername(),
                rabbitMqService.getPassword(), null, tlsEnabled);
	}

	public void createVHosts(RabbitMqService rabbitMqService, String vhostName, boolean tlsEnabled) {
        executeRequest(rabbitMqService.getAdminApi(tlsEnabled) + "/vhosts/" + vhostName, HttpMethod.PUT, rabbitMqService.getUsername(),
                rabbitMqService.getPassword(), null, tlsEnabled);
    }
	
	public void setHaPolicy(RabbitMqService rabbitMqService, String username, String password, String vhostName, boolean tlsEnabled) {
		String payload = "{\"pattern\": \".*\",\"apply-to\": \"all\",\"definition\": {\"ha-mode\": \"all\",\"ha-sync-mode\": \"automatic\"},\"priority\": 0}";
		executeRequest(rabbitMqService.getAdminApi(tlsEnabled) + "/policies/" + vhostName +"/ha-"+vhostName,
                HttpMethod.PUT, username, password, payload, tlsEnabled);
	}

	private void executeRequest(String url, HttpMethod method, String username, String password, String payload, boolean tlsEnabled) {
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

		RestTemplate template;

		if (tlsEnabled) {
			SSLContext sslcontext;
			try {
				sslcontext = SSLContexts.custom().loadTrustMaterial(null,
						new TrustSelfSignedStrategy()).build();
			} catch (NoSuchAlgorithmException | KeyManagementException | KeyStoreException e) {
				throw new RuntimeException();
			}

			SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslcontext,
					new String[]{"TLSv1"}, null, new NoopHostnameVerifier());

			HttpClient httpclient = HttpClients.custom().setSSLSocketFactory(sslsf).build();
			HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpclient);

			template = new RestTemplate(factory);
		} else {
			template = new RestTemplate();
		}

		template.exchange(url, method, entity, String.class);

	}

	private String buildAuthHeader(String username, String password) {
		String auth = username + ":" + password;
		byte[] encodedAuth = Base64.encodeBase64(auth.getBytes(Charset.forName("UTF-8")));

		return "Basic " + new String(encodedAuth);
	}

}
