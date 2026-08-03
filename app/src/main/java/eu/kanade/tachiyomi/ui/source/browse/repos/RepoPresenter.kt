package eu.kanade.tachiyomi.ui.source.browse.repos

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.base.presenter.BaseCoroutinePresenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Presenter of [RepoController]. Used to manage the repos for the extensions.
 */
class RepoPresenter(
    private val controller: RepoController,
    private val preferences: PreferencesHelper = Injekt.get(),
) : BaseCoroutinePresenter<RepoController>() {
    private var scope = CoroutineScope(Job() + Dispatchers.Default)

    /**
     * List containing repos.
     */
    /**
     * List containing repos.
     */
    private var repos: Set<String>
        get() =
            preferences
                .extensionRepos()
                .get()
                .map { normalizeRepoUrl(it) }
                .sorted()
                .toSet()
        set(value) = preferences.extensionRepos().set(value.map { normalizeRepoUrl(it) }.toSet())

    /**
     * Called when the presenter is created.
     */
    fun getRepos() {
        scope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                controller.updateRepos()
            }
        }
    }

    fun getReposWithCreate(): List<RepoItem> = (listOf(CREATE_REPO_ITEM) + repos).map(::RepoItem)

    fun getRepoUrl(repo: String): String =
        githubRepoRegex
            .find(repo)
            ?.let {
                val (user, repoName) = it.destructured
                "https://github.com/$user/$repoName"
            } ?: repo

    /**
     * Creates and adds a new repo to the database.
     *
     * @param name The name of the repo to create.
     */
    fun createRepo(name: String): Boolean {
        val normalized = normalizeRepoUrl(name)
        if (isInvalidRepo(normalized)) return false

        // Do not allow duplicate repos.
        if (repoExists(normalized)) {
            controller.onRepoExistsError()
            return true
        }

        repos += normalized
        controller.updateRepos()
        return true
    }

    /**
     * Deletes the repo from the database.
     *
     * @param repo The repo to delete.
     */
    fun deleteRepo(repo: String?) {
        val safeRepo = repo ?: return
        val normalized = normalizeRepoUrl(safeRepo)
        repos = repos.filterNot { it.equals(normalized, ignoreCase = true) || it.equals(safeRepo, ignoreCase = true) }.toSet()
        controller.updateRepos()
    }

    /**
     * Renames a repo.
     *
     * @param repo The repo to rename.
     * @param name The new name of the repo.
     */
    fun renameRepo(
        repo: String,
        name: String,
    ): Boolean {
        val normalizedName = normalizeRepoUrl(name)
        if (!repo.equals(normalizedName, true)) {
            if (isInvalidRepo(normalizedName)) return false
            val currentRepos = repos.filterNot { it.equals(repo, ignoreCase = true) }.toSet()
            repos = currentRepos + normalizedName
            controller.updateRepos()
        }
        return true
    }

    private fun isInvalidRepo(name: String): Boolean {
        // Do not allow invalid formats
        if (!name.matches(repoRegex)) {
            controller.onRepoInvalidNameError()
            return true
        }
        return false
    }

    /**
     * Returns true if a repo with the given name already exists.
     */
    private fun repoExists(name: String): Boolean = repos.any { it.equals(name, true) }

    companion object {
        private val repoRegex = "^https?://.+$".toRegex()
        private val githubRepoRegex = "https://(?:raw.githubusercontent.com|github.com)/(.+?)/(.+?)/.+".toRegex()
        const val CREATE_REPO_ITEM = "create_repo"

        fun normalizeRepoUrl(url: String): String {
            var trimmed = url.trim().trimEnd('/')
            if (trimmed.contains("github.com/keiyoushi/extensions-source") ||
                trimmed.contains("github.com/keiyoushi/extensions") ||
                trimmed.contains("raw.githubusercontent.com/keiyoushi/extensions") ||
                trimmed.contains("keiyoushi.github.io/extensions")
            ) {
                return "https://raw.githubusercontent.com/keiyoushi/extensions/repo"
            }
            val ghMatch = "^https://github\\.com/([^/]+)/([^/]+)/?".toRegex().find(trimmed)
            if (ghMatch != null) {
                val (user, repo) = ghMatch.destructured
                val cleanRepo = repo.removeSuffix(".git")
                return "https://raw.githubusercontent.com/$user/$cleanRepo/repo"
            }
            return trimmed
        }
    }
}
