/**
 * 
 */
package de.evoila.cf.broker.custom.rabbitmq;

import de.evoila.cf.broker.bean.ExistingEndpointBean;
import de.evoila.cf.broker.model.*;
import de.evoila.cf.broker.service.impl.BindingServiceImpl;
import de.evoila.cf.broker.util.RandomString;
import de.evoila.cf.broker.util.ServiceInstanceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Johannes Hiemer.
 *
 */
@Service
public class RabbitMqBindingService extends BindingServiceImpl {

    private RandomString usernameRandomString = new RandomString(10);
    private RandomString passwordRandomString = new RandomString(15);

    private static String URI = "uri";
    private static String USERNAME = "user";
    private static String PASSWORD = "password";
    private static String HOST = "host";
    private static String PORT = "port";
    private static String VHOST = "vhost";

	private Logger log = LoggerFactory.getLogger(getClass());

	@Autowired
	private RabbitMqCustomImplementation rabbitMqCustomImplementation;
	
	@Autowired
	private ExistingEndpointBean existingEndpointBean;

    @Override
    public ServiceInstanceBinding getServiceInstanceBinding(String id) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected RouteBinding bindRoute(ServiceInstance serviceInstance, String route) {
        throw new UnsupportedOperationException();
    }

    @Override
	protected Map<String, Object> createCredentials(String bindingId, ServiceInstanceBindingRequest serviceInstanceBindingRequest,
                                                    ServiceInstance serviceInstance, Plan plan, ServerAddress host) {
        RabbitMqService rabbitMqService = connection(serviceInstance, plan);

        String username = usernameRandomString.nextString();
        String password = passwordRandomString.nextString();
        String vHostName = serviceInstance.getId();

        rabbitMqCustomImplementation.addUserToVHostAndSetPermissions(rabbitMqService,
                username, password, vHostName);

        List<ServerAddress> serverAddresses = ServiceInstanceUtils.filteredServerAddress(serviceInstance.getHosts(),
                plan.getMetadata().getIngressInstanceGroup());
        String endpoint = ServiceInstanceUtils.connectionUrl(serverAddresses);

        // When host is not empty, it is a service key
        if (host != null)
            endpoint = host.getIp() + ":" + host.getPort();

        String dbURL = String.format("amqp://%s:%s@%s/%s", username, password, endpoint,
                vHostName);

        Map<String, Object> credentials = new HashMap<>();
        credentials.put(URI, dbURL);
        credentials.put(HOST, endpoint.split(":")[0]);
        credentials.put(PORT, endpoint.split(":")[1]);
        credentials.put(USERNAME, username);
        credentials.put(PASSWORD, password);
        credentials.put(VHOST, vHostName);

        return credentials;
    }

    @Override
    protected void deleteBinding(ServiceInstanceBinding binding, ServiceInstance serviceInstance, Plan plan) {
        RabbitMqService rabbitMqService = connection(serviceInstance, plan);

        rabbitMqCustomImplementation.removeUser(rabbitMqService, binding.getCredentials().get(USERNAME).toString());
    }

    private RabbitMqService connection(ServiceInstance serviceInstance, Plan plan) {
        RabbitMqService rabbitMqService = new RabbitMqService();

        if(plan.getPlatform() == Platform.BOSH) {
            List<ServerAddress> serverAddresses = serviceInstance.getHosts();

            if (plan.getMetadata().getIngressInstanceGroup() != null &&
                    plan.getMetadata().getIngressInstanceGroup().length() > 0)
                serverAddresses = ServiceInstanceUtils.filteredServerAddress(serviceInstance.getHosts(),
                        plan.getMetadata().getIngressInstanceGroup());

            rabbitMqService.createConnection(serviceInstance.getUsername(), serviceInstance.getPassword(),
                    "/", serverAddresses);
        } else if (plan.getPlatform() == Platform.EXISTING_SERVICE)
            rabbitMqService.createConnection(existingEndpointBean.getUsername(), existingEndpointBean.getPassword(),
                    existingEndpointBean.getDatabase(), existingEndpointBean.getHosts());

        return rabbitMqService;
    }

}
