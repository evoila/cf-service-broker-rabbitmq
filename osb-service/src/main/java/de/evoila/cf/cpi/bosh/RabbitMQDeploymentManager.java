package de.evoila.cf.cpi.bosh;

import de.evoila.cf.broker.bean.BoshProperties;
import de.evoila.cf.broker.model.ServiceInstance;
import de.evoila.cf.broker.model.catalog.plan.Plan;
import de.evoila.cf.broker.model.credential.CertificateCredential;
import de.evoila.cf.broker.model.credential.UsernamePasswordCredential;
import de.evoila.cf.broker.util.MapUtils;
import de.evoila.cf.cpi.CredentialConstants;
import de.evoila.cf.cpi.bosh.deployment.DeploymentManager;
import de.evoila.cf.cpi.bosh.deployment.manifest.Manifest;
import de.evoila.cf.security.credentials.CredentialStore;
import org.springframework.core.env.Environment;
import org.springframework.credhub.support.certificate.CertificateParameters;
import org.springframework.credhub.support.certificate.ExtendedKeyUsage;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Johannes Hiemer.
 */
public class RabbitMQDeploymentManager extends DeploymentManager {

    private final String INSTANCE_GROUP = "rabbitmq";
    private CredentialStore credentialStore;
    private static final String SSL_CA = "cacert";
    private static final String SSL_CERT = "cert";
    private static final String SSL_KEY = "key";
    private static final String ORGANIZATION = "evoila";

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

        Map<String, Object> rabbitmqProperties = manifestProperties(INSTANCE_GROUP, manifest);

        HashMap<String, Object> rabbitmqExporter = (HashMap<String, Object>) rabbitmqProperties.get("rabbitmq_exporter");


        HashMap<String, Object> rabbitmq = (HashMap<String, Object>) rabbitmqProperties.get("rabbitmq");
        HashMap<String, Object> rabbitmqServer = (HashMap<String, Object>) rabbitmq.get("server");
        HashMap<String, Object> rabbitmqTls = (HashMap<String, Object>) rabbitmqServer.get("ssl");


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

        // set up tsl config
        CertificateCredential certificateCredential = credentialStore.createCertificate(serviceInstance, CredentialConstants.TRANSPORT_SSL,
                CertificateParameters.builder()
                        .organization(ORGANIZATION)
                        .selfSign(true)
                        .certificateAuthority(true)
                        .extendedKeyUsage(ExtendedKeyUsage.CLIENT_AUTH, ExtendedKeyUsage.SERVER_AUTH)
                        .build());

        rabbitmqTls.put(SSL_CA, certificateCredential.getCertificateAuthority());
        rabbitmqTls.put(SSL_CERT, certificateCredential.getCertificate());
        rabbitmqTls.put(SSL_KEY, certificateCredential.getPrivateKey());


        for (Map.Entry parameter : customParameters.entrySet()) {
            Map<String, Object> manifestProperties = manifestProperties(parameter.getKey().toString(), manifest);

            if (manifestProperties != null)
                MapUtils.deepMerge(manifestProperties, customParameters);
        }


        serviceInstance.setUsername(brokerUsernamePasswordCredential.getUsername());

        this.updateInstanceGroupConfiguration(manifest, plan);
    }

    private Map<String, Object> manifestProperties(String instanceGroup, Manifest manifest) {
        return manifest
                .getInstanceGroups()
                .stream()
                .filter(i -> {
                    if (i.getName().equals(instanceGroup))
                        return true;
                    return false;
                }).findFirst().get().getProperties();
    }
}
