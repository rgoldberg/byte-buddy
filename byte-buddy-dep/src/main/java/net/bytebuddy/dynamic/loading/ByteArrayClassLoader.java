package net.bytebuddy.dynamic.loading;

import net.bytebuddy.instrumentation.type.TypeDescription;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A {@link java.lang.ClassLoader} that is capable of loading explicitly defined classes. The class loader will free
 * any binary resources once a class that is defined by its binary data is loaded. This class loader is thread safe since
 * the class loading mechanics are only called from synchronized context.
 */
public class ByteArrayClassLoader extends ClassLoader {

    /**
     * A mutable map of type names mapped to their binary representation.
     */
    protected final Map<String, byte[]> typeDefinitions;

    /**
     * The persistence handler of this class loader.
     */
    protected final PersistenceHandler persistenceHandler;

    /**
     * The access control context of this class loader's instantiation.
     */
    protected final AccessControlContext accessControlContext;

    /**
     * Creates a new class loader for a given definition of classes.
     *
     * @param parent             The {@link java.lang.ClassLoader} that is the parent of this class loader.
     * @param typeDefinitions    A map of fully qualified class names pointing to their binary representations.
     * @param persistenceHandler The persistence handler of this class loader.
     */
    public ByteArrayClassLoader(ClassLoader parent,
                                Map<String, byte[]> typeDefinitions,
                                PersistenceHandler persistenceHandler) {
        super(parent);
        this.typeDefinitions = new HashMap<String, byte[]>(typeDefinitions);
        this.persistenceHandler = persistenceHandler;
        accessControlContext = AccessController.getContext();
    }

    /**
     * Creates a new class loader for a given definition of classes.
     *
     * @param parent             The {@link java.lang.ClassLoader} that is the parent of this class loader.
     * @param typeDefinitions    A map of type descriptions pointing to their binary representations.
     * @param persistenceHandler The persistence handler to be used by the created class loader.
     * @param childFirst         {@code true} if the class loader should apply child first semantics when loading
     *                           the {@code typeDefinitions}.
     * @return A corresponding class loader.
     */
    public static ClassLoader of(ClassLoader parent,
                                 Map<TypeDescription, byte[]> typeDefinitions,
                                 PersistenceHandler persistenceHandler,
                                 boolean childFirst) {
        Map<String, byte[]> rawTypeDefinitions = new HashMap<String, byte[]>(typeDefinitions.size());
        for (Map.Entry<TypeDescription, byte[]> entry : typeDefinitions.entrySet()) {
            rawTypeDefinitions.put(entry.getKey().getName(), entry.getValue());
        }
        return childFirst
                ? new ChildFirst(parent, rawTypeDefinitions, persistenceHandler)
                : new ByteArrayClassLoader(parent, rawTypeDefinitions, persistenceHandler);
    }

    /**
     * Loads a given set of class descriptions and their binary representations.
     *
     * @param classLoader        The parent class loader.
     * @param types              The raw types to load.
     * @param persistenceHandler The persistence handler of the created class loader.
     * @param childFirst         {@code true} {@code true} if the created class loader should apply child-first
     *                           semantics when loading the {@code types}.
     * @return A map of the given type descriptions pointing to their loaded representations.
     */
    public static Map<TypeDescription, Class<?>> load(ClassLoader classLoader,
                                                      Map<TypeDescription, byte[]> types,
                                                      PersistenceHandler persistenceHandler,
                                                      boolean childFirst) {
        Map<TypeDescription, Class<?>> loadedTypes = new LinkedHashMap<TypeDescription, Class<?>>(types.size());
        classLoader = ByteArrayClassLoader.of(classLoader, types, persistenceHandler, childFirst);
        for (TypeDescription typeDescription : types.keySet()) {
            try {
                loadedTypes.put(typeDescription, classLoader.loadClass(typeDescription.getName()));
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Cannot load class " + typeDescription, e);
            }
        }
        return loadedTypes;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        try {
            // This does not need synchronization because this method is only called from within
            // ClassLoader in a synchronized context.
            return AccessController.doPrivileged(new ClassLoadingAction(name), accessControlContext);
        } catch (PrivilegedActionException e) {
            throw (ClassNotFoundException) e.getCause();
        }
    }

    /**
     * A class loading action is responsible to perform the loading of a class in a privileged security context.
     */
    private class ClassLoadingAction implements PrivilegedExceptionAction<Class<?>> {

        /**
         * The name of the type to be loaded.
         */
        private final String name;

        /**
         * Creates a new class loading action.
         *
         * @param name The name of the type to be loaded.
         */
        private ClassLoadingAction(String name) {
            this.name = name;
        }

