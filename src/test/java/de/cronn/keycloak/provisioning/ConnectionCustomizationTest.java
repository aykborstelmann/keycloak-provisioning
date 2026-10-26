package de.cronn.keycloak.provisioning;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import de.cronn.keycloak.provisioning.connection.DefaultKeycloakConnectionManager;
import de.cronn.keycloak.provisioning.connection.KeycloakConnectionManager;
import de.cronn.keycloak.provisioning.connection.KeycloakDefaultConnectionConfig;
import de.cronn.keycloak.provisioning.connection.admincli.KeycloakAdminCliConnectionManager;
import de.cronn.keycloak.provisioning.connection.admincli.KeycloakAdminCredentialsConfig;
import de.cronn.keycloak.provisioning.connection.client.KeycloakProvisioningClientConfig;
import de.cronn.keycloak.provisioning.connection.client.KeycloakProvisioningConnectionManager;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The DPoP option and the HTTP client customizer hook on the connection managers. Each test
 * bootstraps its own provisioning client in the master realm, so these need a container of their
 * own rather than the one {@link SharedKeycloakTest} shares across a whole suite.
 */
class ConnectionCustomizationTest extends OwnKeycloakTest {

  @ParameterizedTest(name = "useDPoP={0}")
  @ValueSource(booleans = {true, false})
  void requestsADPoPBoundTokenOnlyWhenEnabled(boolean useDPoP) throws JsonProcessingException {
    KeycloakProvisioningClientConfig config =
        new KeycloakProvisioningClientConfig("dpop-client", "secret").withDPoP(useDPoP);

    try (DefaultKeycloakConnectionManager connectionManager = connectionManager(config)) {
      assertThat(hasConfirmationClaim(connectionManager)).isEqualTo(useDPoP);
      assertThat(dpopAttribute(connectionManager, config.clientId()))
          .isEqualTo(String.valueOf(useDPoP));
    }
  }

  @ParameterizedTest(name = "sees requests through {0}")
  @MethodSource("httpClientCustomizerPaths")
  void appliesTheHttpClientCustomizer(
      String path, BiConsumer<ConnectionCustomizationTest, ClientRequestFilter> connect) {
    RecordingFilter filter = new RecordingFilter();

    connect.accept(this, filter);

    assertThat(filter.seenRequests()).isNotEmpty();
  }

  private static Stream<Arguments> httpClientCustomizerPaths() {
    return Stream.of(
        Arguments.of(
            "the default connection",
            (BiConsumer<ConnectionCustomizationTest, ClientRequestFilter>)
                ConnectionCustomizationTest::connectThroughTheDefaultConnection),
        Arguments.of(
            "admin-cli, connected directly",
            (BiConsumer<ConnectionCustomizationTest, ClientRequestFilter>)
                ConnectionCustomizationTest::connectAsAdminCliDirectly),
        Arguments.of(
            "the provisioning client, connected directly",
            (BiConsumer<ConnectionCustomizationTest, ClientRequestFilter>)
                ConnectionCustomizationTest::connectAsTheProvisioningClientDirectly));
  }

  private void connectThroughTheDefaultConnection(ClientRequestFilter filter) {
    KeycloakDefaultConnectionConfig config =
        new KeycloakDefaultConnectionConfig(
                serverUrl(),
                new KeycloakAdminCredentialsConfig(ADMIN_USERNAME, ADMIN_PASSWORD),
                new KeycloakProvisioningClientConfig("customized-default-client", "secret"))
            .withHttpClientCustomizer(builder -> builder.register(filter));

    try (DefaultKeycloakConnectionManager connectionManager =
        new DefaultKeycloakConnectionManager(config)) {
      connectionManager.realmManager("master").getRoles();
    }
  }

  private void connectAsAdminCliDirectly(ClientRequestFilter filter) {
    try (KeycloakAdminCliConnectionManager adminCli =
        new KeycloakAdminCliConnectionManager(
            serverUrl(),
            new KeycloakAdminCredentialsConfig(ADMIN_USERNAME, ADMIN_PASSWORD),
            ManagedNaming.standard(),
            builder -> builder.register(filter))) {
      assertThat(adminCli.getKeycloakClient()).isNotNull();
    }
  }

  private void connectAsTheProvisioningClientDirectly(ClientRequestFilter filter) {
    KeycloakProvisioningClientConfig config =
        new KeycloakProvisioningClientConfig("customized-direct-client", "secret");
    // Bootstraps the client first, so the direct connection below can authenticate as it.
    connectionManager(config).close();

    try (KeycloakProvisioningConnectionManager direct =
        new KeycloakProvisioningConnectionManager(
            serverUrl(), config, ManagedNaming.standard(), builder -> builder.register(filter))) {
      direct.realmManager("master").getRoles();
    }
  }

  private DefaultKeycloakConnectionManager connectionManager(
      KeycloakProvisioningClientConfig config) {
    return new DefaultKeycloakConnectionManager(
        new KeycloakDefaultConnectionConfig(
            serverUrl(),
            new KeycloakAdminCredentialsConfig(ADMIN_USERNAME, ADMIN_PASSWORD),
            config));
  }

  /** Whether Keycloak added the {@code cnf.jkt} claim it puts on a DPoP-bound access token. */
  private boolean hasConfirmationClaim(KeycloakConnectionManager connectionManager)
      throws JsonProcessingException {
    String[] jwt =
        connectionManager.getKeycloakClient().tokenManager().getAccessTokenString().split("\\.");
    String payload = new String(Base64.getUrlDecoder().decode(jwt[1]), StandardCharsets.UTF_8);
    return jsonMapper().readTree(payload).get("cnf") != null;
  }

  private String dpopAttribute(KeycloakConnectionManager connectionManager, String clientId) {
    return connectionManager
        .realmManager("master")
        .getClientByClientId(clientId)
        .orElseThrow()
        .toRepresentation()
        .getAttributes()
        .get("dpop.bound.access.tokens");
  }

  private static final class RecordingFilter implements ClientRequestFilter {

    private final List<String> seenRequests = new CopyOnWriteArrayList<>();

    @Override
    public void filter(ClientRequestContext requestContext) {
      seenRequests.add(requestContext.getMethod() + " " + requestContext.getUri().getPath());
    }

    List<String> seenRequests() {
      return seenRequests;
    }
  }
}
