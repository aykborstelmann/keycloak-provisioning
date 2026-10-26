package de.cronn.keycloak.provisioning.connection;

import de.cronn.keycloak.provisioning.ManagedNaming;
import de.cronn.keycloak.provisioning.connection.admincli.KeycloakAdminCredentialsConfig;
import de.cronn.keycloak.provisioning.connection.client.KeycloakProvisioningClientConfig;
import de.cronn.keycloak.provisioning.connection.client.ProvisioningClientBootstrapper;
import java.util.Objects;
import java.util.function.Consumer;
import org.apache.commons.lang3.Validate;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;

/**
 * Everything needed to connect to a Keycloak server's <em>master</em> realm, either as the
 * bootstrap admin user or as the provisioning client managed by {@link
 * ProvisioningClientBootstrapper}. Handed to {@link DefaultKeycloakConnectionManager}, which
 * decides which connection to use and when to (re-)bootstrap the provisioning client.
 */
public final class KeycloakDefaultConnectionConfig {

  private final String serverUrl;
  private final KeycloakAdminCredentialsConfig adminCredentialsConfig;
  private final KeycloakProvisioningClientConfig provisioningClientConfig;
  private final ManagedNaming clientNaming;
  private final Consumer<ResteasyClientBuilder> httpClientCustomizer;

  public KeycloakDefaultConnectionConfig(
      String serverUrl,
      KeycloakAdminCredentialsConfig adminCredentialsConfig,
      KeycloakProvisioningClientConfig provisioningClientConfig) {
    this(serverUrl, adminCredentialsConfig, provisioningClientConfig, ManagedNaming.standard());
  }

  public KeycloakDefaultConnectionConfig(
      String serverUrl,
      KeycloakAdminCredentialsConfig adminCredentialsConfig,
      KeycloakProvisioningClientConfig provisioningClientConfig,
      ManagedNaming clientNaming) {
    this.serverUrl = Validate.notBlank(serverUrl);
    this.adminCredentialsConfig = Objects.requireNonNull(adminCredentialsConfig);
    this.provisioningClientConfig = Objects.requireNonNull(provisioningClientConfig);
    this.clientNaming =
        Objects.requireNonNull(clientNaming)
            .withProvisioningClient(provisioningClientConfig.clientId());
    this.httpClientCustomizer = httpClient -> {};
  }

  private KeycloakDefaultConnectionConfig(
      KeycloakDefaultConnectionConfig base, Consumer<ResteasyClientBuilder> httpClientCustomizer) {
    this.serverUrl = base.serverUrl;
    this.adminCredentialsConfig = base.adminCredentialsConfig;
    this.provisioningClientConfig = base.provisioningClientConfig;
    this.clientNaming = base.clientNaming;
    this.httpClientCustomizer = Objects.requireNonNull(httpClientCustomizer);
  }

  /** Runs after this library's own registrations, so it can add further ones. */
  public KeycloakDefaultConnectionConfig withHttpClientCustomizer(
      Consumer<ResteasyClientBuilder> httpClientCustomizer) {
    return new KeycloakDefaultConnectionConfig(this, httpClientCustomizer);
  }

  public ManagedNaming clientNaming() {
    return clientNaming;
  }

  public String serverUrl() {
    return serverUrl;
  }

  public KeycloakProvisioningClientConfig provisioningClientConfig() {
    return provisioningClientConfig;
  }

  public KeycloakAdminCredentialsConfig adminCredentialsConfig() {
    return adminCredentialsConfig;
  }

  public Consumer<ResteasyClientBuilder> httpClientCustomizer() {
    return httpClientCustomizer;
  }
}
