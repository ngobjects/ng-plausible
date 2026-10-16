package ng.plausible;

import ng.appserver.NGApplication;
import ng.plugins.Elements;
import ng.plugins.NGPlugin;
import ng.plugins.Routes;

/**
 * Plausible analytics served and received through the application's own domain, so pages never talk to the
 * stats server directly and tracker blocklists have nothing to match.
 *
 * Put {@code <wo:NGPlausibleScript />} in the page's head and set two properties:
 *
 * <pre>
 * ng.plausible.url=https://stats.example.com     the Plausible server
 * ng.plausible.domain=example.com                the site, as registered in Plausible
 * </pre>
 *
 * The plugin then serves Plausible's script at {@code /np/js} (fetched from the server, held for an hour) and
 * forwards the page's events from {@code /np/event}, passing on the visitor's user agent and address, which
 * Plausible needs for unique-visitor counting and geography; nothing else about the visitor is sent.
 * The path deliberately says nothing about analytics.
 *
 * Optional: {@code ng.plausible.script} picks the script variant (default {@code script.outbound-links.js});
 * {@code ng.plausible.trackInDevelopment=true} renders the tag in development mode too, which it otherwise skips.
 */
public class NGPlausiblePlugin implements NGPlugin {

	@Override
	public String namespace() {
		return "plausible";
	}

	@Override
	public void load( final NGApplication application ) {
		NGPlausible.logConfiguration();
	}

	@Override
	public Routes routes() {
		return Routes
				.create()
				.map( NGPlausible.PATH_PREFIX + "/*", NGPlausible::handle );
	}

	@Override
	public Elements elements() {
		return Elements
				.create()
				.elementClass( NGPlausibleScript.class, "NGPlausibleScript" );
	}
}
