package de.evoila.cf.cpi.bosh;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import de.evoila.cf.broker.bean.BoshProperties;
import de.evoila.cf.broker.model.DashboardClient;
import de.evoila.cf.broker.model.ServiceInstance;
import de.evoila.cf.broker.model.catalog.plan.Plan;
import de.evoila.cf.broker.repository.PlatformRepository;
import de.evoila.cf.broker.service.CatalogService;
import de.evoila.cf.broker.service.availability.ServicePortAvailabilityVerifier;
import de.evoila.cf.cpi.CredentialConstants;
import de.evoila.cf.broker.custom.rabbitmq.RabbitmqPort;
import de.evoila.cf.cpi.bosh.deployment.manifest.Manifest;
import de.evoila.cf.security.credentials.CredentialStore;
import io.bosh.client.deployments.Deployment;
import io.bosh.client.vms.Vm;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@ConditionalOnBean(BoshProperties.class)
public class RabbitMQBoshPlatformService extends BoshPlatformService {

    private int defaultPort;

    private final String INSTANCE_GROUP = "rabbitmq";

    private final ObjectMapper mapper;

    private CredentialStore credentialStore;

    public RabbitMQBoshPlatformService(PlatformRepository repository, CatalogService catalogService,
                                ServicePortAvailabilityVerifier availabilityVerifier,
                                BoshProperties boshProperties, Optional<DashboardClient> dashboardClient,
                                Environment environment, CredentialStore credentialStore) {
        super(repository, catalogService,
              availabilityVerifier,boshProperties,
              dashboardClient, new RabbitMQDeploymentManager(boshProperties, environment, credentialStore));
        this.credentialStore = credentialStore;
        this.mapper = new ObjectMapper(new YAMLFactory());
        this.mapper.setSerializationInclusion(JsonInclude.Include.NON_DEFAULT);
    }

    @Override
    protected void updateHosts(ServiceInstance serviceInstance, Plan plan, Deployment deployment) {
        List<Vm> vms = super.getVms(serviceInstance);
        serviceInstance.getHosts().clear();

        Manifest manifest;
        try {
            manifest = mapper.readValue(deployment.getRawManifest(), Manifest.class);
        } catch ( IOException e) {
            throw new RuntimeException("Unable to read bosh manifest");
        }

        Map<String, Object> manifestProperties = manifest.getInstanceGroups()
                .stream()
                .filter(i -> i.getName().equals(INSTANCE_GROUP))
                .findAny().get().getProperties();

        HashMap<String, Object>  rabbitmq = (HashMap<String, Object>) manifestProperties.get("rabbitmq");
        HashMap<String, Object>  rabbitmqServer = (HashMap<String, Object>) rabbitmq.get("server");
        HashMap<String, Object>  rabbitmqSsl = (HashMap<String, Object>) rabbitmqServer.get("ssl");

        if (rabbitmqSsl.get("enable").equals(true)) {
            this.defaultPort = RabbitmqPort.SSL_PORT;
            plan.getMetadata().getCustomParameters().put("tlsEnabled", true);

        } else {
            this.defaultPort = RabbitmqPort.TCP_PORT;
            plan.getMetadata().getCustomParameters().put("tlsEnabled", false);
        }
        vms.forEach(vm -> serviceInstance.getHosts().add(super.toServerAddress(vm, defaultPort)));
    }

    @Override
    public void postDeleteInstance(ServiceInstance serviceInstance) {
        credentialStore.deleteCredentials(serviceInstance, CredentialConstants.MANAGEMENT_ADMIN);
        credentialStore.deleteCredentials(serviceInstance, CredentialConstants.BROKER_ADMIN);
        credentialStore.deleteCertificate(serviceInstance, CredentialConstants.TRANSPORT_SSL);
    }

}
