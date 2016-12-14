package org.pac4j.demo.spark;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import org.pac4j.core.client.Client;
import org.pac4j.core.client.Clients;
import org.pac4j.core.config.Config;
import org.pac4j.core.exception.HttpAction;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.core.profile.ProfileManager;
import org.pac4j.http.client.indirect.FormClient;

import org.pac4j.jwt.profile.JwtGenerator;
import org.pac4j.saml.client.SAML2Client;
import org.pac4j.sparkjava.ApplicationLogoutRoute;
import org.pac4j.sparkjava.CallbackRoute;
import org.pac4j.sparkjava.SecurityFilter;
import org.pac4j.sparkjava.SparkWebContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.ModelAndView;
import spark.Request;
import spark.Response;
import spark.Route;
import spark.template.mustache.MustacheTemplateEngine;

import javax.servlet.MultipartConfigElement;
import javax.servlet.http.Part;

import static spark.Spark.*;

public class SparkPac4jDemo {

	private final static String JWT_SALT = "12345678901234567890123456789012";

	private final static Logger logger = LoggerFactory.getLogger(SparkPac4jDemo.class);

	private final static MustacheTemplateEngine templateEngine = new MustacheTemplateEngine();

	public static void main(String[] args) {
		port(5002);
		final Config config = new DemoConfigFactory(JWT_SALT, templateEngine).build();

		staticFileLocation("/public");



		post("/upload", "multipart/form-data", (request, response) -> {

			String location = "/Users/Aqib/Downloads/files/";          // the directory location where files will be stored
			long maxFileSize = 100000000;       // the maximum size allowed for uploaded files
			long maxRequestSize = 100000000;    // the maximum size allowed for multipart/form-data requests
			int fileSizeThreshold = 1024;       // the size threshold after which files will be written to disk

			MultipartConfigElement multipartConfigElement = new MultipartConfigElement(
					location, maxFileSize, maxRequestSize, fileSizeThreshold);
			request.raw().setAttribute("org.eclipse.jetty.multipartConfig",
					multipartConfigElement);

			Collection<Part> parts = request.raw().getParts();
			for (Part part : parts) {
				System.out.println("Name: " + part.getName());
				System.out.println("Size: " + part.getSize());
				System.out.println("Filename: " + part.getSubmittedFileName());
			}

			String fName = request.raw().getPart("file").getSubmittedFileName();
			System.out.println("Title: " + request.raw().getParameter("title"));
			System.out.println("File: " + fName);

			Part uploadedFile = request.raw().getPart("file");
			Path out = Paths.get("/Users/Aqib/Downloads/files/" + fName);
			try (final InputStream in = uploadedFile.getInputStream()) {
				Files.copy(in, out);
				uploadedFile.delete();
			}
			// cleanup
			multipartConfigElement = null;
			parts = null;
			uploadedFile = null;

			return "OK";
		});


	/*	post("/upload", (request, response) -> {
					// Get foo then call your Java method
					String foo = request.queryParams("foo");
					(foo);
				});
*/


		get("/", SparkPac4jDemo::index, templateEngine);
		final CallbackRoute callback = new CallbackRoute(config, null, true);
		get("/callback", callback);
		post("/callback", callback);

		before("/oidc", new SecurityFilter(config, "OidcClient"));

		get("/oidc", SparkPac4jDemo::protectedIndex, templateEngine);

		get("/loginForm", (rq, rs) -> form(config), templateEngine);
		get("/logout", new ApplicationLogoutRoute(config, "/?defaulturlafterlogout"));

		exception(Exception.class, (e, request, response) -> {
			logger.error("Unexpected exception", e);
			response.body(templateEngine.render(new ModelAndView(new HashMap<>(), "error500.mustache")));
		});
    }

	private static ModelAndView index(final Request request, final Response response) {
		final Map map = new HashMap();
		map.put("profiles", getProfiles(request, response));
		return new ModelAndView(map, "index.mustache");
	}


	private static ModelAndView form(final Config config) {
		final Map map = new HashMap();
		final FormClient formClient = config.getClients().findClient(FormClient.class);
		map.put("callbackUrl", formClient.getCallbackUrl());
		return new ModelAndView(map, "loginForm.mustache");
	}

	private static ModelAndView protectedIndex(final Request request, final Response response) {
		final Map map = new HashMap();
		map.put("profiles", getProfiles(request, response));
		return new ModelAndView(map, "protectedIndex.mustache");
	}

	private static List<CommonProfile> getProfiles(final Request request, final Response response) {
		final SparkWebContext context = new SparkWebContext(request, response);
		final ProfileManager manager = new ProfileManager(context);
		return manager.getAll(true);
	}

}
