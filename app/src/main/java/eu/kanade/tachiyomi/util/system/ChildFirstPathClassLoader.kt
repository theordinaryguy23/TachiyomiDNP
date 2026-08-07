package eu.kanade.tachiyomi.util.system

import dalvik.system.PathClassLoader

/**
 * A class loader that uses child-first delegation for loading classes from the extension APK,
 * but parent delegation for framework classes and core libraries.
 *
 * This prevents class conflicts when an extension bundles its own version of a library
 * that also exists in the host app.
 *
 * @param classPath The class path of the extension APK.
 * @param librarySearchPath Optional library search path (can be null).
 * @param parent The parent class loader.
 */
class ChildFirstPathClassLoader(
    classPath: String,
    librarySearchPath: String? = null,
    parent: ClassLoader,
) : PathClassLoader(classPath, librarySearchPath, parent)

/**
 * Convenience constructor that accepts a nullable parent (defaults to system class loader).
 * Usage: ChildFirstPathClassLoader(appInfo.sourceDir, parent = context.classLoader)
 */
fun ChildFirstPathClassLoader(classPath: String, parent: ClassLoader? = null): ChildFirstPathClassLoader =
    ChildFirstPathClassLoader(classPath, null, parent ?: ClassLoader.getSystemClassLoader())