package com.mydomain.web;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.Session;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.AuthHandler;
import io.vertx.ext.web.handler.BasicAuthHandler;
import io.vertx.ext.web.handler.CookieHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.UserSessionHandler;
import io.vertx.ext.web.sstore.LocalSessionStore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.types.ObjectId;
import org.mongodb.morphia.Datastore;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mydomain.web.ServicesFactory;
import com.mydomain.web.User;
import com.mydomain.web.UserDTO;
import com.mydomain.web.Blog;
import com.mydomain.web.BlogDTO;
import com.mydomain.web.Comment;
import com.mydomain.web.CommentDTO;

public class RouterVerticle extends AbstractVerticle {

	private static List<ServerWebSocket> allConnectedSockets = new ArrayList<>();
	public  static HashMap<String, User> loggedInUsers = new HashMap<String, User>();

	@Override
	public void start(Future<Void> startFuture) throws Exception {
		HttpServer server = vertx.createHttpServer();

		server.websocketHandler(serverWebSocket -> {
			//Got a new connection
			System.out.println("Connected: "+serverWebSocket.remoteAddress());
			//Store new connection in list
			allConnectedSockets.add(serverWebSocket);
			//Setup handler to receive the data
			
			for(ServerWebSocket sock1: allConnectedSockets){
				System.out.println("client..."+sock1.remoteAddress());
			};
			
			serverWebSocket.handler( handler ->{
				String message = new String(handler.getBytes());
				System.out.println("message: "+message);
				//Now broadcast received message to all other clients
				for(ServerWebSocket sock : allConnectedSockets){
					System.out.println("Sending message to client..."+sock.remoteAddress());
					Buffer buf = Buffer.buffer();
					buf.appendBytes(message.getBytes());
					sock.writeFinalTextFrame(message);
				}
			});
			//Register handler to remove connection from list when connection is closed
			serverWebSocket.closeHandler(handler->{
				allConnectedSockets.remove(serverWebSocket);
			});
		});
		
		Router router = Router.router(vertx);

		router.route().handler(CookieHandler.create());
		router.route().handler(
				SessionHandler.create(LocalSessionStore.create(vertx)));
		AuthProvider ap = new MyAuthProvier();
		router.route().handler(UserSessionHandler.create(ap));

		AuthHandler basicAuthHandler = BasicAuthHandler.create(ap);
		
		router.route("/private/*").handler(basicAuthHandler);
		router.route("/private/*").handler(new Handler<RoutingContext>() {
			@Override
			public void handle(RoutingContext rc) {
				System.out.println("Handler: " + rc.user().principal());
				rc.response().end("Done");
			}
		});
		router.get("/Services/rest/user").handler(new CheckUser());
		router.get("/Services/rest/user?signedIn=true").handler(new CheckUser());
		router.post("/Services/rest/user/auth").handler(new UserLogin());
		router.get("/services/users/:id").handler(new UserLoader());
		router.post("/Services/rest/user/register").handler(new UserPersister());
		//router.post("/Services/rest/blogs").handler(new BlogLoader());
		
        router.get("/Services/rest/blogs").handler(new BlogList());
        router.post("/Services/rest/blogs/:id/comments").handler(new CommentPersister());
        router.post("/Services/rest/blogs").handler(new BlogPersister());
		
        router.route("/*").handler(StaticHandler.create("webroot"));		
		
		//server.requestHandler(router::accept).listen(8080);
		server.requestHandler(router::accept).listen(8080, java.net.InetAddress.getLocalHost().getHostAddress());
		System.out.println("Thread Router Start: "
				+ Thread.currentThread().getId());
		System.out.println("STARTED ROUTER");
		startFuture.complete(); 
	}
	
	public static void sendNewUserInfo(User u) {
		for(ServerWebSocket sock : allConnectedSockets){
		System.out.println("Sending User to client...");
		JsonObject userInfoMsg = new JsonObject();
		JsonObject userInfo = new JsonObject();

		userInfo.put("first", u.getFirst());
		userInfo.put("last", u.getLast());
		userInfo.put("username", u.getUserName());
		userInfoMsg.put("event", "UserLogin");
		userInfoMsg.put("messageObject", userInfo);
		System.out.println("New User msg: " + userInfoMsg.toString());
		sock.writeFinalTextFrame(userInfoMsg.toString());

		}
	}
	
}


