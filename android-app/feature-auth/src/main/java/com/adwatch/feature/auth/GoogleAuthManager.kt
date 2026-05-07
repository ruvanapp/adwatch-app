package com.adwatch.feature.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleAuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun getIdToken(activity: Activity): String {
        val resourceId = context.resources.getIdentifier(
            "default_web_client_id",
            "string",
            context.packageName
        )
        require(resourceId != 0) { "Missing default_web_client_id" }
        val serverClientId = context.getString(resourceId)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(serverClientId)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val credentialManager = CredentialManager.create(context)
        val result = try {
            credentialManager.getCredential(
                context = activity,
                request = request
            )
        } catch (exception: GetCredentialException) {
            throw IllegalStateException(exception.message ?: "Google sign-in failed")
        }

        val credential = result.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            try {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                return googleIdTokenCredential.idToken
            } catch (exception: GoogleIdTokenParsingException) {
                throw IllegalStateException("Unable to parse Google credentials")
            }
        }

        throw IllegalStateException("Google sign-in was cancelled")
    }
}