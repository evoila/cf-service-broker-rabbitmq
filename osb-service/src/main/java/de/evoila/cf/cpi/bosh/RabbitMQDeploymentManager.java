package de.evoila.cf.cpi.bosh;

import de.evoila.cf.broker.bean.BoshProperties;
import de.evoila.cf.broker.model.ServiceInstance;
import de.evoila.cf.broker.model.catalog.plan.Plan;
import de.evoila.cf.broker.util.RandomString;
import de.evoila.cf.cpi.bosh.deployment.DeploymentManager;
import de.evoila.cf.cpi.bosh.deployment.manifest.Manifest;
import org.springframework.core.env.Environment;

import java.util.HashMap;
import java.util.Map;

public class RabbitMQDeploymentManager extends DeploymentManager {
    public static final String INSTANCE_GROUP = "rabbitmq";

    private RandomString randomStringPassword = new RandomString(15);

    public RabbitMQDeploymentManager(BoshProperties boshProperties, Environment environment) {
        super(boshProperties, environment);
    }

    @Override
    protected void replaceParameters(ServiceInstance serviceInstance, Manifest manifest, Plan plan, Map<String, Object> customParameters, boolean isUpdate) {
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


        String password = randomStringPassword.nextString();

        rabbitmqExporter.put("password", password);

        HashMap<String, Object> administrators = (HashMap<String, Object>) rabbitmqServer.get("administrators");
        HashMap<String, Object> mgmtAdmins = (HashMap<String, Object>) administrators.get("management");
        HashMap<String, Object> brokerAdmins = (HashMap<String, Object>) administrators.get("broker");
        mgmtAdmins.put("password", password);
        brokerAdmins.put("password", password);
        brokerAdmins.put("vhost", serviceInstance.getId());

        serviceInstance.setUsername("admin");
        serviceInstance.setPassword(password);

        this.updateInstanceGroupConfiguration(manifest, plan);
    }
}