class MyAuthProvier implements AuthProvider {

	@Override
	public void authenticate(JsonObject json,
			Handler<AsyncResult<io.vertx.ext.auth.User>> handler) {
		System.out.println("Authenticating users with: " + json);
		AsyncResult<io.vertx.ext.auth.User> result = new AsyncResult<io.vertx.ext.auth.User>() {
			public boolean succeeded() {
				return json.getString("username").equals("admin")
						&& json.getString("password").equals("admin123");
			}

			public io.vertx.ext.auth.User result() {
				return new io.vertx.ext.auth.User() {
					public void setAuthProvider(AuthProvider provider) {
						System.out
								.println("Setting auth provider: " + provider);
					}

					public JsonObject principal() {
						Map<String, Object> dataMap = new HashMap<>();
						dataMap.put("buffer", json.getString("username"));
						JsonObject obj = new JsonObject(dataMap);
						return obj;
					}

					public io.vertx.ext.auth.User isAuthorised(String url,
							Handler<AsyncResult<Boolean>> handler) {
						System.out.println("Is authorized call: " + url);
						return this;
					}

					public io.vertx.ext.auth.User clearCache() {
						return null;
					}
				};
			}

			public boolean failed() {
				return !(json.getString("username").equals("admin") && json
						.getString("password").equals("admin123"));
			}

			public Throwable cause() {
				return null;
			}
		};
		handler.handle(result);
	}
}

class GraphLoader implements Handler<RoutingContext> {
	@Override
	public void handle(RoutingContext arg0) {
		GraphDatabaseFactory dbFactory = new GraphDatabaseFactory();
		File f = new File("/Users/maruthir/Documents/Training/neo4jdb");
		GraphDatabaseService db = dbFactory.newEmbeddedDatabase(f);
		try (Transaction tx = db.beginTx()) {
			// Perform DB operations
			tx.success();
		}
	}
}

class UserPersister implements Handler<RoutingContext> {
	public void handle(RoutingContext routingContext) {
		System.out.println("Thread UserPersister: "
				+ Thread.currentThread().getId());
		// This handler will be called for every request
		HttpServerResponse response = routingContext.response();
		routingContext.request().bodyHandler(new Handler<Buffer>() {
			public void handle(Buffer buf) {
				String json = buf.toString("UTF-8");
				ObjectMapper mapper = new ObjectMapper();
				UserDTO dto = null;
				try {
					dto = mapper.readValue(json, UserDTO.class);
				} catch (IOException e) {
					e.printStackTrace();
				}
				User u = dto.toModel();
				Datastore dataStore = ServicesFactory.getMongoDB();
				dataStore.save(u);
				response.setStatusCode(204).end("Data saved");
			};
		});
	}
}

class UserLoader implements Handler<RoutingContext> {
	public void handle(RoutingContext routingContext) {
		System.out.println("Thread UserLoader: "
				+ Thread.currentThread().getId());
		// This handler will be called for every request
		HttpServerResponse response = routingContext.response();
		String id = routingContext.request().getParam("id");

		response.putHeader("content-type", "application/json");
		Datastore dataStore = ServicesFactory.getMongoDB();
		ObjectId oid = null;
		try {
			oid = new ObjectId(id);
		} catch (Exception e) {// Ignore format errors
		}
		List<User> users = dataStore.createQuery(User.class).field("id")
				.equal(oid).asList();
		if (users.size() != 0) {
			UserDTO dto = new UserDTO().fillFromModel(users.get(0));
			ObjectMapper mapper = new ObjectMapper();
			JsonNode node = mapper.valueToTree(dto);
			response.end(node.toString());
		} else {
			response.setStatusCode(404).end("not found");
		}
	}
}

