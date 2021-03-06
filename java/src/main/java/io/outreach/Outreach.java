package io.outreach;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import javax.net.ssl.HttpsURLConnection;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import io.outreach.exception.OutreachSecurityException;
import io.outreach.security.TrustedHostnameVerifier;
import io.outreach.security.TrustedSSLSocketFactory;

/**
 * Example project for Outreach platform consumers.
 */
public class Outreach {

    /**
     * Authorization access credential, this is returned from redirected
     * authorization requests from api.outreach.io/oauth/authorize.
     */
    private final String authorizationCode;

    /**
     * Credentials representing the application, namely the application
     * identifier, secret, and other metadata
     */
    private final ApplicationCredentials applicationCredentials;

    /**
     * Request access bearer-credential, this short-lived credential is returned
     * from api.outreach.io/oauth/token.
     */
    private String requestBearer = null;

    /**
     * Refresh token used to generate a new bearer token after the original
     * authorization code has been consumed.
     */
    private String refreshBearer = null;
    
    private long accessExpires = 0;

    private final KeyStore trustStore;
    private String apiEndpoint = "";
    private String authEndpoint = "";

    public Outreach(final ApplicationCredentials applicationCredentials, final String authorizationCode) {
        this(applicationCredentials, authorizationCode, null);
    }

    public Outreach(final ApplicationCredentials applicationCredentials,
                    final String authorizationCode,
                    final KeyStore trustStore) {
        this.applicationCredentials = applicationCredentials;
        this.authorizationCode = authorizationCode;
        this.trustStore = trustStore;
        
        try (FileInputStream propertiesFile = new FileInputStream("src/main/resources/api.properties")) {
            Properties apiProperties = new Properties();
            apiProperties.load(propertiesFile);

            this.authEndpoint = apiProperties.getProperty("endpoint");
            this.apiEndpoint = this.authEndpoint + "/" + apiProperties.getProperty("version");
        } catch (IOException e) {
            return;
        }
    }

