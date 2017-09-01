package de.evoila.cf.broker.service;


import de.evoila.cf.broker.bean.BackupConfiguration;
import de.evoila.cf.broker.exception.ServiceInstanceDoesNotExistException;
import de.evoila.cf.model.DatabaseCredential;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;

@ConditionalOnBean(BackupConfiguration.class)
public interface InstanceCredentialService {
    DatabaseCredential getCredentialsForInstanceId (String serviceInstanceId) throws ServiceInstanceDoesNotExistException;
}