class UserLogin implements Handler<RoutingContext> {
	public void handle(RoutingContext routingContext) {
		System.out.println("Thread UserLoader: "
				+ Thread.currentThread().getId());
		// This handler will be called for every request
		HttpServerResponse response = routingContext.response();
		Session session = routingContext.session();
		
		String user = routingContext.request().getParam("username");
		String pass = routingContext.request().getParam("password");

//		response.putHeader("content-type", "application/json");
//		Datastore dataStore = ServicesFactory.getMongoDB();
/*		ObjectId oid = null;
		try {
			oid = new ObjectId(id);
		} catch (Exception e) {// Ignore format errors
		}
		*/
/*		
		List<User> users = dataStore.createQuery(User.class).field("userName")
				.equal(user).asList();
		
		if (users.size() != 0) {
            for (User u : users){
                if (u.getPassword().equals(pass)){
                    session.put("user", u.getUserName());
                    response.setStatusCode(204).end("User Authentication Success !!!");
                    break;
                }
            }
		} else {
			response.setStatusCode(404).end("not found");
		}
*/	
        routingContext.request().bodyHandler(new Handler<Buffer>() {
            public void handle(Buffer buf)
            {
                Datastore dataStore = ServicesFactory.getMongoDB();
                String json = buf.toString("UTF-8");
                JsonObject jsonObj = new JsonObject(json);
                String user = jsonObj.getString("userName");
                String passwd = jsonObj.getString("password");
                List<User> users = dataStore.createQuery(User.class).field("userName")
                                .equal(user).asList();
                if (users.size() != 0)
                {
                    for (User u : users){
                        if (u.getPassword().equals(passwd)){
                            session.put("user", u.getUserName());
/*
                            if (RouterVerticle.loggedInUsers.put(u.getUserName(), u) == null) {
                                System.out.println("Send New User information to clients");
                                RouterVerticle.sendNewUserInfo(u);
                               }
*/                            
                            response.setStatusCode(204).end("User Authentication Success !!!");
                            break;
                        }
                    }
                }
                else
                {
                    response.setStatusCode(404).end("not found");
                }
            };
        });
	}
}

class BlogLoader implements Handler<RoutingContext>{
	public void handle(RoutingContext routingContext){
		System.out.println("Thread BlogLoader"
				+ Thread.currentThread().getId());
		HttpServerResponse response = routingContext.response();
		routingContext.request().bodyHandler(new Handler<Buffer>() {
			public void handle(Buffer buf) {
				String json = buf.toString("UTF-8");
				ObjectMapper mapper = new ObjectMapper();
				
				Blog dto = null;
				try {
					dto = mapper.readValue(json, Blog.class);
				} catch (IOException e) {
					e.printStackTrace();
				}
//				User u = dto.toModel();
				Datastore dataStore = ServicesFactory.getMongoDB();
				dataStore.save(dto);
				response.setStatusCode(204).end("Data saved");
			};
		});		
	}
}

