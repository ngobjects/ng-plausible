package ng.plausible;

import java.util.Map;

import ng.appserver.NGContext;
import ng.appserver.http.NGResponse;
import ng.appserver.privates.NGHTMLUtilities;
import ng.appserver.templating.NGDynamicElement;
import ng.appserver.templating.NGElement;
import ng.appserver.templating.associations.NGAssociation;

/**
 * The script tag for the page's head: {@code <wo:NGPlausibleScript />}. Loads the script from our own domain and
 * points it at our own event endpoint. Renders nothing when the plugin isn't configured, and nothing in
 * development mode unless ng.plausible.trackInDevelopment is true, so local runs stay out of the numbers.
 */
public class NGPlausibleScript extends NGDynamicElement {

	public NGPlausibleScript( final String name, final Map<String, NGAssociation> associations, final NGElement template ) {
		super( null, null, null );
	}

	@Override
	public void appendToResponse( final NGResponse response, final NGContext context ) {
		if( !NGPlausible.isConfigured() ) {
			return;
		}

		if( context.component().application().isDevelopmentMode() && !NGPlausible.trackInDevelopment() ) {
			return;
		}

		response.appendContentString( "<script defer data-domain=\"%s\" data-api=\"%s\" src=\"%s\"></script>".formatted(
				NGHTMLUtilities.escapeHTML( NGPlausible.domain() ),
				NGPlausible.EVENT_PATH,
				NGPlausible.SCRIPT_PATH ) );
	}
}
