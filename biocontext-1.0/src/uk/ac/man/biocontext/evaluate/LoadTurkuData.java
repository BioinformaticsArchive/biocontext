package uk.ac.man.biocontext.evaluate;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import uk.ac.man.documentparser.DocumentParser;
import uk.ac.man.documentparser.dataholders.Document;
import uk.ac.man.documentparser.input.DocumentIterator;
import martin.common.ArgParser;
import martin.common.Loggers;
import martin.common.Misc;
import martin.common.SQL;
import martin.common.StreamIterator;

public class LoadTurkuData {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		ArgParser ap = ArgParser.getParser(args);
		Logger logger = Loggers.getDefaultLogger(ap);

		Connection conn = SQL.connectMySQL(ap, logger, "farzin");
		File baseDir = ap.getFile("dir");

		boolean clear = ap.containsKey("clear");

		DocumentIterator documents = DocumentParser.getDocuments(ap, logger);
		PreparedStatement genePstmt = getGeneInsertStatement(conn, ap.get("loadGenes", "data_turku_entities"), clear);
		//		doGenes(baseDir, documents,pstmt);

		PreparedStatement eventPstmt = getEventInsertStatement(conn, ap.get("loadEvents", "data_turku_events"), clear);
		//		doEvents(baseDir,documents,pstmt);

		int report = ap.getInt("report",-1);

		run(baseDir,documents,genePstmt,eventPstmt, report);
	}

	private static void run(File baseDir, DocumentIterator documents,
			PreparedStatement genePstmt, PreparedStatement eventPstmt, int report) {
		int c = 0;
		for (Document d : documents){
			String id = d.getID();

			File a1 = new File(getPath(baseDir, id),id + ".a1");
			File a2 = new File(getPath(baseDir, id),id + ".a2.t123");

			if (a1.exists()){
				doGeneFile(a1,genePstmt,id);
			}

			if (a2.exists()){
				doEventFile(a2,eventPstmt,id);
			}

			if (report != -1 && ++c  % report == 0)
				System.out.println("Processed " + c + " documents.");
		}			
	}

	private static void doEventFile(File a1, PreparedStatement pstmt, String id) {
		StreamIterator s = new StreamIterator(a1);
		try{
			Map<String,String> triggers = new HashMap<String,String>();

			for (String line : s){

				if (line.startsWith("T")){
					String[] fs = line.split("\t");
					String[] fss = fs[1].split(" ");
					triggers.put(fs[0] + ".s", fss[1]);
					triggers.put(fs[0] + ".e", fss[2]);
					triggers.put(fs[0] + ".t", fs[2]);
				}

				if (line.startsWith("E")){

					//					System.out.println(line);

					String[] fs = line.split("\t");
					String[] fss = fs[1].split(" ");


					String trigger = fss[0].split(":")[1];

					SQL.set(pstmt, 1, id);
					SQL.set(pstmt, 2, fs[0]);
					SQL.set(pstmt, 3, triggers.get(trigger + ".s"));
					SQL.set(pstmt, 4, triggers.get(trigger + ".e"));
					SQL.set(pstmt, 5, triggers.get(trigger + ".t"));
					SQL.set(pstmt, 6, fss[0].split(":")[0]);

					List<String> themes = new ArrayList<String>();
					List<String> causes = new ArrayList<String>();


					for (int i = 1; i < fss.length; i++){
						//						System.out.println(fss[i]);

						if (fss[i].split(":")[0].startsWith("Theme"))
							themes.add(fss[i].split(":")[1]);
						if (fss[i].split(":")[0].startsWith("Cause"))
							causes.add(fss[i].split(":")[1]);
					}

					String p = Misc.implode(causes.toArray(new String[0]), ",") + "|" + 
					Misc.implode(themes.toArray(new String[0]), ",");

					SQL.set(pstmt,7,p);
					pstmt.addBatch();
				}

				//				doc_id,id,trigger_start,trigger_end,trigger_text,type,participants
			}
			pstmt.executeBatch();
		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}
	}

	private static PreparedStatement getEventInsertStatement(Connection conn,
			String table, boolean clear) {
		try{	
			String q = "DROP TABLE IF EXISTS `" + table + "`";

			if (clear){
				System.out.println("Clearing " + table);
				conn.createStatement().execute(q);
			}

			q = "CREATE TABLE  `" + table + "` ("+
			"`doc_id` varchar(64) character set utf8 NOT NULL,"+
			"`id` varchar(64) character set utf8 NOT NULL,"+
			"`trigger_start` text character set utf8,"+
			"`trigger_end` text character set utf8,"+
			"`trigger_text` text character set utf8,"+
			"`type` text character set utf8,"+
			"`participants` varchar(64) character set utf8 NOT NULL,"+
			"PRIMARY KEY  (`doc_id`,`id`,`participants`)"+
			") ENGINE=MyISAM DEFAULT CHARSET=utf8;";

			if (clear)
				conn.createStatement().execute(q);

			q = "INSERT INTO `" + table + "` (doc_id,id,trigger_start,trigger_end,trigger_text,type,participants) VALUES (?,?,?,?,?,?,?)";
			return conn.prepareStatement(q);
		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}
		return null;
	}

	private static File getPath(File baseDir, String pmid){
		int id = Integer.parseInt(pmid);

		int a = id / 1000000;
		id -= a * 1000000;

		int b = id / 100000;
		id -= b * 100000;

		int c = id / 10000;
		id -= c * 10000;

		int d = id / 1000;
		id -= d * 1000;

		File f = new File(baseDir,""+a);
		f = new File(f,""+b);
		f = new File(f,""+c);
		f = new File(f,""+d);
		return f;
	}

	private static void doGeneFile(File a1, PreparedStatement pstmt, String docid) {
		StreamIterator s = new StreamIterator(a1);
		try{
			for (String line : s){
				String[] fs = line.split("\t");
				String[] fss = fs[1].split(" ");

				if (fss[0].equals("Protein")){
					if (!fs[0].startsWith("T"))
						throw new IllegalStateException(a1.getAbsolutePath() + " --- " + s);

					SQL.set(pstmt, 1, docid);
					SQL.set(pstmt, 2, fs[0].substring(1));
					SQL.set(pstmt, 3, fss[1]);
					SQL.set(pstmt, 4, fss[2]);
					SQL.set(pstmt, 5, fs[2]);
					pstmt.addBatch();
				}
			}
			pstmt.executeBatch();
		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}
	}


	private static PreparedStatement getGeneInsertStatement(Connection conn, String table, boolean clear) {
		try{	
			String q = "DROP TABLE IF EXISTS `" + table + "`";

			if (clear){
				System.out.println("Clearing " + table);
				conn.createStatement().execute(q);
			}

			q = "CREATE TABLE  `" + table + "` ("+
			"`doc_id` varchar(255) NOT NULL,"+
			"`id` text,"+
			"`entity_id` text,"+
			"`entity_start` text,"+
			"`entity_end` text,"+
			"`entity_term` text,"+
			"`entity_group` text,"+
			"KEY `doc_id` (`doc_id`)"+
			") ENGINE=MyISAM DEFAULT CHARSET=utf8;";

			if (clear)
				conn.createStatement().execute(q);

			q = "INSERT INTO `" + table + "` (doc_id,id,entity_id,entity_start,entity_end,entity_term) VALUES (?,?,0,?,?,?)";
			return conn.prepareStatement(q);
		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}
		return null;
	}
}
