[![Maven Central](https://img.shields.io/maven-central/v/nl.praegus/fitnesse-autocomplete-responder.svg?maxAge=21600)](https://mvnrepository.com/artifact/nl.praegus/fitnesse-autocomplete-responder)
# Autocomplete responder
This responder returns a json containing all imported classes in a fitnesse context and their public methods and parameter types.
Also returns scenario's on page if these are present. Scenario's are de-camelcased and parameters are also provided.

This json is usable as a basis for context-aware autocompletion in the wiki editor.

### Usage
The easiest way: use toolchain-fitnesse-plugin. This incorporates bootstra-plus and autocmplete-responder and does all the configuration automagically.

Otherwise:
When the jar is in your FitNesse plugins directory, add `Responders=autoComplete:nl.praegus.fitnesse.responders.AutoCompleteResponder` to your plugins.properties file.

For more details on usage and an implementation: see the readme in [Bootstrap-plus-theme repository](https://github.com/praegus/fitnesse-bootstrap-plus-theme)

