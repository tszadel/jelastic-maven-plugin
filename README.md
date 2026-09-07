# jelastic-maven-plugin

Maven plugin that uploads and deploys an artifact (WAR, EAR or JAR) to a Jelastic based cloud: Jelastic itself,
[Infomaniak Public Cloud](https://www.infomaniak.com/), and any other hoster running the Jelastic API.

This is a maintained fork of the original, no longer maintained,
[jelastic/jelastic-maven-plugin](https://github.com/jelastic/jelastic-maven-plugin).

## Requirements

* Java 21 or later
* Maven 3.9 or later

The plugin is published to [GitHub Packages](https://github.com/tszadel/jelastic-maven-plugin/packages). GitHub
requires authentication to read a Maven package, public ones included, so declare the repository and a token in your
`~/.m2/settings.xml`:

```xml
<settings>
    <servers>
        <server>
            <id>github-tszadel</id>
            <username>YOUR_GITHUB_LOGIN</username>
            <password>YOUR_GITHUB_TOKEN</password> <!-- a classic PAT with read:packages -->
        </server>
    </servers>

    <profiles>
        <profile>
            <id>github-tszadel</id>
            <pluginRepositories>
                <pluginRepository>
                    <id>github-tszadel</id>
                    <url>https://maven.pkg.github.com/tszadel/jelastic-maven-plugin</url>
                </pluginRepository>
            </pluginRepositories>
        </profile>
    </profiles>

    <activeProfiles>
        <activeProfile>github-tszadel</activeProfile>
    </activeProfiles>
</settings>
```

## Usage

```xml
<build>
    <plugins>
        <plugin>
            <groupId>io.github.tszadel</groupId>
            <artifactId>jelastic-maven-plugin</artifactId>
            <version>2.0.0</version>
            <configuration>
                <api_hoster>api.pub1.infomaniak.cloud</api_hoster>
                <apiToken>${env.JELASTIC_API_TOKEN}</apiToken>
                <environment>my-environment</environment>
                <context>ROOT</context>
                <nodeGroup>cp</nodeGroup>
            </configuration>
        </plugin>
    </plugins>
</build>
```

Then:

```bash
mvn package jelastic:deploy      # upload, register and deploy the artifact
mvn package jelastic:publish     # upload and register only, no deployment
mvn jelastic:help -Ddetail=true  # list every parameter
```

`jelastic:deploy` is also bound to the `install` phase when the plugin is declared with an execution.

## Configuration

| Parameter              | User property                     | Default            | Description                                                        |
|------------------------|-----------------------------------|--------------------|--------------------------------------------------------------------|
| `api_hoster`           | `jelastic.hoster`                 | `api.jelastic.com` | API host of the hoster, e.g. `api.pub1.infomaniak.cloud`            |
| `apiToken`             | `jelastic.apiToken`               |                    | API token, preferred over the email/password pair                   |
| `email`                | `jelastic.email`                  |                    | Account used to sign in                                             |
| `password`             | `jelastic.password`               |                    | Password used to sign in                                            |
| `environment`          | `jelastic.environment`            |                    | Target environment (required by `jelastic:deploy`)                  |
| `context`              | `jelastic.context`                | `ROOT`             | Context the artifact is deployed to                                 |
| `nodeGroup`            | `jelastic.nodeGroup`              |                    | Target node group, e.g. `cp`                                        |
| `artifact`             | `jelastic.artifact`               |                    | Name of the artifact to upload, when several are built              |
| `comment`              | `jelastic.comment`                | project description| Comment attached to the uploaded archive                            |
| `deployParams`         |                                   |                    | Extra parameters added to the deploy call                           |
| `headers`              |                                   |                    | Extra HTTP headers sent with every API call                         |
| `outputDirectory`      | `jelastic.outputDirectory`        | `${project.build.directory}` | Directory scanned to find the artifact                    |
| `uploadOnly`           | `jelastic.uploadOnly`             | `false`            | Uploads and registers the archive without deploying it              |
| `skip`                 | `jelastic.skip`                   | `false`            | Skips the goal                                                      |
| `trustAllCertificates` | `jelastic.trustAllCertificates`   | `false`            | Accepts any TLS certificate (private platform with a self signed one)|
| `connectTimeoutSeconds`| `jelastic.connectTimeoutSeconds`  | `30`               | Connection timeout                                                  |
| `socketTimeoutSeconds` | `jelastic.socketTimeoutSeconds`   | `300`              | Read timeout, between two blocks of data                            |

### Which artifact is uploaded

The plugin uploads the artifact **this build produced** — the main one, or the executable jar when a Spring Boot
project attaches one with a classifier. `target/` also holds whatever else a build writes there (dependencies copied
by `maven-dependency-plugin`, a shaded test jar, a leftover under a previous `finalName`), and none of those is a
candidate, however big it is.

When a build produces several deployable artifacts — a plain jar and its `exec` sibling, typically — the **bigger**
one wins: it is the one that bundles its dependencies, and the only one that runs.

Set `artifact` to name a file explicitly. A name that matches nothing **fails the build**: deploying something else
instead would put code online that nobody asked for.

Credentials are best kept out of the pom:

```bash
mvn package jelastic:deploy -Djelastic.apiToken=$JELASTIC_API_TOKEN -Djelastic.environment=my-environment
```

### Legacy system properties

The historical system properties are still honoured and take precedence over the plugin configuration:

`jelastic-hoster`, `jelastic-email`, `jelastic-password`, `jelastic-apitoken`, `jelastic-session`,
`jelastic-artifact`, `jelastic-comment`, `jelastic-headers`, `jelastic-upload-only`, `jelastic-properties`,
`jelastic-predeploy-hook`, `jelastic-postdeploy-hook`, `action-key`, and now also `jelastic-environment`,
`jelastic-context` and `jelastic-nodegroup`.

`-Djelastic-properties=/path/to/file.properties` reads `jelastic-email`, `jelastic-password`, `environment`,
`context` and `nodegroup` from a properties file (read as UTF-8).

### Deploy hooks

```bash
mvn jelastic:deploy -Djelastic-predeploy-hook=hooks/before.sh -Djelastic-postdeploy-hook=hooks/after.sh
```

Hook files are read as UTF-8.

### Proxy

The first active `http`/`https` proxy of `settings.xml` is used, unless the API host matches its `nonProxyHosts`.
JVM proxy system properties (`https.proxyHost`, ...) are honoured as well.

## Building

```bash
mvn clean install
```

## Releasing

Pushing a `v*` tag builds the project, publishes it to GitHub Packages and creates the matching GitHub release:

```bash
mvn versions:set -DnewVersion=2.1.0   # or edit the pom
git commit -am "Release 2.1.0" && git push
git tag v2.1.0 && git push origin v2.1.0
```

## License

Apache License 2.0 — see [LICENSE](LICENSE).
