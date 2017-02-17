package nl.tcnh.fitnesse.responders.util;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Class to find classes by package, from fileSystem or JAR Based on code from https://dzone.com/articles/get-all-classes-within-package and
 * http://stackoverflow.com/questions/11016092/how-to-load-classes-at-runtime-from-a-folder-or-jar *
 */
public class ClassFinder {

    /**
     * Scans all classes accessible from the context class loader which belong to the given package and subpackages.
     *
     * @param packageName The base package
     * @return The classes
     * @throws ClassNotFoundException
     * @throws IOException
     */

    public static List<Class> getClasses(String packageName, boolean recursive)
            throws ClassNotFoundException, IOException {

        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        assert classLoader != null;
        String path = packageName.replace('.', '/');
        Enumeration resources = classLoader.getResources(path);
        List<File> dirs = new ArrayList<>();

        while (resources.hasMoreElements()) {
            URL resource = (URL) resources.nextElement();
            dirs.add(new File(resource.getFile()));
        }
        List<Class> classes = new ArrayList<>();
        for (File directory : dirs) {
            classes.addAll(findClasses(directory, packageName, recursive));
        }
        return classes;
    }

    /**
     * Recursive method used to find all classes in a given directory and subdirs.
     *
     * @param directory   The base directory
     * @param packageName The package name for classes found inside the base directory
     * @return The classes
     * @throws ClassNotFoundException
     */
    public static List<Class> findClasses(File directory, String packageName, boolean recursive) throws ClassNotFoundException {
        List<Class> classes = new ArrayList<>();
        if (!directory.exists()) {
            if (directory.getPath().contains(".jar")) {
                String jarFile = directory.getPath().split("!")[0].replace("file:\\", "");
                classes.addAll(getClassesFromJar(jarFile, packageName));
            }
            return classes;
        }

        File[] files = directory.listFiles();
        for (File file : files) {
            if (file.isDirectory()) {
                if (recursive) {
                    assert !file.getName().contains(".");
                    classes.addAll(findClasses(file, packageName + "." + file.getName(), recursive));
                }
            } else if (file.getName().endsWith(".class") && !file.getName().contains("$")) {
                classes.add(Class.forName(packageName + '.' + file.getName().substring(0, file.getName().length() - 6)));
            }
        }
        return classes;
    }

    private static List<Class> getClassesFromJar(String jar, String pkg) {
        List<Class> jarClasses = new ArrayList<>();
        try {
            JarFile jarFile = new JarFile(jar);
            Enumeration<JarEntry> e = jarFile.entries();
            URL[] urls = {new URL("jar:file:" + jar + "!/")};
            URLClassLoader cl = URLClassLoader.newInstance(urls);

            while (e.hasMoreElements()) {
                JarEntry je = e.nextElement();
                if (je.isDirectory() || !je.getName().endsWith(".class") || je.getName().contains("$")) {
                    continue;
                }
                String className = je.getName().substring(0, je.getName().length() - 6);
                className = className.replace('/', '.');
                try {
                    if(className.contains(pkg)) {
                    Class c = cl.loadClass(className);
                    jarClasses.add(c);
                    }
                } catch (ClassNotFoundException ex) {
                    System.err.println("Class not found: " + ex.getMessage());
                }
            }

        } catch (IOException e) {
            System.err.println("IOException: " + e);
        }
        return jarClasses;
    }


}