package za.co.ziemia.gcp.secretmanager;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import com.google.api.client.util.Maps;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretVersionName;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Read <b>Google Cloud Platform <i>SecterManager</i></b> secrets during the build process. Secrets that does not exist will cause the build to fail.
 * <br><br>
 * Note that this plugin always reads the <b>latest</b> secret version for the specified key(s).
 * <br><br>
 * Example config in POM plugin section:
 * <pre>
 * {@code
 * <plugin>
 *     <groupId>za.co.ziemia</groupId>
 *     <artifactId>gcp-secretmanager-maven-plugin</artifactId>
 *     <version>1.0.0-RELEASE</version>
 *     <configuration>
 *         <projectId>your-project-id</projectId>
 *         <secrets>
 *             <secret>secret.key1</secret>
 *             <secret>secret.key2</secret>
 *             <secret>secret.key3</secret>
 *         </secrets>
 *         <!--optional setting, default: false-->
 *         <debug>true|false</debug>
 *         <!--optional setting, default value: value-->
 *         <!--resulting property will be, for example: secret.key1.secret-->
 *         <postfix>secret</postfix>
 *     </configuration>
 *     <executions>
 *         <execution>
 *             <phase>compile</phase>
 *             <goals>
 *                 <goal>read-secrets</goal>
 *             </goals>
 *         </execution>
 *     </executions>
 * </plugin>
 * }
 * </pre>
 */
@Mojo(name = "read-secrets", defaultPhase = LifecyclePhase.COMPILE)
public class GCPSecretManagerMojo extends AbstractMojo {

    private static final String LATEST = "latest";

    /**
     * The project being built
     */
    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    /**
     * If <code>true</code> the secret name as well as the secret is printed during retrieval
     */
    @Parameter(property = "debug", defaultValue = "false")
    private boolean debug;

    /**
     * Postfix to append to secret keys, which is the property under which the secret value will be set for the current build.
     *  For example, if the secret key is <code>key1</code> and the <code>postfix</code> parameter is set to <b>secret</b>, the resulting property that will be set will be <b>key1.secret</b>
     *  <br>
     *  Default value: <code>value</code>
     */
    @Parameter(property = "postfix", defaultValue = "value")
    private String postfix;

    /**
     * The Google Cloud Platform project-id for the project being accessed
     */
    @Parameter(property = "projectId")
    private String projectId;

    /**
     * Array of secrets to fetch
     */
    @Parameter(property = "secrets")
    private String[] secrets;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        List<String> secretsList = Arrays.stream(secrets).collect(Collectors.toList());
        getLog().info(String.format("Fetching %d secrets from SecretManager for Project [%s]", secrets.length, projectId));

        Properties properties = project.getProperties();
        Map<String, String> retrievedSecrets = loadSecrets(secretsList);
        retrievedSecrets.keySet().stream().forEach(k -> addToProperties(k, retrievedSecrets.get(k), properties));
    }

    /**
     * Helper method to load the actual secrets
     */
    private Map<String, String> loadSecrets(List<String> keys) {
        getLog().info("Connecting to GCP");
        try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
            return keys.stream().collect(Collectors.toMap(k -> k, k -> client.accessSecretVersion(SecretVersionName.of(projectId, k, LATEST)).getPayload().getData().toStringUtf8()));
        } catch (IOException e) {
            getLog().warn(String.format("Error retrieving value from secret manager: %s", e.getMessage()));
            return Maps.newHashMap();
        }
    }

    /**
     * Helper method to export the secret values to the current environment
     */
    private void addToProperties(String key, String value, Properties properties) {
        if (debug) {
            getLog().info(String.format("Adding %s.%s = %s", key, postfix, value));
        }
        properties.setProperty(String.format("%s.%s", key, postfix), value);
    }

}
