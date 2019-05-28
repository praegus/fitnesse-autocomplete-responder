package nl.praegus.doclets;

import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.doclet.Reporter;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class JavadocCollectorDoclet implements Doclet {

    @Override
    public void init(Locale locale, Reporter reporter) {

    }

    public String getName() {
        return "FitNesse Fixture Javadoc Collector";
    }

    public Set<Option> getSupportedOptions() {
        Option[] options = {
                new Option() {
                    public int getArgumentCount() {
                        return 1;
                    }
                    public String getDescription() {
                        return "Collect JavaDoc for fixture Methods";
                    }
                    public Option.Kind getKind() {
                        return Option.Kind.STANDARD;
                    }
                    public List<String> getNames() {
                        return Arrays.asList("option","javadoc");
                    }
                    public String getParameters() {
                        return "";
                    }
                    public boolean matches(String option) {
                        String opt = option.startsWith("-") ? option.substring(1) : option;
                        return getName().equals(opt);
                    }

                    @Override
                    public boolean process(String option, List<String> arguments) {
                        return true;
                    }

                }
        };
        return new HashSet<>(Arrays.asList(options));
    }

    public SourceVersion getSourceVersion() {
        // support the latest release
        return SourceVersion.latest();
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.RELEASE_8;
    }

    @Override
    public boolean run(DocletEnvironment environment) {

        for (Element t : environment.getIncludedElements()) {
            if(t.getKind().isClass()) {
                JSONObject methods = new JSONObject();
                for (Element e : t.getEnclosedElements()) {

                    JSONObject method = new JSONObject();
                    JSONArray params = new JSONArray();

                    DocCommentTree docCommentTree = environment.getDocTrees().getDocCommentTree(e);
                    if (docCommentTree != null) {
                        method.put("body", docCommentTree.getFullBody().toString());
                        for (DocTree tag : docCommentTree.getBlockTags()) {
                            String tagStr = tag.toString();
                            if (tagStr.startsWith("@param")) {
                                params.put(tagStr.substring(7).replaceFirst(" ", ": "));
                            } else if (tagStr.startsWith("@return")) {
                                method.put("return", tagStr.substring(8));
                            } else if (tagStr.startsWith("@throws")) {
                                method.put("throws", tagStr.substring(8));
                            } else if (tagStr.startsWith("@deprecated")) {
                                method.put("body", tagStr);
                            }
                        }
                        method.put("params", params);
                    }
                    methods.put(e.getSimpleName().toString(), method);
                }
                saveDocJson(t.toString(), methods);
            }
        }
        return true;
    }

    private static void saveDocJson(String className, JSONObject docs) {
        try (FileWriter file = new FileWriter(className + ".json")) {
            file.write(docs.toString(4));
            file.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}