    /**
     * Allows adding a single prospect for the associated account to the local
     * bearer credential.
     * 
     * @param prospectId
     * @param prospectAttributes
     *            the JSONObject API-formatted request containing the prospect
     *            attributes to be modified.
     * @return a JSONObject blob of the response, containing the prospect metadata.
     */
    public JSONObject modifyProspect(final int prospectId, final String prospectAttributes) {
        try {
            // Refresh access token on each request, the first request will use the authorization
            // code and subsequent requests will use the refresh token from the initial exchange.
            this.fetchAccessToken();

            final HttpsURLConnection connection = authorizedConnection("POST", this.apiEndpoint + "/prospects/" + prospectId);

            connection.setDoOutput(true);
            connection.setRequestProperty("X-HTTP-Method-Override", "PATCH"); // Patch isn't supported in Java's HTTPConnection

            try (OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream())) {
                writer.write(prospectAttributes);
            }

            final JSONObject response;
            try (BufferedReader readStream = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                response = (JSONObject) JSONValue.parse(readStream);
            }

            return response;
        } catch (Throwable throwable) {
            throw new OutreachSecurityException(throwable);
        }
    }
    
    /**
     * Allows adding a single prospect for the associated account to the local
     * bearer credential.
     *
     * @param prospect
     *            the JSONObject API-formatted request containing the prospect
     *            to be created, this should ideally be wrapped in a domain
     *            object.
     * @return a JSONObject blob of the response, containing the created
     *         prospect identifier and creation/update timestamps.
     */
    public JSONObject addProspect(final String prospect) {
        try {
            // Refresh access token on each request, the first request will use the authorization
            // code and subsequent requests will use the refresh token from the initial exchange.
            this.fetchAccessToken();

            final HttpsURLConnection connection = authorizedConnection("POST", this.apiEndpoint + "/prospects");

            connection.setDoOutput(true);

            try (OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream())) {
                writer.write(prospect);
            }

            final JSONObject response;
            try (BufferedReader readStream = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                response = (JSONObject) JSONValue.parse(readStream);
            }

            return response;
        } catch (Throwable throwable) {
            throw new OutreachSecurityException(throwable);
        }
    }

    /**
     * Allows fetching a single prospect given it's identifier
     *
     * @param prospectId
     * @return a JSONObject blob of the response, containing the prospects.
     */
    public JSONObject getProspect(final int prospectId) {
        try {
            // Refresh access token on each request, the first request will use the authorization
            // code and subsequent requests will use the refresh token from the initial exchange.
            this.fetchAccessToken();

            final HttpsURLConnection connection = authorizedConnection("GET", this.apiEndpoint + "/prospects/" + prospectId);

            final JSONObject response;
            try (BufferedReader readStream = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                response = (JSONObject) JSONValue.parse(readStream);
            }

            return response;
        } catch (Throwable throwable) {
            throw new OutreachSecurityException(throwable);
        }
    }

    /**
     * Allows fetching a set of prospects given various query filters.
     *
     * @param firstName
     * @param lastName
     * @param companyName
     * @param email
     * @param page
     * @return a JSONObject blob of the response, containing the prospects.
     */
    public JSONObject getProspects(final String firstName, final String lastName, final String companyName, final String email, final Integer page) {
        try {
            // Refresh access token on each request, the first request will use the authorization
            // code and subsequent requests will use the refresh token from the initial exchange.
            this.fetchAccessToken();

            String query = "/prospects?";

            // TODO: Fix this with something less aweful.
            if (page != null) {
                query += "page[number]=" + page.toString() + "&";
            }
            if (firstName != null) {
                query += "filter[personal/name/first]=" + firstName + "&";
            }
            if (lastName != null) {
                query += "filter[personal/name/last]=" + lastName + "&";
            }
            if (email != null) {
                query += "filter[contact/email]=" + email.replaceAll("@", "%40") + "&"; // Ghetto URI encoding
            }
            if (companyName != null) {
                query += "filter[company/name]=" + companyName + "&";
            }

            query = query.substring(0, query.length() - 1); // last character will always be a superfluous ? or &

            final HttpsURLConnection connection = authorizedConnection("GET", this.apiEndpoint + query);

            final JSONObject response;
            try (BufferedReader readStream = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                response = (JSONObject) JSONValue.parse(readStream);
            }

            return response;
        } catch (Throwable throwable) {
            throw new OutreachSecurityException(throwable);
        }
    }
    
    /**
     * Allows fetching a list of sequences, sorted by name in ascending order.
     *
     * @param page
     * @return a JSONObject blob of the response, containing the prospects.
     */
    public JSONObject getSequences(final int page) {
        try {
            // Refresh access token on each request, the first request will use the authorization
            // code and subsequent requests will use the refresh token from the initial exchange.
            this.fetchAccessToken();

            final HttpsURLConnection connection = authorizedConnection("GET", this.apiEndpoint + "/sequences?page[number]=" + page);

            final JSONObject response;
            try (BufferedReader readStream = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                response = (JSONObject) JSONValue.parse(readStream);
            }

            return response;
        } catch (Throwable throwable) {
            throw new OutreachSecurityException(throwable);
        }
    }
    
    /**
     * Allows updating a sequence to additively associate a set of prospects.
     * 
     * @param sequenceId
     * @param payload
     *            the JSONObject API-formatted request containing the prospects to be added.
     * @return a JSONObject blob of the response, containing the batch metadata.
     */
    public JSONObject addProspectsToSequence(final int sequenceId, final String payload) {
        try {
            // Refresh access token on each request, the first request will use the authorization
            // code and subsequent requests will use the refresh token from the initial exchange.
            this.fetchAccessToken();

            final HttpsURLConnection connection = authorizedConnection("POST", this.apiEndpoint + "/sequences/" + sequenceId);

            connection.setDoOutput(true);
            connection.setRequestProperty("X-HTTP-Method-Override", "PATCH"); // Patch isn't supported in Java's HTTPConnection

            try (OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream())) {
                writer.write(payload);
            }

            final JSONObject response;
            try (BufferedReader readStream = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                response = (JSONObject) JSONValue.parse(readStream);
            }

            return response;
        } catch (Throwable throwable) {
            throw new OutreachSecurityException(throwable);
        }
    }
    
    /**
     * Allows fetching metadata associated with the bound authorization token
     *
     * @return a JSONObject blob of the response, containing the user email and application metadata.
     */
    public JSONObject getInfo() {
        try {
            // Refresh access token on each request, the first request will use the authorization
            // code and subsequent requests will use the refresh token from the initial exchange.
            this.fetchAccessToken();

            final HttpsURLConnection connection = authorizedConnection("GET", this.apiEndpoint + "/info");

            final JSONObject response;
            try (BufferedReader readStream = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                response = (JSONObject) JSONValue.parse(readStream);
            }

            return response;
        } catch (Throwable throwable) {
            throw new OutreachSecurityException(throwable);
        }
    }

    private HttpsURLConnection connectTo(String verb, String urlString) throws IOException, NoSuchAlgorithmException, KeyManagementException {
        return connectTo(verb, new URL(urlString));
    }

    private HttpsURLConnection connectTo(String verb, URL url) throws IOException, NoSuchAlgorithmException, KeyManagementException {
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();

        if (trustStore != null) {
            connection.setSSLSocketFactory(TrustedSSLSocketFactory.get(trustStore));
            connection.setHostnameVerifier(new TrustedHostnameVerifier(trustStore));
        }
        
        connection.setRequestMethod(verb);

        return connection;
    }
    
    private HttpsURLConnection authorizedConnection(String verb, String url) throws IOException, NoSuchAlgorithmException, KeyManagementException {
    	final HttpsURLConnection connection = connectTo(verb, url);
        connection.setRequestProperty("Authorization", new String("Bearer " + this.requestBearer));
        connection.setRequestProperty("Content-Type", "application/json");
        
        return connection;
    }

    /**
     * Creates a new bearer token associated with this instance, this must be
     * done before any calls to the API can be made. This is automatically
     * called on all API requests, which is somewhat overkill since the access
     * token should be valid for a period of time (TTL is specified in the
     * response payload below). <br />
     * <br />
     * <b>NOTE</b>: Authorization codes will only grant a single bearer token
     * which expires after timeout or use; to generate a new one either get a
     * new authorize code or use the refresh token in the response (change:
     * &code and &grant_type).
     */
    private void fetchAccessToken() {
        try {
        	// If access token hasn't expired (with some leeway for clock skew) use existing access token
        	if (this.accessExpires - 1000 > System.currentTimeMillis()) {
        		return;
        	}

        	final HttpsURLConnection connection = connectTo("POST", this.authEndpoint + "/oauth/token");

            connection.setDoOutput(true);

            // Use a refresh token if one was previously provided.
            final String token;
            if (this.refreshBearer != null) {
                token = "&grant_type=refresh_token&refresh_token=" + this.refreshBearer;
            } else {
                token = "&grant_type=authorization_code&code=" + this.authorizationCode;
            }

            try (OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream())) {
                writer.write("client_id=" + this.applicationCredentials.APP_IDENTIFIER + "&client_secret="
                           + this.applicationCredentials.APP_SECRET_KEY + "&redirect_uri="
                           + this.applicationCredentials.APP_RETURN_URI + token);
            }

            if (connection.getResponseCode() == 401) {
                System.out.println("Server returned unauthorized response, verify that the authorize_code hasn't already been used.");
            }

            final JSONObject response;
            try (BufferedReader readStream = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                response = (JSONObject) JSONValue.parse(readStream);
            }

            this.requestBearer = response.get("access_token").toString();
            this.refreshBearer = response.get("refresh_token").toString();
            // Expires_in is defined in seconds
            this.accessExpires = System.currentTimeMillis() + (Long.parseLong(response.get("expires_in").toString()) * 1000);
        } catch (Throwable throwable) {
            throw new OutreachSecurityException(throwable);
        }
    }

    public static class ApplicationCredentials {
        /**
         * Application credentials, these are generated when a client
         * application is provisioned in api.outreach.io/oauth/applications.
         */
        protected final String APP_IDENTIFIER, APP_SECRET_KEY, APP_RETURN_URI;

        public ApplicationCredentials(final String app_identifier, final String app_secret_key,
                                      final String app_return_uri) {
            this.APP_IDENTIFIER = app_identifier;
            this.APP_SECRET_KEY = app_secret_key;
            this.APP_RETURN_URI = app_return_uri;
        }
    }
}
