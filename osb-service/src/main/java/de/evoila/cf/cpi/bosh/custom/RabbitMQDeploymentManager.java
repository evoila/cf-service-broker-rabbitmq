package de.evoila.cf.cpi.bosh.custom;

import de.evoila.cf.broker.bean.BoshProperties;
import de.evoila.cf.broker.model.Plan;
import de.evoila.cf.broker.model.ServiceInstance;
import de.evoila.cf.cpi.bosh.BoshPlatformService;
import de.evoila.cf.cpi.bosh.deployment.DeploymentManager;
import io.bosh.client.deployments.Deployment;
import io.bosh.client.vms.Vm;

import java.util.ArrayList;
import java.util.List;

public class RabbitMQDeploymentManager extends DeploymentManager {


    public RabbitMQDeploymentManager(BoshProperties boshProperties) {
        super(boshProperties);
    }

}
