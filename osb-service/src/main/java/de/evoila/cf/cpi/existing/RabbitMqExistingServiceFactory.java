/**
 * 
 */
package de.evoila.cf.cpi.existing;

import de.evoila.cf.broker.bean.ExistingEndpointBean;
import de.evoila.cf.broker.custom.rabbitmq.RabbitMqCustomImplementation;
import de.evoila.cf.broker.custom.rabbitmq.RabbitMqService;
import de.evoila.cf.broker.exception.PlatformException;
import de.evoila.cf.broker.model.ServiceInstance;
import de.evoila.cf.broker.model.catalog.plan.Plan;
import de.evoila.cf.broker.model.credential.UsernamePasswordCredential;
import de.evoila.cf.broker.repository.PlatformRepository;
import de.evoila.cf.broker.service.availability.ServicePortAvailabilityVerifier;
import de.evoila.cf.cpi.CredentialConstants;
import de.evoila.cf.security.credentials.CredentialStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * @author Johannes Hiemer.
 *
 */
@Service
@ConditionalOnBean(ExistingEndpointBean.class)
public class RabbitMqExistingServiceFactory extends ExistingServiceFactory {

	private RabbitMqCustomImplementation rabbitMqCustomImplementation;

	private CredentialStore credentialStore;
	
	private ExistingEndpointBean existingEndpointBean;


    public RabbitMqExistingServiceFactory(PlatformRepository platformRepository, ServicePortAvailabilityVerifier portAvailabilityVerifier,
                                          ExistingEndpointBean existingEndpointBean, RabbitMqCustomImplementation rabbitMqCustomImplementation,
                                          CredentialStore credentialStore) {
        super(platformRepository, portAvailabilityVerifier, existingEndpointBean);
        this.rabbitMqCustomImplementation = rabbitMqCustomImplementation;
        this.credentialStore = credentialStore;
    }

    @Override
    public void deleteInstance(ServiceInstance serviceInstance, Plan plan) throws PlatformException {
        RabbitMqService rabbitMqService = rabbitMqCustomImplementation.connection(serviceInstance, plan, null);
        rabbitMqCustomImplementation.removeVHosts(rabbitMqService, serviceInstance.getId());
        rabbitMqCustomImplementation.closeConnection(rabbitMqService);
        credentialStore.deleteCredentials(serviceInstance, CredentialConstants.BROKER_ADMIN);
	}

	@Override
    public ServiceInstance createInstance(ServiceInstance serviceInstance, Plan plan, Map<String, Object> parameters) throws PlatformException {
        credentialStore.createUser(serviceInstance, CredentialConstants.BROKER_ADMIN);
        UsernamePasswordCredential usernamePasswordCredential = credentialStore.getUser(serviceInstance, CredentialConstants.BROKER_ADMIN);

        serviceInstance.setUsername(usernamePasswordCredential.getUsername());

        RabbitMqService rabbitMqService = rabbitMqCustomImplementation.connection(serviceInstance, plan, null);
        rabbitMqCustomImplementation.createVHosts(rabbitMqService, serviceInstance.getId());
        rabbitMqCustomImplementation.addUserToVHostAndSetPermissions(rabbitMqService, usernamePasswordCredential.getUsername(),
                usernamePasswordCredential.getPassword(), serviceInstance.getId());
        rabbitMqCustomImplementation.closeConnection(rabbitMqService);

        return serviceInstance;
	}

    @Override
    public ServiceInstance getInstance(ServiceInstance serviceInstance, Plan plan) {
        return serviceInstance;
    }

}
