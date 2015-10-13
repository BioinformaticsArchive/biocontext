package uk.ac.man.textpipe.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import uk.ac.man.textpipe.Annotator;
import uk.ac.man.textpipe.TextPipeClient;

import martin.common.Misc;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class HTTPServer implements Runnable {
	private class ServiceHandler implements HttpHandler{
		private Annotator annotator;

		public ServiceHandler(Annotator annotator){
			this.annotator = annotator;
		}

		@Override
		public void handle(HttpExchange exchange) throws IOException {
			// Not a GET/POST request? We're not handling these here.
			String requestMethod = exchange.getRequestMethod();
			if (!requestMethod.equalsIgnoreCase("GET") && !requestMethod.equalsIgnoreCase("POST")) {
				System.out.println("error");
				Headers responseHeaders = exchange.getResponseHeaders();
				responseHeaders.set("Content-Type", "text/plain");
				exchange.sendResponseHeaders(400, 0);
				exchange.close();
				return;
			}

			// so far, the request seems valid, send an OK ACK
			Headers responseHeaders = exchange.getResponseHeaders();
			responseHeaders.set("Content-Type", "text/plain");
			exchange.sendResponseHeaders(200, 0);

			// get an output stream for the response
			OutputStream responseBody = exchange.getResponseBody();

			Request request = getQuery(exchange);

			if (request == null || request.isEmpty()){
				responseBody.write(annotator.helpMessage().getBytes());
				responseBody.close();
				exchange.close();
				return;				
			}

			//TODO do encoding and decoding before sending
			
			Map<String,String> indata = new HashMap<String,String>();
			
//			if (request.hasParameter("doc_id")){
//				indata.put("doc_id", request.getValue("id"));
//			}
//			if (request.hasParameter("text")){
//				indata.put("doc_text", request.getValue("text"));
//			}
			
			for (String k : request.getMap().keySet())
				indata.put(k,request.getMap().get(k));			
			
			List<Map<String,String>> res = annotator.process(indata);
			String[] outputFields = annotator.getOutputFields();


			if (outputFields == null || outputFields.length == 0){
				Set<String> headers = new TreeSet<String>();
				for (Map<String,String> r : res)
					for (String k : r.keySet())
						headers.add(k);

				outputFields = headers.toArray(new String[0]);
			}

			responseBody.write("#".getBytes());
			responseBody.write((Misc.implode(outputFields, "\t") + "\n").getBytes());

			for (Map<String,String> r : res){
				boolean first = true;
				for (String o : outputFields){
					if (!first)
						responseBody.write("\t".getBytes());
					if (r.get(o) != null && !r.get(o).equals("null"))
						responseBody.write(r.get(o).getBytes());
					first=false;
				}
				responseBody.write("\n".getBytes());						
			}

			responseBody.close();
			exchange.close();
		}
	}

	/**
	 * Represents a user query, with key/value pairs for all parameters
	 * in a GET or POST request.
	 * <br><br>
	 * All keys are handled case-insensitive, but not the values.
	 */
	private class Request {
		Map<String, String> pairs = new HashMap<String, String>();
		public boolean hasParameter (String key) {
			return pairs.containsKey(key.toLowerCase());
		}
		public String getValue (String key) {
			if (hasParameter(key.toLowerCase()))
				return pairs.get(key.toLowerCase());
			else return null;
		}
		/**
		 * Set the <tt>value</tt> for <tt>key</tt> in this request.<br>
		 * Overwrites older values.<br>
		 * <tt>key</tt> cannot be null or empty, or it will be ignored.
		 * @param key
		 * @param value
		 */
		public void setValue (String key, String value) {
			if (key != null && key.length() > 0)
				pairs.put(key.toLowerCase(), value);
		}
		/**
		 * Used for boolean parameters, sets the value of the <tt>key</tt> to "1".<br>
		 * <tt>key</tt> cannot be null or empty, or it will be ignored.
		 * @param key
		 */
		public void setValue (String key) {
			if (key != null && key.length() > 0)
				pairs.put(key.toLowerCase(), "1");
		}
		public void clear() {
			pairs.clear();
		}
		public void removeParameter (String key) {
			pairs.remove(key.toLowerCase());
		}
		/** Checks if this request has any parameters at all. */
		public boolean isEmpty () {
			return pairs.size() == 0;
		}
		/**
		 * Checks whether the given parameter exists and is set to 
		 * 'true', 'yes', '1', or 'on' (case-insensitive).
		 * @param parameter
		 * @return
		 */
		public boolean isTrue (String key) {
			if (!hasParameter(key)) return false;
			return getValue(key).toLowerCase().matches("(true|yes|y|1|on)");
		}
		/**
		 * Prints all current key/value pairs to STDOUT.
		 */
		public void printAll () {
			for (String key: pairs.keySet())
				System.out.println(key + "=" + pairs.get(key));
		}
		public Map<String,String> getMap(){
			return pairs;
		}
	}

	private int port;
	private Annotator annotator;
	private int numThreads;

	/**
	 * Parses the request (GET or POST) and extracts key/value pairs stored in a {@link Request} object.
	 * @param exchange
	 * @return
	 */
	public Request getQuery (HttpExchange exchange) {
		Request query = null;

		String requestMethod = exchange.getRequestMethod();
		// GET request?
		if (requestMethod.equalsIgnoreCase("GET")) {
			URI requestedUri = exchange.getRequestURI();
			String queryString = requestedUri.getRawQuery();

			if (queryString != null) {
				//System.out.println("--accepted valid GET request");
				//System.out.println("--query: " + query);
				query = parseQuery(queryString);

			}

			// POST request?
		} else if (requestMethod.equalsIgnoreCase("POST")) {
			InputStream request = exchange.getRequestBody();
			if (request != null) {
				StringBuffer buffer = new StringBuffer();

				try {
					Reader reader = new BufferedReader(new InputStreamReader(request, "UTF-8"));
					int n;
					while ((n = reader.read()) != -1) {
						buffer.append((char)n);
					}
				} catch (IOException e) {
					e.printStackTrace();
				}

				query = parseQuery(buffer.toString());

			} else {
				//System.out.println("Empty request.");
			}

			// not a GET or POST request?
		} else {
			return null;
		}

		return query;
	}

	/**
	 * 
	 * @param query
	 * @return
	 * @throws UnsupportedEncodingException
	 */
	public Request parseQuery (String query) {
		Request retQuery = new Request();

		if (query != null) {
			String[] pairs = query.split("[&]");
			for (String pair: pairs) {
				String param[] = pair.split("[=]");

				String key = null;
				String value = null;

				try {
					if (param.length > 0) {
						key = URLDecoder.decode(param[0],
								System.getProperty("file.encoding"));
					}

					if (param.length > 1) {
						value = URLDecoder.decode(param[1],
								System.getProperty("file.encoding"));
					}
				} catch (UnsupportedEncodingException e) {
					e.printStackTrace();
					return null;
				}

				if (key != null && value != null) {
					retQuery.setValue(key, value);
				} else if (key != null) {
					retQuery.setValue(key, "true");
				}
			}
		}

		return retQuery;
	}
	
	public HTTPServer(int port, Annotator annotator, int numThreads){
		this.port = port;
		this.annotator = annotator;
		this.numThreads = numThreads;
	}

	public void run(){
		numThreads = annotator.isConcurrencySafe() ? numThreads : 1;

		try{
			InetSocketAddress addr = new InetSocketAddress(port);
			HttpServer server = HttpServer.create(addr, 0);

			server.createContext("/", new ServiceHandler(annotator));

			//			server.setExecutor(Executors.newCachedThreadPool());
			server.setExecutor(null);

			server.start();
		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}
	}
	
	public static void main(String[] args){
		new Thread(new HTTPServer(63000, new TextPipeClient("130.88.192.127",51001), 1)).start();
	}
}
