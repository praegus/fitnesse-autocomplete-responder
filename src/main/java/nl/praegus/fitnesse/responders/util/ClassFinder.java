package nl.praegus.fitnesse.responders.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Class to find classes by package, from fileSystem or JAR Based on code from:
 * https://dzone.com/articles/get-all-classes-within-package and
 * http://stackoverflow.com/questions/11016092/how-to-load-classes-at-runtime-from-a-folder-or-jar *
 */

public class ClassFinder {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClassFinder.class);

    public static List<Class> getClasses(String packageName, boolean recursive, URLClassLoader classLoader)
            throws ClassNotFoundException, IOException {

        String path = packageName.replace('.', '/');
        Enumeration resources = classLoader.getResources(path);
        List<File> dirs = new ArrayList<>();

        while (resources.hasMoreElements()) {
            URL resource = (URL) resources.nextElement();
            dirs.add(new File(resource.getFile()));
        }
        List<Class> classes = new ArrayList<>();
        for (File directory : dirs) {
            classes.addAll(findClasses(directory, packageName, recursive, classLoader));
        }
        return classes;
    }

    private static Set<Class> findClasses(File directory, String packageName, boolean recursive, URLClassLoader classLoader) throws ClassNotFoundException {
        Set<Class> classes = new HashSet<>();
        if (!directory.exists()) {
            if (directory.getPath().contains(".jar")) {
                String jarFile = directory.getPath().split("!")[0].replace("file:\\", "");
                jarFile = jarFile.replace("file:", "");
                classes.addAll(getClassesFromJar(jarFile, packageName, classLoader));
            }
            return classes;
        }

        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    if (recursive) {
                        assert !file.getName().contains(".");
                        classes.addAll(findClasses(file, packageName + "." + file.getName(), true, classLoader));
                    }
                } else if (file.getName().endsWith(".class") && !file.getName().contains("$")) {
                    classes.add(classLoader.loadClass(packageName + '.' + file.getName().substring(0, file.getName().length() - 6)));
                }
            }
        }
        return classes;
    }

    private static List<Class> getClassesFromJar(String jar, String pkg, URLClassLoader classLoader) {
        List<Class> jarClasses = new ArrayList<>();
        try (JarFile jarFile = new JarFile(jar)) {
            Enumeration<JarEntry> e = jarFile.entries();
            URL[] urls = {new URL("jar:file:" + jar + "!/")};
            URLClassLoader cl = new URLClassLoader(urls, classLoader);
            String pkgName;
            while (e.hasMoreElements()) {
                JarEntry je = e.nextElement();
                if (je.isDirectory() || !je.getName().endsWith(".class") || je.getName().contains("$")) {
                    continue;
                }
                String fqClassName = je.getName().substring(0, je.getName().length() - 6);
                fqClassName = fqClassName.replace('/', '.');
                if (fqClassName.lastIndexOf('.') >= 0) {
                    pkgName = fqClassName.substring(0, fqClassName.lastIndexOf('.'));
                } else {
                    pkgName = "";
                }
                if (pkgName.equals(pkg)) {
                    try {
                        Class c = cl.loadClass(fqClassName);
                        //Ignore classes without any public constructor
                        if (c.getConstructors().length > 0) {
                            jarClasses.add(c);
                        }
                    } catch (ClassNotFoundException ex) {
                        //intentionally ignore classes that cannot be found
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("IOException: " + e);
        }
        return jarClasses;
    }
}