        @Override
        public Class<?> run() throws ClassNotFoundException {
            byte[] javaType = persistenceHandler.lookup(name, typeDefinitions);
            if (javaType != null) {
                return defineClass(name, javaType, 0, javaType.length);
            }
            throw new ClassNotFoundException(name);
        }

        @Override
        public String toString() {
            return "ByteArrayClassLoader.ClassLoadingAction{name='" + name + '\'' + '}';
        }
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        InputStream inputStream = super.getResourceAsStream(name);
        if (inputStream != null) {
            return inputStream;
        } else {
            return persistenceHandler.inputStream(name, typeDefinitions);
        }
    }

    @Override
    public String toString() {
        return "ByteArrayClassLoader{" +
                "parent=" + getParent() +
                ", typeDefinitions=" + typeDefinitions +
                ", persistenceHandler=" + persistenceHandler +
                ", accessControlContext=" + accessControlContext +
                '}';
    }

    /**
     * A persistence handler decides on weather the byte array that represents a loaded class is exposed by
     * the {@link java.lang.ClassLoader#getResourceAsStream(String)} method.
     */
    public static enum PersistenceHandler {

        /**
         * The manifest persistence handler retains all class file representations and makes them accessible.
         */
        MANIFEST {
            @Override
            protected byte[] lookup(String name, Map<String, byte[]> typeDefinitions) {
                return typeDefinitions.get(name);
            }

            @Override
            protected InputStream inputStream(String name, Map<String, byte[]> typeDefinitions) {
                byte[] binaryRepresentation = typeDefinitions.get(name);
                return binaryRepresentation == null
                        ? null
                        : new ByteArrayInputStream(binaryRepresentation);
            }
        },

        /**
         * The latent persistence handler hides all class file representations and does not make them accessible
         * even before they are loaded.
         */
        LATENT {
            @Override
            protected byte[] lookup(String name, Map<String, byte[]> typeDefinitions) {
                return typeDefinitions.remove(name);
            }

            @Override
            protected InputStream inputStream(String name, Map<String, byte[]> typeDefinitions) {
                return null;
            }
        };

        /**
         * Performs a lookup of a class file by its name.
         *
         * @param name            The name of the class to be loaded.
         * @param typeDefinitions A map of fully qualified class names pointing to their binary representations.
         * @return The byte array representing the requested class or {@code null} if no such class is known.
         */
        protected abstract byte[] lookup(String name, Map<String, byte[]> typeDefinitions);

        /**
         * Performs a lookup of an input stream for exposing a class file as a resource.
         *
         * @param name            The name of the class to be exposed as its class file.
         * @param typeDefinitions A map of fully qualified class names pointing to their binary representations.
         * @return An input stream representing the requested resource or {@code null} if no such resource is known.
         */
        protected abstract InputStream inputStream(String name, Map<String, byte[]> typeDefinitions);
    }

    /**
     * A {@link net.bytebuddy.dynamic.loading.ByteArrayClassLoader} which applies child-first semantics for the
     * given type definitions.
     */
    public static class ChildFirst extends ByteArrayClassLoader {

        /**
         * Creates a new child-first byte array class loader.
         *
         * @param parent             The {@link java.lang.ClassLoader} that is the parent of this class loader.
         * @param typeDefinitions    A map of fully qualified class names pointing to their binary representations.
         * @param persistenceHandler The persistence handler of this class loader.
         */
        public ChildFirst(ClassLoader parent,
                          Map<String, byte[]> typeDefinitions,
                          PersistenceHandler persistenceHandler) {
            super(parent, typeDefinitions, persistenceHandler);
        }

        @Override
        protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            Class<?> type = findLoadedClass(name);
            if (type != null) {
                return type;
            }
            try {
                type = findClass(name);
                if (resolve) {
                    resolveClass(type);
                }
                return type;
            } catch (ClassNotFoundException e) {
                // If an unknown class is loaded, this implementation causes the findClass method of this instance
                // to be triggered twice. This is however of minor importance because this would result in a
                // ClassNotFoundException which is rather uncommon.
                return super.loadClass(name, resolve);
            }
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            InputStream inputStream = persistenceHandler.inputStream(name, typeDefinitions);
            if (inputStream != null) {
                return inputStream;
            } else {
                URL url = getResource(name);
                try {
                    return url != null ? url.openStream() : null;
                } catch (IOException ignored) {
                    return null;
                }
            }
        }

        @Override
        public String toString() {
            return "ByteArrayClassLoader.ChildFirst{" +
                    "parent=" + getParent() +
                    ", typeDefinitions=" + typeDefinitions +
                    ", persistenceHandler=" + persistenceHandler +
                    ", accessControlContext=" + accessControlContext +
                    '}';
        }
    }
}