class BlogPersister implements Handler<RoutingContext> {
    public void handle(RoutingContext routingContext) {
        System.out.println("Thread BlogPersister: "
                + Thread.currentThread().getId());
        HttpServerResponse response = routingContext.response();
        Session session = routingContext.session();
        routingContext.request().bodyHandler(new Handler<Buffer>() {
            public void handle(Buffer buf) {
                String json = buf.toString("UTF-8");
                ObjectMapper mapper = new ObjectMapper();
                Datastore dataStore = ServicesFactory.getMongoDB();
                BlogDTO dto = null;
                try {
                    dto = mapper.readValue(json, BlogDTO.class);
                    String userName = session.get("user");
                    if (userName == null || userName.equals(""))
                        userName = "test";
                    User user = dataStore.createQuery(User.class).field("userName")
                                    .equal(userName).get();
                    dto.setUserFirst(user.getFirst());
                    dto.setUserLast(user.getLast());
                    dto.setUserId(user.getId().toString());
                    dto.setDate(new Date().getTime());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                Blog blog = dto.toModel();
                dataStore.save(blog);
                /*List<Blog> blogs = user.getUserBlogs();
                blogs.add(blog);
                user.setUserBlogs(blogs);
                dataStore.save(user);*/
                response.setStatusCode(204).end("Blog saved !!");
            };
        });
    }
}

class CheckUser implements Handler<RoutingContext> {
    public void handle(RoutingContext routingContext) {
        System.out.println("Thread Checkuser: "
                + Thread.currentThread().getId());
        MultiMap params = routingContext.request().params();
        HttpServerResponse response = routingContext.response();
        Session session = routingContext.session();
        String userName = session.get("user");        
        
        System.out.println("Paramas:"+params);
        //System.out.println("Context User:"+routingContext.user().principal().toString());
        System.out.println("User:"+userName);       
                
        response.putHeader("content-type", "application/text");
        routingContext.request().bodyHandler(new Handler<Buffer>() {
            public void handle(Buffer buf) {
            	if(userName != null)
            		response.end(userName.toString());
            	else
            		response.setStatusCode(204).end("User Available!!");
            };
        });
    }
}


class CommentPersister implements Handler<RoutingContext> {
    public void handle(RoutingContext routingContext) {
        System.out.println("Thread CommentPersister: "
                + Thread.currentThread().getId());
        HttpServerResponse response = routingContext.response();
        String blogId = routingContext.request().getParam("id");
        Session session = routingContext.session();
        response.putHeader("content-type", "application/json");
        routingContext.request().bodyHandler(new Handler<Buffer>() {
            public void handle(Buffer buf) {
                String json = buf.toString("UTF-8");
                ObjectMapper mapper = new ObjectMapper();
                Datastore dataStore = ServicesFactory.getMongoDB();
                CommentDTO dto = null;
                try {
                    dto = mapper.readValue(json, CommentDTO.class);
                    String userName = session.get("user");
                    if (userName == null || userName.equals(""))
                        userName = "test";
                    User user = dataStore.createQuery(User.class).field("userName")
                                    .equal(userName).get();
                    dto.setUserFirst(user.getFirst());
                    dto.setUserLast(user.getLast());
                    dto.setUserId(user.getId().toString());
                    dto.setDate(new Date().getTime());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                Comment comment = dto.toModel();
                
                ObjectId oid = null;
                try {
                    oid = new ObjectId(blogId);
                } catch (Exception e) {// Ignore format errors
                }
                Blog blog = dataStore.createQuery(Blog.class).field("id")
                        .equal(oid).get();
                List<Comment> comments = blog.getComments();
                comments.add(comment);
                blog.setComments(comments);
                dataStore.save(blog);
                
                
                /*List<Blog> blogs = user.getUserBlogs();
                blogs.add(blog);
                user.setUserBlogs(blogs);
                dataStore.save(user);*/
                response.setStatusCode(204).end("Comment saved !!");
            };
        });
    }
}

class BlogList implements Handler<RoutingContext> {
    public void handle(RoutingContext routingContext) {
        System.out.println("Thread BlogList: "
                + Thread.currentThread().getId());
        HttpServerResponse response = routingContext.response();
        response.putHeader("content-type", "application/json");
        Datastore dataStore = ServicesFactory.getMongoDB();
        
        
        //For tag search
        String tagParam = routingContext.request().query();
        List<Blog> blogs = null;
        if (tagParam != null){
            String tagValue = tagParam.split("=")[1];
            blogs = dataStore.createQuery(Blog.class).field("tags").contains(tagValue).asList();
        }
        else{
            blogs = dataStore.createQuery(Blog.class).asList();
        }
        if (blogs.size() != 0)
        {
            List<BlogDTO> obj = new ArrayList<BlogDTO>();
            for (Blog b : blogs)
            {
                BlogDTO dto = new BlogDTO().fillFromModel(b);
                obj.add(dto);
            }
            
            ObjectMapper mapper = new ObjectMapper();
            try
            {
                response.end(mapper.writeValueAsString(obj));
            }
            catch (JsonProcessingException e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        else {
            response.setStatusCode(404).end("not found");
        }
    }
}