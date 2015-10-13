package uk.ac.man.textpipe;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import martin.common.ArgParser;
import martin.common.Loggers;
import martin.common.Misc;
import martin.common.SQL;

import uk.ac.man.documentparser.DocumentParser;
import uk.ac.man.documentparser.dataholders.Document;
import uk.ac.man.documentparser.input.DocumentIterator;
import uk.ac.man.textpipe.annotators.CacheAnnotator;
import uk.ac.man.textpipe.server.HTTPServer;
import uk.ac.man.textpipe.server.PubToolsServer;

/**
 * Primary TextPipe class, for starting TextPipe annotation tools as servers or for computations.
 *
 * @author Martin Gerner
 */
public class TextPipe {
	public static int BUFFER_SIZE = 10000000; // max line length: 10 MB

	public enum VerbosityLevel {SILENT, WARNINGS, STATUS, DEBUG}
	public static VerbosityLevel verbosity = VerbosityLevel.STATUS;

	final public static String NEWLINE_STRING = "-$$$$$-";
	final public static String TAB_STRING = "-$$$$$$-";

	public static void printData(Map<String,String> data){
		for (String k : data.keySet())
			System.out.println(k + "\t" + data.get(k));
	}

	public static void printData(List<Map<String,String>> data){
		for (Map<String,String> m : data){
			printData(m);
			System.out.println();
		}
	}

	public static void main(String[] args) throws Exception {
		ArgParser ap = ArgParser.getParser(args);

		if (ap.containsKey("help") || args.length == 0){
			System.out.println("--annotator <class name> [--debug | -d]");
			System.out.println("\tDebug output is disabled  by default");
			System.out.println("\tService parameters can be specified on the format --params <k1=v1,k2=v2,...>");
			System.out.println("\tEnabling caching: --cache <db> <table> [--clearCache] [--replace]");
			System.out.println("Running as a server:");
			System.out.println("\t--server [--port <n>] [--buffer <size>]");
			System.out.println("\tDefault port: 6000; Default network buffer: 10000000 (10 MB)");
			System.out.println("Running in compute mode:");
			System.out.println("\t--compute <db name> <table name> [--clearCompute] [--report <n>]");
			System.out.println("For database and document parameter specification, see LINNAEUS documentation");

			System.exit(0);
		}

		Logger logger = Loggers.getDefaultLogger(ap);
		verbosity = getVerbosity(ap);

		//load the annotation tool 
		Annotator annotator = getAnnotator(ap);

		//operate in a server mode, listening to HTTP requests, performing computations for connecting clients
		if (ap.containsKey("server")){
			//settings
			int port = ap.getInt("port", 6000);
			int conns = ap.getInt("conns", 1);
			BUFFER_SIZE = ap.getInt("buffer", 10000000);

			System.out.println("Starting TextPipe HTTP server at port " + port + ", conns: " + conns);
			new Thread(new HTTPServer(port, annotator, conns)).start();
		}

		//operate in a server mode, listening to basic TCP, performing computations for connecting clients
		if (ap.containsKey("rawServer")){
			//settings
			int port = ap.getInt("port", 6000);
			int conns = ap.getInt("conns", 1);
			BUFFER_SIZE = ap.getInt("buffer", 10000000);

			System.out.println("Starting TextPipe raw server at port " + port + ", conns: " + conns);
			new Thread(new PubToolsServer(port, annotator, conns)).start();			
		}

		//operate in a compute mode, processing documents and depositing results in a database
		if (ap.containsKey("compute")){
			//database location where results should be deposited
			String db = ap.gets("compute").length == 2 ? ap.gets("compute")[0] : null;
			String table = ap.gets("compute").length == 2 ? ap.gets("compute")[1] : ap.gets("compute")[0];

			boolean insertDummyRecords = ap.containsKey("insertDummyRecords");
			int batch = ap.getInt("batch", 0);
			int report = ap.getInt("report",-1);

			//load documents; see LINNAEUS documentation for specification details
			DocumentIterator documents = DocumentParser.getDocuments(ap, logger);

			String[] outputFields = annotator.getOutputFields();
		

			//connect to output database
			Connection conn = martin.common.SQL.connectMySQL(ap, null, db);

			//create database table (if --clear is specified), and get a PreparedStatement for insert, delete, and check statements
			PreparedStatement insertPstmt = getInsertStatement(conn, table, annotator, ap.containsKey("clearCompute"));
			PreparedStatement deletePstmt = ap.containsKey("check") ? null : getDeleteStatement(conn, table);
			PreparedStatement checkPstmt = ap.containsKey("check") ? getCheckStatement(conn, table) : null;

			System.out.println("insertDummyRecords: " + insertDummyRecords + ", batch: " + batch + ", check: " + (checkPstmt != null) + ", useCacheTables: " + ap.containsKey("useCacheTables"));

			compute(annotator,documents,outputFields,insertPstmt,deletePstmt,checkPstmt, logger,report,insertDummyRecords,batch);

			logger.info("%t: Compute complete, shutting down...");
			System.exit(0);
		}
	}

