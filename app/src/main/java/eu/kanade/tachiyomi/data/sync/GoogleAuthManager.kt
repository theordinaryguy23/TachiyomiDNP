package eu.kanade.tachiyomi.data.sync

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/**
 * Manages Google Sign-In and Firebase authentication for history sync.
 */
class GoogleAuthManager(private val context: Context) {

    companion object {
        private const val RC_SIGN_IN = 9001
    }

    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()

    private val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken("602437167181-31854fe5851f25a88a5404.apps.googleusercontent.com")
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    /**
     * Returns the current Firebase user, or null if not signed in.
     */
    fun getCurrentUser(): FirebaseUser? = firebaseAuth.currentUser

    /**
     * Returns the Google Sign-In account, or null if not signed in.
     */
    fun getGoogleAccount(): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context)

    /**
     * Checks if the user is signed in via Google.
     */
    fun isSignedIn(): Boolean = getCurrentUser() != null

    /**
     * Gets the user ID for Firestore. Returns null if not signed in.
     */
    fun getUserId(): String? = getCurrentUser()?.uid

    /**
     * Signs in with a Google account and links to Firebase Auth.
     * @return Result with the FirebaseUser on success, or exception on failure.
     */
    suspend fun signInWithGoogle(account: GoogleSignInAccount): Result<FirebaseUser> {
        return try {
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            val user = authResult.user
            if (user != null) {
                Timber.d("Google sign-in successful: ${user.uid}")
                Result.success(user)
            } else {
                Timber.e("Google sign-in failed: user is null")
                Result.failure(Exception("Sign-in failed: user is null"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Google sign-in failed")
            Result.failure(e)
        }
    }

    /**
     * Signs out from both Google and Firebase.
     */
    suspend fun signOut() {
        try {
            googleSignInClient.signOut().await()
            firebaseAuth.signOut()
            Timber.d("Sign-out successful")
        } catch (e: Exception) {
            Timber.e(e, "Sign-out failed")
        }
    }

    /**
     * Returns the Google Sign-In client intent for launching the sign-in flow.
     */
    fun getSignInIntent() = googleSignInClient.signInIntent

    /**
     * Returns the sign-in request code.
     */
    fun getSignInRequestCode() = RC_SIGN_IN

    /**
     * Returns a user-friendly display name for the signed-in user.
     */
    fun getDisplayName(): String? = getCurrentUser()?.displayName
        ?: getCurrentUser()?.email
        ?: getGoogleAccount()?.displayName
        ?: getGoogleAccount()?.email

    /**
     * Returns the user's profile photo URL, or null.
     */
    fun getPhotoUrl(): String? = getCurrentUser()?.photoUrl?.toString()
        ?: getGoogleAccount()?.photoUrl?.toString()
}
