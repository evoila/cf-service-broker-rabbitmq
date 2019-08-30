/**
 * 
 */
package de.evoila.cf.broker.custom.rabbitmq;

import de.evoila.cf.broker.bean.ExistingEndpointBean;
import de.evoila.cf.broker.exception.PlatformException;
import de.evoila.cf.broker.exception.ServiceBrokerException;
import de.evoila.cf.broker.model.*;
import de.evoila.cf.broker.model.catalog.ServerAddress;
import de.evoila.cf.broker.model.catalog.plan.Plan;
import de.evoila.cf.broker.model.credential.UsernamePasswordCredential;
import de.evoila.cf.broker.repository.*;
import de.evoila.cf.broker.service.AsyncBindingService;
import de.evoila.cf.broker.service.impl.BindingServiceImpl;
import de.evoila.cf.broker.util.ServiceInstanceUtils;
import de.evoila.cf.cpi.CredentialConstants;
import de.evoila.cf.security.credentials.CredentialStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Johannes Hiemer.
 */
@Service
public class RabbitMqBindingService extends BindingServiceImpl {

    private Logger log = LoggerFactory.getLogger(getClass());

    private static String URI = "uri";

    private static String USERNAME = "user";

    private static String VHOST = "vhost";

	private RabbitMqCustomImplementation rabbitMqCustomImplementation;

	private ExistingEndpointBean existingEndpointBean;

	private CredentialStore credentialStore;

    public RabbitMqBindingService(BindingRepository bindingRepository, ServiceDefinitionRepository serviceDefinitionRepository,
                                  ServiceInstanceRepository serviceInstanceRepository,
                                  RouteBindingRepository routeBindingRepository,
                                  RabbitMqCustomImplementation rabbitMqCustomImplementation, ExistingEndpointBean existingEndpointBean,
                                  JobRepository jobRepository, AsyncBindingService asyncBindingService, PlatformRepository platformRepository,
                                  CredentialStore credentialStore) {
        super(bindingRepository, serviceDefinitionRepository, serviceInstanceRepository, routeBindingRepository,
                jobRepository, asyncBindingService, platformRepository);
        this.rabbitMqCustomImplementation = rabbitMqCustomImplementation;
        this.existingEndpointBean = existingEndpointBean;
        this.credentialStore = credentialStore;
    }

    @Override
    protected RouteBinding bindRoute(ServiceInstance serviceInstance, String route) {
        throw new UnsupportedOperationException();
    }

    @Override
	protected Map<String, Object> createCredentials(String bindingId, ServiceInstanceBindingRequest serviceInstanceBindingRequest,
                                                    ServiceInstance serviceInstance, Plan plan, ServerAddress host) throws ServiceBrokerException,
            PlatformException {
        UsernamePasswordCredential usernamePasswordCredential = credentialStore.getUser(serviceInstance, CredentialConstants.BROKER_ADMIN);

        boolean tlsEnabled;

        if (plan.getPlatform() == Platform.BOSH) {
            HashMap<String, Object> rabbitmqParams = (HashMap<String, Object>) serviceInstance.getParameters().get("rabbitmq");
            HashMap<String, Object> serverParams = (HashMap<String, Object>) rabbitmqParams.get("server");
            HashMap<String, Object> sslParams = (HashMap<String, Object>) serverParams.get("ssl");
            tlsEnabled = (boolean) sslParams.get("enabled");
        } else {
            tlsEnabled = (boolean) plan.getMetadata().getCustomParameters().get("tlsEnabled");
        }

        RabbitMqService rabbitMqService = rabbitMqCustomImplementation.connection(serviceInstance, plan, usernamePasswordCredential, tlsEnabled);

        credentialStore.createUser(serviceInstance, bindingId);
        UsernamePasswordCredential bindingCredentials = credentialStore.getUser(serviceInstance, bindingId);

        String vHostName = serviceInstance.getId();
        rabbitMqCustomImplementation.addUserToVHostAndSetPermissions(rabbitMqService,
                bindingCredentials.getUsername(),
                bindingCredentials.getPassword(),
                vHostName,
                tlsEnabled);
        rabbitMqCustomImplementation.closeConnection(rabbitMqService);

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
        configurations.put(URI, String.format("amqp://%s:%s@%s/%s", bindingCredentials.getUsername(),
                bindingCredentials.getPassword(), endpoint, vHostName));
        configurations.put(VHOST, vHostName);

        Map<String, Object> credentials = ServiceInstanceUtils.bindingObject(serviceInstance.getHosts(),
                bindingCredentials.getUsername(),
                bindingCredentials.getPassword(),
                configurations);

        return credentials;
    }

    @Override
    protected void unbindService(ServiceInstanceBinding binding, ServiceInstance serviceInstance, Plan plan) throws PlatformException {
        UsernamePasswordCredential usernamePasswordCredential = credentialStore.getUser(serviceInstance, CredentialConstants.BROKER_ADMIN);

        boolean tlsEnabled;

        if (plan.getPlatform() == Platform.BOSH) {
            HashMap<String, Object> rabbitmqParams = (HashMap<String, Object>) serviceInstance.getParameters().get("rabbitmq");
            HashMap<String, Object> serverParams = (HashMap<String, Object>) rabbitmqParams.get("server");
            HashMap<String, Object> sslParams = (HashMap<String, Object>) serverParams.get("ssl");
            tlsEnabled = (boolean) sslParams.get("enabled");
        } else {
            tlsEnabled = (boolean) plan.getMetadata().getCustomParameters().get("tlsEnabled");
        }

        RabbitMqService rabbitMqService = rabbitMqCustomImplementation.connection(serviceInstance, plan, usernamePasswordCredential, tlsEnabled);

        rabbitMqCustomImplementation.removeUser(rabbitMqService, binding.getCredentials().get(USERNAME).toString(), tlsEnabled);
        rabbitMqCustomImplementation.closeConnection(rabbitMqService);

        credentialStore.deleteCredentials(serviceInstance, binding.getId());
    }

}
