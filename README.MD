# Autocomplete responder
 [![Maven Central](https://img.shields.io/maven-central/v/com.github.tcnh/fitnesse-autocomplete-responder.svg?maxAge=86400)](https://mvnrepository.com/artifact/com.github.tcnh/fitnesse-autocomplete-responder)
This responder returns a json containing all imported classes in a fitnesse context and their public methods and parameter types.
Also returns scenario's on page if these are present. Scenario's are de-camelcased and parameters are also provided.

This json should be usable as a basis for context-aware autocompletion in the wiki editor.

