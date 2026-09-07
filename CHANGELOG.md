# Changelog

## 2.0.2

### Fixed

* **The artifact to upload is the one the build produced, not the biggest file lying in `target/`.** Selection had a
  single criterion — size — and `target/` is a scratch directory, not a manifest: dependencies copied there by
  `maven-dependency-plugin`, a shaded test jar, a leftover from a previous `finalName`, anything bigger than the
  application won. The deployment then succeeded while putting the wrong code online, and nothing said so, because the
  log only ever printed the winner.

  Candidates are now the files Maven attached to the project. Size remains the tie-break **between siblings of the same
  build**, which is what a Spring Boot project configured with `<classifier>exec</classifier>` needs: the plain jar and
  the executable one are both legitimate, and only the bigger one runs. A project deploying in an invocation of its own
  (`mvn jelastic:deploy` without `package`) has no attached file, and keeps the previous behaviour.

### Changed

* **A named `artifact` that does not exist now fails the build.** It used to warn and upload the biggest file instead.
  Someone who names an artifact has a reason to; a typo in that name put an unintended jar online, with the warning
  scrolling past in a CI log nobody reads when the build is green.

## 2.0.1

### Fixed

* **The upload no longer announces a charset the platform reads as part of the boundary.** `MultipartEntityBuilder`
  appends the charset *after* the boundary — `multipart/form-data; boundary=BTDqPv9a1viO6ve; charset=UTF-8` — and a
  parser that takes everything after `boundary=` then looks for a separator that was never written. The Jelastic
  uploader of app.jpe.infomaniak.com is one of those; it answered `result=99` with its own
  `StringIndexOutOfBoundsException: start 0, end -1, length 4293`, on the very first call of the deployment, naming
  neither the plugin nor the request. 1.9.5 sent no charset and kept working on the same platform, the same day, with
  the same token — this regression came in with 2.0.0.

  The part headers are still written in UTF-8, so an artifact name holding non ASCII characters still survives the
  trip: only the header is trimmed back to the boundary, exactly as a browser sends it.

## 2.0.0

Maintenance release of the fork: the plugin builds and runs on current toolchains again, and it now reports what the
platform actually answered instead of failing with an unrelated error.

### Fixed

* **API errors are no longer swallowed.** Every call used to catch `IOException`, log it and return `null`, so the real
  failure surfaced later as a `NullPointerException`. Failures now fail the build with the message returned by the
  platform.
* **Non JSON answers are reported as such.** An HTML error page or a plain text message coming from a gateway or a
  reverse proxy (Infomaniak, nginx, a corporate proxy) is summarised in the error message together with the HTTP status
  code, instead of breaking the JSON parsing.
* **Structured errors are supported.** `error` members returned as an object (`{"code":401,"message":"..."}`) or as a
  list — which some hosters do — are rendered as a readable message instead of breaking the deserialization.
* **Error bodies of 4xx/5xx answers are read.** `BasicResponseHandler` used to throw the body away; the JSON error
  payload carried by an HTTP error is now parsed and reported.
* **Null responses no longer crash.** A failed deployment or registration returns `"response":null`; reading it used to
  throw a `NullPointerException` and hide the actual error.
* **A failed sign in is no longer reported as a success.** A network failure during authentication returned an empty
  object whose `result` was `0`, and the build carried on with a null session.
* **UTF-8 everywhere.**
  * The archive description is serialized with Jackson instead of being concatenated by hand: a project description
    holding a quote, an apostrophe, a backslash or an accented character used to produce an invalid JSON payload.
  * Response bodies are decoded with the charset advertised by the server and fall back to UTF-8, never to the platform
    default charset.
  * The multipart upload declares UTF-8, so artifact names holding non ASCII characters are transmitted correctly.
  * Deploy hook files and the `jelastic-properties` file are read as UTF-8.
* **Sign out endpoint fixed**: it was missing the `/1.0` API version prefix and always failed.
* **The `<comment>` parameter is now used.** It was declared but never read; only the `jelastic-comment` system property
  was.
* **Proxy selection fixed**: the first proxy of `settings.xml` was returned whether it was active or not, and
  `nonProxyHosts` was ignored.
* Deploy output no longer assumes a single node response.
* Clean up of obsolete archives no longer runs in a fire and forget thread that could outlive the build.

### Security

* **TLS certificates are validated again.** The plugin used to trust every certificate and accept any hostname, which
  exposed the credentials and the session to interception. Set `<trustAllCertificates>true</trustAllCertificates>` to
  restore the previous behaviour on a private platform using a self signed certificate.

### Changed

* **The groupId is now `io.github.tszadel`** (was `com.jelastic`), so that the fork can be published on its own.
  Update the plugin declaration in your pom accordingly.
* **Published to GitHub Packages** at `https://maven.pkg.github.com/tszadel/jelastic-maven-plugin`; pushing a `v*` tag
  builds, publishes and creates the matching GitHub release. Reading a package from GitHub requires an authenticated
  `settings.xml`, see the README.
* Requires Java 21 and Maven 3.9 (was Java 5 and the Maven 2.2.1 API).
* Goals are declared with Maven plugin annotations instead of javadoc tags.
* Jackson 1.8.1 (`org.codehaus.jackson`, end of life) replaced with Jackson 2.18.2.
* HttpClient calls migrated off the deprecated `DefaultHttpClient`, `MultipartEntity` and `URIUtils` APIs;
  `commons-fileupload` and `plexus-utils`, which were not used, are gone.
* Connection and read timeouts are configurable; requests no longer hang forever.
* Upload progress is logged every 10% instead of every 1%.
* New `skip`, `uploadOnly`, `trustAllCertificates`, `connectTimeoutSeconds` and `socketTimeoutSeconds` parameters, plus
  `jelastic.*` user properties for the existing ones.
* Release profile publishes through Sonatype Central; `oss.sonatype.org` has been retired.
