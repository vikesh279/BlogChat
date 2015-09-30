package com.mydomain.web;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.StaticHandler;

public class Server extends AbstractVerticle {

	// Convenience method so you can run it in your IDE
	public static void main(String[] args) {

		// We set this property to prevent Vert.x caching files loaded from the
		// classpath on disk
		// This means if you edit the static files in your IDE then the next
		// time they are served the new ones will
		// be served without you having to restart the main()
		// This is only useful for development - do not use this in a production
		// server
		System.setProperty("vertx.disableFileCaching", "true");
		Vertx vertx = Vertx.factory.vertx();
		//vertx.deployVerticle(Server.class.getName());
		DeploymentOptions options = new DeploymentOptions().setInstances(2);
		vertx.deployVerticle("com.mydomain.web.RouterVerticle", options);
	}

	@Override
	public void start() {
		Router router = Router.router(vertx);		
		DeploymentOptions options = new DeploymentOptions().setInstances(10);
		// Serve the static pages
		router.route().handler(StaticHandler.create("webroot"));
		vertx.createHttpServer().requestHandler(router::accept).listen(8080);
		vertx.deployVerticle("com.mydomain.web.RouterVerticle", options);
		System.out.println("Server is started");

	}
}
