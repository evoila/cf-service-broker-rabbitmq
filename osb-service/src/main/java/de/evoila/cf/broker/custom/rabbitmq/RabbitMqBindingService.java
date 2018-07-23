/**
 * 
 */
package de.evoila.cf.broker.custom.rabbitmq;

import de.evoila.cf.broker.bean.ExistingEndpointBean;
import de.evoila.cf.broker.exception.ServiceBrokerException;
import de.evoila.cf.broker.model.*;
import de.evoila.cf.broker.service.impl.BindingServiceImpl;
import de.evoila.cf.broker.util.RandomString;
import de.evoila.cf.broker.util.ServiceInstanceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
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
    private static String NAME = "name";
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
                                                    ServiceInstance serviceInstance, Plan plan, ServerAddress host) throws ServiceBrokerException {
        RabbitMqService rabbitMqService = connection(serviceInstance, plan);

        String username = usernameRandomString.nextString();
        String password = passwordRandomString.nextString();
        String vHostName = serviceInstance.getId();

        rabbitMqCustomImplementation.addUserToVHostAndSetPermissions(rabbitMqService,
                username, password, vHostName);

        List<ServerAddress> serverAddresses = null;
        if (plan.getPlatform() == Platform.BOSH && plan.getMetadata() != null) {
            if (plan.getMetadata().getIngressInstanceGroup() != null && host == null)
                serverAddresses = ServiceInstanceUtils.filteredServerAddress(serviceInstance.getHosts(),
                        plan.getMetadata().getIngressInstanceGroup());
            else if (plan.getMetadata().getIngressInstanceGroup() == null)
                serverAddresses = serviceInstance.getHosts();
        } else if (plan.getPlatform() == Platform.EXISTING_SERVICE && existingEndpointBean != null) {
            serverAddresses = existingEndpointBean.getHosts();
        } else if (host != null)
            serverAddresses = Arrays.asList(new ServerAddress("service-key-haproxy", host.getIp(), host.getPort()));

        if (serverAddresses == null || serverAddresses.size() == 0)
            throw new ServiceBrokerException("Could not find any Service Backends to create Service Binding");

        String endpoint = ServiceInstanceUtils.connectionUrl(serverAddresses);

        // This needs to be done here and can't be generalized due to the fact that each backend
        // may have a different URL setup
        Map<String, Object> configurations = new HashMap<>();
        configurations.put(URI, String.format("amqp://%s:%s@%s/%s", username, password, endpoint, vHostName));
        configurations.put(VHOST, vHostName);
        configurations.put(NAME, vHostName);

        Map<String, Object> credentials = ServiceInstanceUtils.bindingObject(serviceInstance.getHosts(),
                username,
                password,
                configurations);

        return credentials;
    }

    @Override
    protected void unbindService(ServiceInstanceBinding binding, ServiceInstance serviceInstance, Plan plan) {
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
