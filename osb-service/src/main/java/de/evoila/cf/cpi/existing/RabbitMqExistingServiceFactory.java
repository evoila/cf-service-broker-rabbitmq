/**
 * 
 */
package de.evoila.cf.cpi.existing;

import de.evoila.cf.broker.bean.ExistingEndpointBean;
import de.evoila.cf.broker.custom.rabbitmq.RabbitMqCustomImplementation;
import de.evoila.cf.broker.custom.rabbitmq.RabbitMqService;
import de.evoila.cf.broker.exception.PlatformException;
import de.evoila.cf.broker.model.Platform;
import de.evoila.cf.broker.model.ServiceInstance;
import de.evoila.cf.broker.model.catalog.plan.Plan;
import de.evoila.cf.broker.repository.PlatformRepository;
import de.evoila.cf.broker.service.availability.ServicePortAvailabilityVerifier;
import de.evoila.cf.broker.util.RandomString;
import org.springframework.beans.factory.annotation.Autowired;
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

    RandomString usernameRandomString = new RandomString(10);
    RandomString passwordRandomString = new RandomString(15);

	@Autowired
	private RabbitMqCustomImplementation rabbitMQ;
	
	@Autowired
	private ExistingEndpointBean existingEndpointBean;

    public RabbitMqExistingServiceFactory(PlatformRepository platformRepository, ServicePortAvailabilityVerifier portAvailabilityVerifier, ExistingEndpointBean existingEndpointBean) {
        super(platformRepository, portAvailabilityVerifier, existingEndpointBean);
    }

    @Override
    public void deleteInstance(ServiceInstance serviceInstance, Plan plan) {
        RabbitMqService rabbitMqService = this.connection(serviceInstance, plan);

        rabbitMQ.removeVHosts(rabbitMqService, serviceInstance.getId());
	}

	@Override
    public ServiceInstance createInstance(ServiceInstance serviceInstance, Plan plan, Map<String, Object> parameters) {
        String username = usernameRandomString.nextString();
        String password = passwordRandomString.nextString();

        serviceInstance.setUsername(username);
        serviceInstance.setPassword(password);

        RabbitMqService rabbitMqService = this.connection(serviceInstance, plan);

        rabbitMQ.createVHosts(rabbitMqService, serviceInstance.getId());

        rabbitMQ.addUserToVHostAndSetPermissions(rabbitMqService, username,
                password, serviceInstance.getId());

        return serviceInstance;
	}

    private RabbitMqService connection(ServiceInstance serviceInstance, Plan plan) {
        RabbitMqService rabbitMqService = new RabbitMqService();

        if (plan.getPlatform() == Platform.EXISTING_SERVICE)
            rabbitMqService.createConnection(existingEndpointBean.getUsername(), existingEndpointBean.getPassword(),
                    existingEndpointBean.getDatabase(), existingEndpointBean.getHosts());

        return rabbitMqService;
    }

    @Override
    public ServiceInstance getInstance(ServiceInstance serviceInstance, Plan plan) throws PlatformException {
        return serviceInstance;
    }

}
