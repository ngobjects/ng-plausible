package ng.plausible;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ng.appserver.NGActionResults;
import ng.appserver.NGApplication;
import ng.appserver.http.NGRequest;
import ng.appserver.http.NGResponse;
import ng.appserver.http.NGResponses;
import ng.appserver.privates.NGParsedURI;

/**
 * The proxy itself: configuration, the cached script and event forwarding. See {@link NGPlausiblePlugin}.
 */
public class NGPlausible {

	private static final Logger logger = LoggerFactory.getLogger( NGPlausible.class );

	/**
	 * The routes live under this path: PATH_PREFIX/js serves the script, PATH_PREFIX/event takes the events
	 */
	public static final String PATH_PREFIX = "/np";

	public static final String SCRIPT_PATH = PATH_PREFIX + "/js";
	public static final String EVENT_PATH = PATH_PREFIX + "/event";

	public static final String DEFAULT_SCRIPT = "script.outbound-links.js";

	private static final Duration SCRIPT_MAX_AGE = Duration.ofHours( 1 );

	private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout( Duration.ofSeconds( 5 ) ).build();

	private static String _script;
	private static Instant _scriptFetchedAt;

	private NGPlausible() {}

	/**
	 * @return The Plausible server's URL (ng.plausible.url), without a trailing slash, or null if not configured
	 */
	public static String serverURL() {
		final String url = property( "ng.plausible.url" );
		return url == null ? null : url.endsWith( "/" ) ? url.substring( 0, url.length() - 1 ) : url;
	}

	/**
	 * @return The site's domain as registered in Plausible (ng.plausible.domain), or null if not configured
	 */
	public static String domain() {
		return property( "ng.plausible.domain" );
	}

	/**
	 * @return The script variant to serve (ng.plausible.script), e.g. script.js or script.outbound-links.js
	 */
	public static String scriptName() {
		final String script = property( "ng.plausible.script" );
		return script == null ? DEFAULT_SCRIPT : script;
	}

	public static boolean trackInDevelopment() {
		return "true".equalsIgnoreCase( property( "ng.plausible.trackInDevelopment" ) );
	}

	public static boolean isConfigured() {
		return serverURL() != null && domain() != null;
	}

	/**
	 * Properties come from the application's properties (command line or Properties resource), falling back to
	 * JVM system properties so a -D on the java command line works too.
	 */
	private static String property( final String key ) {
		final NGApplication application = NGApplication.application();
		String value = application == null ? null : application.properties().get( key );

		if( value == null ) {
			value = System.getProperty( key );
		}

		return value == null || value.isBlank() ? null : value.trim();
	}

	static void logConfiguration() {
		if( isConfigured() ) {
			logger.info( "Plausible analytics for {} through {}, proxied at {}", domain(), serverURL(), PATH_PREFIX );
		}
		else {
			logger.warn( "Plausible analytics not configured: set ng.plausible.url and ng.plausible.domain. The tag renders nothing and {} answers 404", PATH_PREFIX );
		}
	}

	/**
	 * Handles everything under PATH_PREFIX
	 */
	public static NGActionResults handle( final NGRequest request ) {
		if( !isConfigured() ) {
			return NGResponses.of( 404, "Not configured" );
		}

		final String what = NGParsedURI.of( request.uri() ).getString( 1 );

		if( "js".equals( what ) ) {
			return script();
		}

		if( "event".equals( what ) && "POST".equalsIgnoreCase( request.method() ) ) {
			return forwardEvent( request );
		}

		return NGResponses.of( 404, "Not found" );
	}

	/**
	 * Plausible's script, fetched from the server and held for an hour. If the server can't be reached and
	 * nothing is cached, an empty 204 keeps the page working: a missing script is a page without stats.
	 */
	private static synchronized NGResponse script() {
		try {
			if( _script == null || Duration.between( _scriptFetchedAt, Instant.now() ).compareTo( SCRIPT_MAX_AGE ) >= 0 ) {
				final HttpRequest upstream = HttpRequest.newBuilder( URI.create( serverURL() + "/js/" + scriptName() ) )
						.timeout( Duration.ofSeconds( 10 ) )
						.GET()
						.build();

				final HttpResponse<String> answer = CLIENT.send( upstream, HttpResponse.BodyHandlers.ofString() );

				if( answer.statusCode() == 200 ) {
					_script = answer.body();
					_scriptFetchedAt = Instant.now();
				}
				else if( _script == null ) {
					throw new IOException( "The Plausible server answered " + answer.statusCode() );
				}
			}
		}
		catch( final Exception e ) {
			logger.warn( "Could not fetch the Plausible script", e );
			return NGResponses.of( 204, "" );
		}

		final NGResponse response = NGResponses.ok( _script.getBytes( StandardCharsets.UTF_8 ) );
		response.setHeader( "Content-Type", "application/javascript; charset=utf-8" );
		response.setHeader( "Cache-Control", "public, max-age=3600" );
		return response;
	}

	/**
	 * Forwards an event to the server as the page sent it, with the visitor's user agent and address
	 */
	private static NGResponse forwardEvent( final NGRequest request ) {
		final byte[] body = request.contentBytes() == null ? new byte[0] : request.contentBytes();
		final String contentType = header( request, "Content-Type" );
		final String userAgent = header( request, "User-Agent" );
		final String clientAddress = clientAddress( request );

		try {
			final HttpRequest.Builder upstream = HttpRequest.newBuilder( URI.create( serverURL() + "/api/event" ) )
					.timeout( Duration.ofSeconds( 10 ) )
					.header( "Content-Type", contentType == null ? "text/plain" : contentType )
					.POST( HttpRequest.BodyPublishers.ofByteArray( body ) );

			if( userAgent != null ) {
				upstream.header( "User-Agent", userAgent );
			}

			if( clientAddress != null ) {
				upstream.header( "X-Forwarded-For", clientAddress );
			}

			final HttpResponse<String> answer = CLIENT.send( upstream.build(), HttpResponse.BodyHandlers.ofString() );
			final NGResponse response = NGResponses.of( answer.statusCode(), answer.body() == null ? "" : answer.body() );
			response.setHeader( "Content-Type", "text/plain; charset=utf-8" );
			return response;
		}
		catch( final Exception e ) {
			logger.warn( "Could not forward a Plausible event", e );
			final NGResponse response = NGResponses.of( 202, "ok" );
			response.setHeader( "Content-Type", "text/plain; charset=utf-8" );
			return response;
		}
	}

	/**
	 * @return The visitor's address. Behind a webserver it arrives in a header: X-Forwarded-For from a standard
	 * reverse proxy, remote_addr from a WebObjects adaptor (mod_WebObjects, modulo), X-Real-IP from nginx. Only
	 * when none is present is the connection's own address used, which is then the webserver's.
	 */
	static String clientAddress( final NGRequest request ) {
		for( final String name : new String[] { "X-Forwarded-For", "remote_addr", "X-Real-IP" } ) {
			final String value = header( request, name );

			if( value != null && !value.isBlank() ) {
				return value.split( "," )[0].trim();
			}
		}

		return request.remoteAddress();
	}

	/**
	 * Header lookup regardless of the key's case, since adaptors differ in how they spell them
	 */
	private static String header( final NGRequest request, final String name ) {
		for( final Map.Entry<String, List<String>> entry : request.headers().entrySet() ) {
			if( name.equalsIgnoreCase( entry.getKey() ) && entry.getValue() != null && !entry.getValue().isEmpty() ) {
				return entry.getValue().get( 0 );
			}
		}

		return null;
	}
}