	public static VerbosityLevel getVerbosity(ArgParser ap) {
		if (ap.containsKey("debug"))
			return VerbosityLevel.DEBUG;

		int v = ap.getInt("v", 2);
		if (v == 0)
			return VerbosityLevel.SILENT;
		if (v == 1)
			return VerbosityLevel.WARNINGS;
		if (v == 2)
			return VerbosityLevel.STATUS;
		if (v == 3)
			return VerbosityLevel.DEBUG;

		throw new IllegalStateException ("verbosity need to be in range [0,3]");		
	}

	/**
	 * Run TextPipe in compute mode, computing results for documents and depositing the results in a database
	 * @param annotator
	 * @param documents
	 * @param outputFields
	 * @param inserPstmt the prepared database insert statement
	 * @param deletePstmt 
	 * @param checkPstmt 
	 * @param logger logging tool
	 * @param report report progress every "report" documents (if progress > 0)
	 * @param insertDummyRecords 
	 * @param batch 
	 */
	private static void compute(Annotator annotator,
			DocumentIterator documents, String[] outputFields,
			PreparedStatement inserPstmt, PreparedStatement deletePstmt, PreparedStatement checkPstmt, Logger logger, int report, boolean insertDummyRecords, int batch) {

		int c = 0;

		if (batch == 0){
			for (Document d : documents){
				try{
					if (checkPstmt == null || !checkIfComputed(d, checkPstmt)){
						if (verbosity.compareTo(VerbosityLevel.DEBUG) >= 0){
							System.out.println("Processing " + d.getID());
						}
						
						List<Map<String,String>> output = annotator.processDoc(d);
						saveToDB(output,d.getID(),outputFields,inserPstmt,deletePstmt,insertDummyRecords);
					}

					if (report > 0 && ++c % report == 0)
						logger.info("%t: processed " + c + " documents\n");

				} catch (Exception e){
					System.err.println(e);
					e.printStackTrace();
					System.exit(-1);
				}
			}
		} else {
			while (documents.hasNext()){
				List<Document> documentList = new ArrayList<Document>(batch);
				for (int i = 0; i < batch && documents.hasNext(); i++){
					Document d = documents.next();
					if (checkPstmt == null || !checkIfComputed(d, checkPstmt)){
						documentList.add(d);
					} else {
						i--;
						if (report > 0 && ++c % report == 0)
							logger.info("%t: processed " + c + " documents\n");
					}
				}

				if (verbosity.compareTo(VerbosityLevel.DEBUG) >= 0){
					System.out.print("Processing ");
					for (Document d : documentList)
						System.out.print(d.getID() + ", ");
					System.out.println();
				}
				
				List<List<Map<String,String>>> res = annotator.processDocs(documentList);

				if (documentList.size() != res.size())
					throw new IllegalStateException("documentList.size()=" + documentList.size() + " and res.size()=" + res.size());

				for (int i = 0; i < res.size(); i++){
					List<Map<String,String>> m = res.get(i);
					String id = documentList.get(i).getID();
					saveToDB(m, id, outputFields, inserPstmt, deletePstmt, insertDummyRecords);
					if (report > 0 && ++c % report == 0)
						logger.info("%t: processed " + c + " documents\n");
				}				
			}
		}

		logger.info("%t: Completed.\n");

		annotator.destroy();
	}

