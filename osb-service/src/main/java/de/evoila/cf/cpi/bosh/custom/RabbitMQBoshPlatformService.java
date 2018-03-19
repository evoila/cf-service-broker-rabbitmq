package de.evoila.cf.cpi.bosh.custom;

import de.evoila.cf.broker.bean.BoshProperties;
import de.evoila.cf.broker.exception.PlatformException;
import de.evoila.cf.broker.model.DashboardClient;
import de.evoila.cf.broker.model.Plan;
import de.evoila.cf.broker.model.ServerAddress;
import de.evoila.cf.broker.model.ServiceInstance;
import de.evoila.cf.broker.repository.PlatformRepository;
import de.evoila.cf.broker.service.CatalogService;
import de.evoila.cf.broker.service.availability.ServicePortAvailabilityVerifier;
import de.evoila.cf.cpi.bosh.BoshPlatformService;
import io.bosh.client.deployments.Deployment;
import io.bosh.client.vms.Vm;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class RabbitMQBoshPlatformService extends BoshPlatformService {
    private final int defaultPort = 5672;

    RabbitMQBoshPlatformService(PlatformRepository repository,CatalogService catalogService,ServicePortAvailabilityVerifier availabilityVerifier,BoshProperties boshProperties,Optional<DashboardClient> dashboardClient) {
        super(repository,catalogService,
              availabilityVerifier,boshProperties,
              dashboardClient,new RabbitMQDeploymentManager(boshProperties));
    }

    @Override
    protected void updateHosts (ServiceInstance in,Plan plan,Deployment deployment) {
        List<Vm> vms = connection.connection().vms().listDetails(
              BoshPlatformService.DEPLOYMENT_NAME_PREFIX + in.getId()).toBlocking().first();
        if (in.getHosts() == null)
            in.setHosts(new ArrayList<>());

        in.getHosts().clear();

        vms.forEach(vm -> {
            in.getHosts().add(new ServerAddress("Host-" + vm.getIndex(),vm.getIps().get(0), defaultPort));
        });
    }

    @Override
    public void postDeleteInstance(ServiceInstance serviceInstance) throws PlatformException { }
}
