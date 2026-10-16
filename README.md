# ng-plausible

[Plausible](https://plausible.io) analytics for [ng-objects](https://github.com/ngobjects/ng-objects) applications, served and received through the application's own domain. Pages never talk to the stats server directly, so tracker blocklists have nothing to match, and the only things passed on about a visitor are what Plausible needs anyway: the user agent and the address, for unique-visitor counting and geography.

Works with Plausible Cloud and self-hosted Plausible Community Edition alike.

## Use

Add the dependency:

```xml
<dependency>
	<groupId>is.rebbi.ng</groupId>
	<artifactId>ng-plausible</artifactId>
	<version>1.0-SNAPSHOT</version>
</dependency>
```

Put the tag in your page's `<head>`:

```html
<wo:NGPlausibleScript />
```

Set two properties, as `-D` arguments to the application or in a `Properties` resource:

```
ng.plausible.url=https://plausible.io          the Plausible server
ng.plausible.domain=example.com                 your site, exactly as registered in Plausible
```

That's it. The plugin is picked up automatically (it registers itself as an `NGPlugin`), serves Plausible's script at `/np/js` (fetched from the server and held for an hour) and forwards events posted to `/np/event`. The path deliberately says nothing about analytics.

## Optional properties

| Property | Default | Meaning |
|---|---|---|
| `ng.plausible.script` | `script.outbound-links.js` | Which of Plausible's script variants to serve, e.g. `script.js`, `script.outbound-links.js`, `script.tagged-events.outbound-links.js` |
| `ng.plausible.trackInDevelopment` | `false` | The tag renders nothing in development mode unless this is `true`, so local runs stay out of the numbers |

Unconfigured, the tag renders nothing and `/np/*` answers 404; a warning is logged at startup.

## Notes

- If the script can't be fetched and nothing is cached, `/np/js` answers an empty 204: a missing script is a page without stats, never a broken page.
- Behind a webserver, the visitor's address is taken from the first entry of `X-Forwarded-For`; otherwise the connection's remote address is used.
- Custom events work as usual: `plausible("Signup")` in the page goes through the same endpoint.
