package outreach;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

/**
 * Example project for Outreach platform consumers.
 */
public class Outreach {
    /** Authorization access credential, this is returned from redirected authorization requests from api.outreach.io/oauth/authorize. */
    private final String authorization_code;

    /** Credentials representing the application, namely the application identifier, secret, and other metadata */
    private final ApplicationCredentials application_credentials;

    /** Request access bearer-credential, this short-lived credential is returned from api.outreach.io/oauth/token. */
    private String request_bearer = null;

    /** Refresh token used to generate a new bearer token after the original authorization code has been consumed. */
    private String refresh_bearer = null;

    public Outreach(final ApplicationCredentials app_creds, final String authorize_code) {
    	this.application_credentials = app_creds;
    	this.authorization_code = authorize_code;
    }

    /**
     * Allows adding a single prospect for the associated account to the local bearer credential.
     * @param prospect the JSONObject API-formatted request containing the prospect to be created, this should ideally be wrapped in a domain object.
     * @return a JSONObject blob of the response, containing the created prospect identifier and creation/update timestamps.
     */
    public JSONObject addProspect(final String prospect) {
        try {
            // Refresh access token on each request, the first request will use the authorization
            // code and subsequent requests will use the refresh token from the initial exchange.
            this.fetchAccessToken();

            final HttpURLConnection connection = (HttpURLConnection) new URL("https://api.outreach.io/v1/prospect").openConnection();

            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Authorization", new String("Bearer " + this.request_bearer));
            connection.setRequestProperty("Content-Type", "application/json");

            try (OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream())) {
                writer.write(prospect);
            }

            System.out.println("reason: " + connection.getHeaderField("reason")); // TODO: Remove

            final JSONObject response;
            try (BufferedReader readStream = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                response = (JSONObject) JSONValue.parse(readStream);
            }

            return response;
        } catch (IOException | IllegalStateException | NullPointerException e) {
            System.out.println(e);
        }

        return null;
    }

    /**
     * Creates a new bearer token associated with this instance, this must be done
     * before any calls to the API can be made. This is automatically called on all
     * API requests, which is somewhat overkill since the access token should be
     * valid for a period of time (TTL is specified in the response payload below).
     * <br /><br />
     * <b>NOTE</b>: Authorization codes will only grant a single bearer token which
     * expires after timeout or use; to generate a new one either get a new authorize
     * code or use the refresh token in the response (change: &code and &grant_type).
     */
    private void fetchAccessToken() {
        try {
            final HttpURLConnection connection = (HttpURLConnection) new URL("https://api.outreach.io/oauth/token").openConnection();

            connection.setDoOutput(true);
            connection.setRequestMethod("POST");

            // Use a refresh token if one was previously provided.
            final String _token;
            if (this.refresh_bearer != null) {
                _token = "&grant_type=refresh_token&refresh_token=" + this.refresh_bearer;
            } else {
                _token = "&grant_type=authorization_code&code=" + this.authorization_code;
            }

            try (OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream())) {
                writer.write("client_id=" + this.application_credentials.APP_IDENTIFIER
                             + "&client_secret=" + this.application_credentials.APP_SECRET_KEY
                             + "&redirect_uri=" + this.application_credentials.APP_RETURN_URI
                             + _token);
            }

            if (connection.getResponseCode() == 401) {
                System.out.println("Server returned unauthorized response, verify that the authorize_code hasn't already been used.");
            }

            final JSONObject response;
            try (BufferedReader readStream = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                response = (JSONObject) JSONValue.parse(readStream);
            }

            this.request_bearer = response.get("access_token").toString();
            this.refresh_bearer = response.get("refresh_token").toString();

            return;
        } catch (IOException | IllegalStateException | NullPointerException e) {
            System.out.println(e);
        }
    }

    public static class ApplicationCredentials {
        /** Application credentials, these are generated when a client application is provisioned in api.outreach.io/oauth/applications. */
        protected final String APP_IDENTIFIER, APP_SECRET_KEY, APP_RETURN_URI;

        public ApplicationCredentials(final String app_identifier, final String app_secret_key, final String app_return_uri) {
        	this.APP_IDENTIFIER = app_identifier;
        	this.APP_SECRET_KEY = app_secret_key;
        	this.APP_RETURN_URI = app_return_uri;
        }
    }
}