	private static boolean checkIfComputed(Document d, PreparedStatement checkPstmt) {
		try{
			SQL.set(checkPstmt, 1, d.getID());
			ResultSet rs = checkPstmt.executeQuery();

			if (rs.next()){
				rs.close();
				return true;
			} else {
				rs.close();
				return false;
			}

		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}

		return false;
	}

	/**
	 * Saves computed results to a database table
	 * @param output
	 * @param docID 
	 * @param outputFields
	 * @param insertPstmt the insert statement
	 * @param deletePstmt 
	 * @param insertDummyRecords if true, will insert a row containing the doc_id but otherwise all nulls if "output" is empty. This is used in order to signify an "empty" result. 
	 */
	private static void saveToDB(List<Map<String, String>> output, String docID, String[] outputFields, PreparedStatement insertPstmt, PreparedStatement deletePstmt, boolean insertDummyRecords) {
		try {

			if (deletePstmt != null){
				SQL.set(deletePstmt, 1, docID);
				deletePstmt.execute();
			}

			for (Map<String,String> m : output){
				int currentField = 1;
				SQL.set(insertPstmt,currentField++,docID);

				for (String k: outputFields)
					SQL.set(insertPstmt,currentField++,m.get(k));

				insertPstmt.addBatch();
			}

			if (output.size() == 0 && insertDummyRecords){
				int currentField = 1;
				SQL.set(insertPstmt,currentField++,docID);

				for (int i = 0; i < outputFields.length; i++)
					SQL.set(insertPstmt,currentField++,(String)null);

				insertPstmt.addBatch();
			}

			insertPstmt.executeBatch();
		} catch (Exception e){
			throw new RuntimeException(e);
		}
	}

	private static PreparedStatement getDeleteStatement(Connection conn,
			String table) {

		try {
			String pq = "DELETE FROM " + table + " WHERE doc_id = ?";
			PreparedStatement pstmt = conn.prepareStatement(pq);

			return pstmt;
		} catch (Exception e){
			throw new RuntimeException(e);
		}		
	}

	private static PreparedStatement getCheckStatement(Connection conn,
			String table) {

		try {
			String pq = "SELECT doc_id FROM " + table + " WHERE doc_id = ?";
			PreparedStatement pstmt = conn.prepareStatement(pq);

			return pstmt;
		} catch (Exception e){
			throw new RuntimeException(e);
		}		
	}

	/**
	 * Prepare an insert statement for inserting data to a table, and create table if clear==true (and drop if it already exists)
	 * @param conn database connection
	 * @param table
	 * @param outputFields column field names (in addition to the mandatory doc_id)
	 * @param clear if true, drop table if it exists and create a new one 
	 * @return prepared insert statement for inserting data into the table
	 */
	private static PreparedStatement getInsertStatement(Connection conn,
			String table, Annotator annotator, boolean clear) {

		try {
			if (clear){
				conn.createStatement().execute("DROP TABLE IF EXISTS " + table);

				String q = "CREATE TABLE  " + table + " ("+
				"doc_id VARCHAR(255) NOT NULL,";

				String[] outputFields = annotator.getOutputFieldsWithIndices();
				
				for (String o : outputFields){
					if (o.startsWith("@")){
						q += o.substring(1) + " VARCHAR(256),";
					}else {
						q += o + " TEXT,";
					}
				}
				
				for (String o : outputFields){
					if (o.startsWith("@")){
						q += "KEY " + o.substring(1) + "(" + o.substring(1) + "), "; 
					}
				}
				
				
				q += " KEY doc_id (doc_id) ) ENGINE=MyISAM DEFAULT CHARSET=utf8;";

				conn.createStatement().execute(q);
			}

			String[] outputFields = annotator.getOutputFields();
			
			String pq = "INSERT INTO " + table + " (doc_id, " + Misc.implode(outputFields, ", ") + 
			") VALUES (?" + Misc.replicateString(",?", outputFields.length) + ")";
		
			PreparedStatement pstmt = conn.prepareStatement(pq);

			return pstmt;
		} catch (Exception e){
			throw new RuntimeException(e);
		}		
	}

