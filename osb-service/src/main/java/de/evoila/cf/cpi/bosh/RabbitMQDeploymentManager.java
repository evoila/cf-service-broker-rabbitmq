package de.evoila.cf.cpi.bosh;

import de.evoila.cf.broker.bean.BoshProperties;
import de.evoila.cf.broker.model.Plan;
import de.evoila.cf.broker.model.ServiceInstance;
import de.evoila.cf.broker.util.RandomString;
import de.evoila.cf.cpi.bosh.deployment.DeploymentManager;
import de.evoila.cf.cpi.bosh.deployment.manifest.Manifest;

import java.util.HashMap;
import java.util.Map;

public class RabbitMQDeploymentManager extends DeploymentManager {
    public static final String INSTANCE_GROUP = "rabbitmq";

    private RandomString randomStringPassword = new RandomString(15);

    public RabbitMQDeploymentManager(BoshProperties boshProperties) {
        super(boshProperties);
    }

    @Override
    protected void replaceParameters(ServiceInstance serviceInstance, Manifest manifest, Plan plan, Map<String, String> customParameters) {
        HashMap<String, Object> properties = new HashMap<>();
        if (customParameters != null && !customParameters.isEmpty())
            properties.putAll(customParameters);

        log.debug("Updating Deployment Manifest, replacing parameters");

        Map<String, Object> manifestProperties = manifest.getInstanceGroups()
                .stream()
                .filter(i -> i.getName().equals(INSTANCE_GROUP))
                .findAny().get().getProperties();

        HashMap<String, Object> rabbitmqExporter = (HashMap<String, Object>) manifestProperties.get("rabbitmq_exporter");
        HashMap<String, Object> rabbitmqServer = (HashMap<String, Object>) manifestProperties.get("rabbitmq-server");

        String password = randomStringPassword.nextString();

        rabbitmqExporter.put("password", password);

        HashMap<String, Object> administrators = (HashMap<String, Object>) rabbitmqServer.get("administrators");
        administrators.put("password", password);

        this.updateInstanceGroupConfiguration(manifest, plan);
    }
}
