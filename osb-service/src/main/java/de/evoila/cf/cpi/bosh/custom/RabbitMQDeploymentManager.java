package de.evoila.cf.cpi.bosh.custom;

import de.evoila.cf.broker.bean.BoshProperties;
import de.evoila.cf.cpi.bosh.deployment.DeploymentManager;

public class RabbitMQDeploymentManager extends DeploymentManager {
    public static final String INSTANCE_GROUP = "rabbitmq";
    public static final String DATA_PATH = "data_path";
    public static final String PORT = "port";

    public RabbitMQDeploymentManager(BoshProperties boshProperties) {
        super(boshProperties);
    }

}
