 [![Maven Central](https://img.shields.io/maven-central/v/nl.praegus/fitnesse-autocomplete-responder.svg?maxAge=21600)](https://mvnrepository.com/artifact/com.github.tcnh/fitnesse-autocomplete-responder)
# Autocomplete responder
This responder returns a json containing all imported classes in a fitnesse context and their public methods and parameter types.
Also returns scenario's on page if these are present. Scenario's are de-camelcased and parameters are also provided.

This json should be usable as a basis for context-aware autocompletion in the wiki editor.

When the jar is in your fitnesse plugins directory, add `Responders=autoComplete:nl.praegus.fitnesse.responders.AutoCompleteResponder` to your plugins.properties file.

For more details on usage and an implementation: see the readme in [Bootstrap-plus-theme repository](https://github.com/tcnh/fitnesse-bootstrap-plus-theme)

# Javadoc CollectorDoclet
NOTE; The doclet is not compatible with Java 8. Please use JDK9 or higher to generate Fixture javadoc JSON's.

#### Usage:
Add the following plugin defnition to your projects' pom.xml. (Preferably in a profile, we don't need to refresh JavaDoc for every build)
```
<!-- Aggregate JavaDoc for use with autoCompleteResponder -->
<plugin>
    <artifactId>maven-javadoc-plugin</artifactId>
    <extensions>true</extensions>
    <executions>
        <execution>
            <phase>generate-resources</phase>
            <goals>
                <goal>aggregate</goal>
            </goals>
        </execution>
    </executions>

    <configuration>
        <doclet>nl.praegus.doclets.JavadocCollectorDoclet</doclet>
        <docletPath>${project.basedir}/wiki/plugins/toolchain-fitnesse-plugin-${toolchain-plugin.version}-jar-with-dependencies.jar</docletPath>
        <includeDependencySources>true</includeDependencySources>

        <!-- Define artefacts to aggregate (this should list your fixture dependencies!) -->
        <dependencySourceIncludes>
            <dependencySourceInclude>nl.hsac:hsac-fitnesse-fixtures</dependencySourceInclude>
            <dependencySourceInclude>nl.praegus:toolchain-fixtures</dependencySourceInclude>
        </dependencySourceIncludes>

        <!-- AutoCompleteResponder expects the documentation in wiki/fixtures -->
        <reportOutputDirectory>${project.basedir}/wiki/fixtures</reportOutputDirectory>
        <useStandardDocletOptions>false</useStandardDocletOptions>
        <doclint>none</doclint>

        <!-- internal stuff needs to be explicitly exposed from Java9 on-->
        <additionalJOption>--add-exports="jdk.scripting.nashorn/jdk.nashorn.internal.runtime=ALL-UNNAMED"</additionalJOption>
    </configuration>
</plugin>
```

