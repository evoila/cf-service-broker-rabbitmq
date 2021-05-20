package de.evoila.cf.cpi.bosh;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import de.evoila.cf.broker.bean.BoshProperties;
import de.evoila.cf.broker.custom.rabbitmq.CustomParameters;
import de.evoila.cf.broker.model.DashboardClient;
import de.evoila.cf.broker.model.ServiceInstance;
import de.evoila.cf.broker.model.catalog.plan.Plan;
import de.evoila.cf.broker.repository.PlatformRepository;
import de.evoila.cf.broker.service.CatalogService;
import de.evoila.cf.broker.model.catalog.ServerAddress;
import de.evoila.cf.broker.service.availability.ServicePortAvailabilityVerifier;
import de.evoila.cf.cpi.CredentialConstants;
import de.evoila.cf.broker.custom.rabbitmq.RabbitmqPort;
import de.evoila.cf.cpi.bosh.deployment.manifest.Manifest;
import de.evoila.cf.security.credentials.CredentialStore;
import de.evoila.cf.security.credentials.DefaultCredentialConstants;
import io.bosh.client.deployments.Deployment;
import io.bosh.client.vms.Vm;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import de.evoila.cf.cpi.bosh.deployment.DeploymentManager;

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

    private ObjectMapper objectMapper;

    public RabbitMQBoshPlatformService(PlatformRepository repository, CatalogService catalogService,
                                       ServicePortAvailabilityVerifier availabilityVerifier,
                                       BoshProperties boshProperties, Optional<DashboardClient> dashboardClient,
                                       Environment environment, CredentialStore credentialStore,
                                       DeploymentManager deploymentManager,
                                       ObjectMapper objectMapper) {
        super(repository, catalogService,
              availabilityVerifier,boshProperties,
              dashboardClient, deploymentManager);
        this.credentialStore = credentialStore;
        this.mapper = new ObjectMapper(new YAMLFactory());
        this.mapper.setSerializationInclusion(JsonInclude.Include.NON_DEFAULT);
        this.objectMapper = objectMapper;
    }

    @Override
    protected void updateHosts(ServiceInstance serviceInstance, Plan plan, Deployment deployment) {

        List<Vm> vms = super.getVms(serviceInstance);
        serviceInstance.getHosts().clear();
        CustomParameters planParameters = objectMapper.convertValue(plan.getMetadata().getCustomParameters(), CustomParameters.class);

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

        if (rabbitmqSsl.get("enabled").equals(true)) {
            this.defaultPort = RabbitmqPort.SSL_PORT;
            updateTlsStatus(serviceInstance, true);
        } else {
            this.defaultPort = RabbitmqPort.TCP_PORT;
            updateTlsStatus(serviceInstance, false);
        }

        if(planParameters.getDns() == null) {
            vms.forEach(vm -> serviceInstance.getHosts().add(super.toServerAddress(vm, defaultPort, plan)));
        }else{
            String dns = serviceInstance.getId().replace("-","") + "." + planParameters.getDns();
            final String backup = ( plan.getMetadata().getBackup() != null )?plan.getMetadata().getBackup().getInstanceGroup():"none ";

            vms.forEach(vm -> {
                serviceInstance.getHosts().add(new ServerAddress(
                                vm.getJobName() + vm.getIndex(),
                                vm.getId() + "." + vm.getJobName() + "." + dns,
                                defaultPort,
                                vm.getJobName().contains(backup)
                        )
                );
            });
        }
    }

    @Override
    public void postDeleteInstance(ServiceInstance serviceInstance) {
        credentialStore.deleteCredentials(serviceInstance, CredentialConstants.ROOT_CREDENTIALS);
        credentialStore.deleteCredentials(serviceInstance, DefaultCredentialConstants.BACKUP_CREDENTIALS);
        credentialStore.deleteCertificate(serviceInstance, DefaultCredentialConstants.EXPORTER_CREDENTIALS);
    }

    private void updateTlsStatus(ServiceInstance serviceInstance, boolean tlsEnabled) {

        HashMap<String, Object> params = (HashMap<String, Object>) serviceInstance.getParameters();
        Map<String, Object> rabbitmq = (HashMap<String, Object>)params.get("rabbitmq");
        if (rabbitmq == null){
            rabbitmq = Map.of("server",new HashMap());
            params.put("rabbitmq",rabbitmq);
        }
        Map<String, Object> rabbitmqServer = (HashMap<String, Object>)rabbitmq.get("server");
        if (rabbitmqServer == null){
            rabbitmqServer = Map.of("ssl",new HashMap());
            rabbitmq.put("server",rabbitmqServer);
        }
        HashMap<String, Object> rabbitmqServerSsl = (HashMap<String, Object>)rabbitmqServer.get("ssl");
        if (rabbitmqServerSsl == null){
            rabbitmqServerSsl = new HashMap<>();
            rabbitmqServer.put("ssl",rabbitmqServerSsl);
        }
        rabbitmqServerSsl.put("enabled", tlsEnabled);
    }

}