	/**
	 * Load the user-specified annotation tool.
	 * The tool results will  be cached by database if if cacheConn != null.   
	 * @param className the name of the annotation class 
	 * @param serviceParams additional tool parameters used for initialization (e.g. specifying locations for required resources)
	 * @param cacheConn cache database connection (null if no caching should occur)
	 * @param cacheTable cache database table name (null if no caching should occur)
	 * @param replace if the tool should replace any results that already might exist in the cache database (false if no caching should occur)
	 * @return A loaded annotation tool.
	 */
	public static Annotator getAnnotator(String className, Map<String, String> serviceParams, ArgParser ap, String db, String cacheTable) {
		if (className == null)
			throw new IllegalStateException("You need to specify the classname of the annotator!");

		try {
			System.out.println("Loading annotator from class " + className + "...");
			Annotator annotator = (Annotator)(Class.forName(className).newInstance());

			annotator.init(serviceParams);

			System.out.println("\nDone, annotator loaded.");

			if (db != null){
				System.out.println("Using cache from table " + cacheTable + ".");
				annotator = new CacheAnnotator(annotator, db, cacheTable, ap);
			}

			return annotator;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Parses a string specifying annotation tool parameters
	 * The parameters should be on the format k1:v1,k2:v2,...
	 * @param string the input string
	 * @return a key-value map for the parameters
	 */
	public static Map<String, String> getServiceParams(String string) {
		if (string == null)
			return null;

		Map<String,String> res = new HashMap<String,String>();

		String[] fields = string.split(", ?");
		for (String f : fields){
			String k = f.split(":|=")[0];
			String v = f.split(":|=")[1];
			if (res.containsKey(k))
				throw new IllegalStateException("Duplicate parameters: " + k);
			res.put(k,v);			
		}

		return res;			
	}

	/**
	 * send a debug message to System.out, if debugMode==true.
	 * @param msg
	 */
	public static void debug(String msg) {
		if (verbosity.compareTo(VerbosityLevel.DEBUG) >= 0) {
			System.out.println(msg);
		}
	}

	/**
	 * encodes the values in a key/value map for network transmission (replacing e.g. newline and tab characters with special characters)
	 * @param data
	 * @return a new map containing the encoded values
	 */
	static Map<String,String> encode(Map<String,String> data){
		Map<String, String> res = new HashMap<String,String>();
		for (String k : data.keySet())
			if (data.get(k) != null)
				res.put(k, data.get(k).replace("\n", NEWLINE_STRING).replace("\t", TAB_STRING));
			else
				res.put(k,null);
		return res ;
	}

	/**
	 * decodes the values in a key/value map that were received by network transmission (replacing e.g. special characters with e.g. newline and tab characters)
	 * @param data
	 * @return a new map containing the decoded values
	 */
	public static Map<String,String> decode(Map<String,String> data){
		Map<String, String> res = new HashMap<String,String>();
		for (String k : data.keySet())
			if (data.get(k) != null)
				res.put(k, data.get(k).replace(NEWLINE_STRING,"\n").replace(TAB_STRING,"\t"));
			else
				res.put(k,null);
		return res;
	}

	public static String toString(List<Map<String, String>> output) {
		StringBuffer sb = new StringBuffer();

		for (Map<String,String> m : output){
			Map<String,String> n = encode(m);
			for (String k : n.keySet())
				sb.append(k + "\t" + n.get(k) + "\n");
			sb.append("\n");
		}

		return sb.toString();
	}

	public static Annotator getAnnotator(ArgParser ap) {
		if (ap.containsKey("cache"))
			return getAnnotator(ap.get("annotator"), getServiceParams(ap.get("params")), ap, ap.gets("cache")[0], ap.gets("cache")[1]);
		else
			return getAnnotator(ap.get("annotator"), getServiceParams(ap.get("params")), ap, null, null);
	}
}
