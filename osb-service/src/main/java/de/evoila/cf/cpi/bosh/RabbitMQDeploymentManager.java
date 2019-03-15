package de.evoila.cf.cpi.bosh;

import de.evoila.cf.broker.bean.BoshProperties;
import de.evoila.cf.broker.model.ServiceInstance;
import de.evoila.cf.broker.model.catalog.plan.Plan;
import de.evoila.cf.broker.model.credential.UsernamePasswordCredential;
import de.evoila.cf.cpi.CredentialConstants;
import de.evoila.cf.cpi.bosh.deployment.DeploymentManager;
import de.evoila.cf.cpi.bosh.deployment.manifest.Manifest;
import de.evoila.cf.security.credentials.CredentialStore;
import org.springframework.core.env.Environment;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Johannes Hiemer.
 */
public class RabbitMQDeploymentManager extends DeploymentManager {
    public static final String INSTANCE_GROUP = "rabbitmq";

    private CredentialStore credentialStore;

    public RabbitMQDeploymentManager(BoshProperties boshProperties, Environment environment, CredentialStore credentialStore) {
        super(boshProperties, environment);
        this.credentialStore = credentialStore;
    }

    @Override
    protected void replaceParameters(ServiceInstance serviceInstance, Manifest manifest, Plan plan,
                                     Map<String, Object> customParameters, boolean isUpdate) {
        HashMap<String, Object> properties = new HashMap<>();
        if (customParameters != null && !customParameters.isEmpty())
            properties.putAll(customParameters);

        log.debug("Updating Deployment Manifest, replacing parameters");

        Map<String, Object> manifestProperties = manifest.getInstanceGroups()
                .stream()
                .filter(i -> i.getName().equals(INSTANCE_GROUP))
                .findAny().get().getProperties();

        HashMap<String, Object> rabbitmqExporter = (HashMap<String, Object>) manifestProperties.get("rabbitmq_exporter");
        HashMap<String, Object> rabbitmq = (HashMap<String, Object>) manifestProperties.get("rabbitmq");
        HashMap<String, Object> rabbitmqServer = (HashMap<String, Object>) rabbitmq.get("server");


        UsernamePasswordCredential managementUsernamePasswordCredential = credentialStore.createUser(serviceInstance,
                CredentialConstants.MANAGEMENT_ADMIN);

        UsernamePasswordCredential brokerUsernamePasswordCredential = credentialStore.createUser(serviceInstance,
                CredentialConstants.BROKER_ADMIN);

        rabbitmqExporter.put("user", managementUsernamePasswordCredential.getUsername());
        rabbitmqExporter.put("password", managementUsernamePasswordCredential.getPassword());

        HashMap<String, Object> administrators = (HashMap<String, Object>) rabbitmqServer.get("administrators");
        HashMap<String, Object> managementAdmins = (HashMap<String, Object>) administrators.get("management");
        HashMap<String, Object> brokerAdmins = (HashMap<String, Object>) administrators.get("broker");

        managementAdmins.put("username", managementUsernamePasswordCredential.getUsername());
        managementAdmins.put("password", managementUsernamePasswordCredential.getPassword());

        brokerAdmins.put("username", brokerUsernamePasswordCredential.getUsername());
        brokerAdmins.put("password", brokerUsernamePasswordCredential.getPassword());
        brokerAdmins.put("vhost", serviceInstance.getId());

        serviceInstance.setUsername(brokerUsernamePasswordCredential.getUsername());

        this.updateInstanceGroupConfiguration(manifest, plan);
    }
}
