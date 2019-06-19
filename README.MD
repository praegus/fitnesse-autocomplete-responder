 [![Maven Central](https://img.shields.io/maven-central/v/nl.praegus/fitnesse-autocomplete-responder.svg?maxAge=21600)](https://mvnrepository.com/artifact/com.github.tcnh/fitnesse-autocomplete-responder)
# Autocomplete responder
This responder returns a json containing all imported classes in a fitnesse context and their public methods and parameter types.
Also returns scenario's on page if these are present. Scenario's are de-camelcased and parameters are also provided.

This json should be usable as a basis for context-aware autocompletion in the wiki editor.

When the jar is in your fitnesse plugins directory, add `Responders=autoComplete:com.github.tcnh.fitnesse.responders.AutoCompleteResponder` to your plugins.properties file.

For more details on usage and an implementation: see the readme in [Bootstrap-plus-theme repository](https://github.com/tcnh/fitnesse-bootstrap-plus-theme)

