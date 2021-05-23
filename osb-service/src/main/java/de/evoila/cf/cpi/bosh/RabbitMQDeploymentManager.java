package de.evoila.cf.cpi.bosh;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.evoila.cf.broker.bean.BoshProperties;
import de.evoila.cf.broker.custom.rabbitmq.CustomParameters;
import de.evoila.cf.broker.model.ServiceInstance;
import de.evoila.cf.broker.model.catalog.plan.Plan;
import de.evoila.cf.broker.model.credential.CertificateCredential;
import de.evoila.cf.broker.model.credential.UsernamePasswordCredential;
import de.evoila.cf.broker.util.MapUtils;
import de.evoila.cf.cpi.CredentialConstants;
import de.evoila.cf.cpi.bosh.deployment.DeploymentManager;
import de.evoila.cf.cpi.bosh.deployment.manifest.Manifest;
import de.evoila.cf.cpi.bosh.deployment.manifest.instanceGroup.JobV2;
import de.evoila.cf.cpi.bosh.deployment.manifest.Variable;
import de.evoila.cf.cpi.bosh.deployment.manifest.features.Features;
import de.evoila.cf.security.credentials.CredentialStore;
import de.evoila.cf.security.credentials.DefaultCredentialConstants;
import org.springframework.core.env.Environment;
import org.springframework.credhub.support.certificate.CertificateParameters;
import org.springframework.credhub.support.certificate.ExtendedKeyUsage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Johannes Hiemer, Marco Di Martino.
 *
 */
@Component
public class RabbitMQDeploymentManager extends DeploymentManager {

    private final String INSTANCE_GROUP = "rabbitmq";
    private CredentialStore credentialStore;
    private static final String SSL_CA = "cacert";
    private static final String SSL_CERT = "cert";
    private static final String SSL_KEY = "key";
    private static final String ORGANIZATION = "evoila";
    private static final String RABBITMQ_USERNAME = "rabbitmq_username";

    private ObjectMapper objectMapper;



    public RabbitMQDeploymentManager(BoshProperties boshProperties, Environment environment, CredentialStore credentialStore,
                                     ObjectMapper objectMapper) {
        super(boshProperties, environment);
        this.credentialStore = credentialStore;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void replaceParameters(ServiceInstance serviceInstance, Manifest manifest, Plan plan,
                                     Map<String, Object> customParameters, boolean isUpdate) {
        CustomParameters planParameters = objectMapper.convertValue(plan.getMetadata().getCustomParameters(), CustomParameters.class);
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
        brokerAdminProperties.put("tag", "administrator");


        serviceInstance.setUsername(rootCredentials.getUsername());

        UsernamePasswordCredential exporterCredential = credentialStore.createUser(serviceInstance,
                DefaultCredentialConstants.EXPORTER_CREDENTIALS);




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


        HashMap<String, Object> exporterProperties = users.get(1);
        exporterProperties.put("username", exporterCredential.getUsername());
        exporterProperties.put("password", exporterCredential.getPassword());
        exporterProperties.put("tag", "monitoring");

        List<HashMap<String, Object>> vhosts = (List<HashMap<String, Object>>) rabbitmqServer.get("vhosts");
        HashMap<String, Object> vhostProperties= vhosts.get(0);

        vhostProperties.put("name", serviceInstance.getId());

        List<String> vhostUsers = (List<String>) vhostProperties.get("users");
        vhostUsers.clear();
        vhostUsers.add(userUsernamePasswordCredential.getUsername());
        vhostUsers.add(exporterCredential.getUsername());

        HashMap<String, Object> rabbitmqExporterRabbitmq = (HashMap<String, Object>)getProperty(rabbitmqExporter,"rabbitmq");
        rabbitmqExporterRabbitmq.put("user", exporterCredential.getUsername());
        rabbitmqExporterRabbitmq.put("password", exporterCredential.getPassword());

        if (planParameters.getDns() != null) {
            String dnsEntry = planParameters.getDns();
            String urlPrefix = serviceInstance.getId().replace("-", "");
            rabbitmqServer.put("dns_postfix","rabbitmq." + urlPrefix + "." + dnsEntry);
            JobV2 rabbitmqJob = manifest.getInstanceGroup("rabbitmq").get().getJobs().stream().findFirst().filter(job -> {
                return job.getName().equals("rabbitmq-server");
            }).get();
            JobV2 haproxyJob = manifest.getInstanceGroup("haproxy").get().getJobs().stream().findFirst().filter(job -> {
                return job.getName().equals("haproxy");
            }).get();

            List<JobV2.Aliases> rabbitmqAliaes = rabbitmqJob.getProvides().get("rabbitmq-address").getAliases();
            List<JobV2.Aliases>  haproxyAliaes = haproxyJob.getProvides().get("haproxy-address").getAliases();
            String dns = planParameters.getDns();
            ArrayList<String> altNames = new ArrayList<String>();

            altNames.add(urlPrefix + "." + dnsEntry);
            altNames.add("*.rabbitmq." + urlPrefix + "." + dnsEntry);
            altNames.add("*.haproxy." + urlPrefix + "." + dnsEntry);
            rabbitmqAliaes.add(new JobV2.Aliases("_.rabbitmq." + urlPrefix + "." + dnsEntry, JobV2.PlaceholderType.UUID));
            haproxyAliaes.add(new JobV2.Aliases("_.haproxy." + urlPrefix + "." + dnsEntry, JobV2.PlaceholderType.UUID));
            if (plan.getMetadata().getIngressInstanceGroup().equals("haproxy")){
                haproxyAliaes.add(new JobV2.Aliases( urlPrefix + "." + dnsEntry));
            }else{
                rabbitmqAliaes.add(new JobV2.Aliases( urlPrefix + "." + dnsEntry));
            }

            Variable serverCert = manifest.getVariables().stream().filter(variable -> {
                return variable.getName().equals("server_cert");
            }).findFirst().get();
            serverCert.getOptions().setAlternativeNames(altNames);
            if (planParameters.getCert() != null) {
                serverCert.getOptions().setCa(planParameters.getCert());
            }


            Features features = new Features();
            features.setUseDnsAddresses(true);
            features.setUseShortDnsAddresses(true);
            manifest.setFeatures(features);

        }


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
