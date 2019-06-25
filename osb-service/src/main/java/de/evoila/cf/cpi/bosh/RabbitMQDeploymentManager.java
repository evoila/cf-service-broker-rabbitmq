package de.evoila.cf.cpi.bosh;

import de.evoila.cf.broker.bean.BoshProperties;
import de.evoila.cf.broker.model.ServiceInstance;
import de.evoila.cf.broker.model.catalog.plan.Plan;
import de.evoila.cf.broker.model.credential.CertificateCredential;
import de.evoila.cf.broker.model.credential.Credential;
import de.evoila.cf.broker.model.credential.UsernamePasswordCredential;
import de.evoila.cf.broker.util.MapUtils;
import de.evoila.cf.cpi.CredentialConstants;
import de.evoila.cf.cpi.bosh.deployment.DeploymentManager;
import de.evoila.cf.cpi.bosh.deployment.manifest.Manifest;
import de.evoila.cf.security.credentials.CredentialStore;
import de.evoila.cf.security.credentials.DefaultCredentialConstants;
import org.springframework.core.env.Environment;
import org.springframework.credhub.support.certificate.CertificateParameters;
import org.springframework.credhub.support.certificate.ExtendedKeyUsage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    private static final String RABBITMQ_USERNAME = "rabbitmq_username";
    private static final String DEFAULT_VHOST_NAME = "default_vhost";



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

        List<HashMap<String, Object>> adminUsers = (List<HashMap<String, Object>>) rabbitmqServer.get("admin_users");
        HashMap<String, Object> adminProperties = adminUsers.get(0);
        UsernamePasswordCredential rootCredentials = credentialStore.createUser(serviceInstance,
                CredentialConstants.ROOT_CREDENTIALS, "root");


        adminProperties.put("username", rootCredentials.getUsername());
        adminProperties.put("password", rootCredentials.getPassword());

        HashMap<String, Object> brokerAdminProperties = adminUsers.get(1);
        UsernamePasswordCredential brokerAdminCredentials = credentialStore.createUser(serviceInstance,
                CredentialConstants.BROKER_ADMIN, "broker_admin");


        brokerAdminProperties.put("username", brokerAdminCredentials.getUsername());
        brokerAdminProperties.put("password", brokerAdminCredentials.getPassword());

        serviceInstance.setUsername(rootCredentials.getUsername());

        UsernamePasswordCredential exporterCredential = credentialStore.createUser(serviceInstance,
                DefaultCredentialConstants.EXPORTER_CREDENTIALS);

        rabbitmqExporter.put("user", exporterCredential.getUsername());
        rabbitmqExporter.put("password", exporterCredential.getPassword());

        HashMap<String, Object> exporterProperties = adminUsers.get(2);
        exporterProperties.put("username", exporterCredential.getUsername());
        exporterProperties.put("password", exporterCredential.getPassword());


        List<HashMap<String, Object>> backupUsers = (List<HashMap<String, Object>>) rabbitmqServer.get("backup_users");
        HashMap<String, Object> backupUserProperties = backupUsers.get(0);
        UsernamePasswordCredential backupUsernamePasswordCredential = credentialStore.createUser(serviceInstance,
                DefaultCredentialConstants.BACKUP_CREDENTIALS);
        backupUserProperties.put("username", backupUsernamePasswordCredential.getUsername());
        backupUserProperties.put("password", backupUsernamePasswordCredential.getPassword());


        List<HashMap<String, Object>> users = (List<HashMap<String, Object>>) rabbitmqServer.get("users");
        HashMap<String, Object> userProperties= users.get(0);
        UsernamePasswordCredential userUsernamePasswordCredential = credentialStore.createUser(serviceInstance,
                RABBITMQ_USERNAME);
        userProperties.put("username", userUsernamePasswordCredential.getUsername());
        userProperties.put("password", userUsernamePasswordCredential.getPassword());

        List<HashMap<String, Object>> vhosts = (List<HashMap<String, Object>>) rabbitmqServer.get("vhosts");
        HashMap<String, Object> vhostProperties= vhosts.get(0);

        vhostProperties.put("name", DEFAULT_VHOST_NAME);

        List<String> vhostUsers = (List<String>) vhostProperties.get("users");
        vhostUsers.add(userUsernamePasswordCredential.getUsername());

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
