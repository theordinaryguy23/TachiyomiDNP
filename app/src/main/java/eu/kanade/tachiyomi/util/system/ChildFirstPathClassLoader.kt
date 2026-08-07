package eu.kanade.tachiyomi.util.system

import dalvik.system.PathClassLoader
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.util.Enumeration

/**
 * A parent-last class loader that will try in order:
 * - the system class loader
 * - the child class loader (extension DEX)
 * - the parent class loader (host app).
 *
 * This prevents class conflicts when an extension bundles its own version of a
 * library that also exists in the host app, while still allowing framework
 * classes and host-provided extension-API classes to resolve via parent.
 */
class ChildFirstPathClassLoader(
    dexPath: String,
    librarySearchPath: String?,
    parent: ClassLoader,
) : PathClassLoader(dexPath, librarySearchPath, parent) {

    private val systemClassLoader: ClassLoader? = getSystemClassLoader()

    override fun loadClass(name: String?, resolve: Boolean): Class<*> {
        var c = findLoadedClass(name)
        if (c == null && systemClassLoader != null) {
            try {
                c = systemClassLoader.loadClass(name)
            } catch (_: ClassNotFoundException) {}
        }
        if (c == null) {
            c = try {
                findClass(name)
            } catch (_: ClassNotFoundException) {
                super.loadClass(name, resolve)
            }
        }
        if (resolve) {
            resolveClass(c)
        }
        return c
    }

    override fun getResource(name: String?): URL? {
        return systemClassLoader?.getResource(name)
            ?: findResource(name)
            ?: super.getResource(name)
    }

    override fun getResources(name: String?): Enumeration<URL> {
        val systemUrls = systemClassLoader?.getResources(name)
        val localUrls = findResources(name)
        val parentUrls = parent?.getResources(name)
        val urls = buildList {
            while (systemUrls?.hasMoreElements() == true) {
                add(systemUrls.nextElement())
            }
            while (localUrls?.hasMoreElements() == true) {
                add(localUrls.nextElement())
            }
            while (parentUrls?.hasMoreElements() == true) {
                add(parentUrls.nextElement())
            }
        }
        return object : Enumeration<URL> {
            val iterator = urls.iterator()
            override fun hasMoreElements() = iterator.hasNext()
            override fun nextElement() = iterator.next()
        }
    }

    override fun getResourceAsStream(name: String?): InputStream? {
        return try {
            getResource(name)?.openStream()
        } catch (_: IOException) {
            return null
        }
    }
}

/**
 * Convenience constructor that accepts a nullable parent (defaults to system class loader).
 * Usage: ChildFirstPathClassLoader(appInfo.sourceDir, parent = context.classLoader)
 */
fun ChildFirstPathClassLoader(classPath: String, parent: ClassLoader? = null): ChildFirstPathClassLoader =
    ChildFirstPathClassLoader(classPath, null, parent ?: ClassLoader.getSystemClassLoader())
