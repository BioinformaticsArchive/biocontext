package uk.ac.man.biocontext.gold;

import java.io.File;
import java.io.FilenameFilter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import uk.ac.man.biocontext.evaluate.Evaluate;
import uk.ac.man.biocontext.util.Misc;
import uk.ac.man.biocontext.wrappers.MccloskyWrapper;
import uk.ac.man.biocontext.wrappers.events.EventMineWrapper;
import uk.ac.man.biocontext.wrappers.genener.GeneNERWrapper;

import martin.common.ArgParser;
import martin.common.Loggers;
import martin.common.SQL;
import martin.common.StreamIterator;

/**
 * Class for importing gold-standard BioNLP data into tables. 
 * @author Martin
 */
public class BioNLP {
	public static void main(String[] args){
		ArgParser ap = new ArgParser(args);
		File dir = ap.getFile("dir");
		Logger logger = Loggers.getDefaultLogger(ap);
		Connection conn = SQL.connectMySQL(ap, logger, ap.get("db"));
		populateBioNLPTables(dir, conn);		
	}

	private static void populateBioNLPTables(File inputDirectory, Connection conn){
		PreparedStatement pstmt1 = Misc.createTable(conn, "gold_bionlp_events", new EventMineWrapper().getOutputFields());
		PreparedStatement pstmt2 = Misc.createTable(conn, "gold_bionlp_entities", new GeneNERWrapper().getOutputFields());
		PreparedStatement pstmt3 = Misc.createTable(conn, "gold_bionlp_mcclosky", new MccloskyWrapper().getOutputFields());
		PreparedStatement pstmt4 = Misc.createTable(conn, "gold_bionlp_negspec", Evaluate.goldNegspecColumns);

		try{
			System.out.println("Populating events...");
			populateBioNLPEventsTable(inputDirectory.listFiles(new FilenameFilter(){
				public boolean accept(File dir, String name) {
					return name.endsWith(".a2");
				}
			}), pstmt1);

			System.out.println("Populating genes...");
			populateBioNLPEntitiesTable(inputDirectory.listFiles(new FilenameFilter(){
				public boolean accept(File dir, String name) {
					return name.endsWith(".a1");
				}
			}), pstmt2);

			System.out.println("Populating mcclosky...");
			populateBioNLPMcCloskyTable(inputDirectory.listFiles(new FilenameFilter(){

				public boolean accept(File dir, String name) {
					return name.endsWith(".pstree");
				}
			}), pstmt3);

			System.out.println("Populating negspec...");
			populateBioNLPNegSpecTable(inputDirectory.listFiles(new FilenameFilter(){
				public boolean accept(File dir, String name) {
					return name.endsWith(".a2");
				}
			}), pstmt4);

		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}
	}

	private static void populateBioNLPNegSpecTable(File[] listFiles,
			PreparedStatement pstmt) throws Exception {
		for (File f : listFiles){
			List<Map<String,String>> events = EventMineWrapper.parseResults(f, null);
			String docid = f.getName().split("\\.")[0];

			for (Map<String,String> e : events){
				SQL.set(pstmt, 1, docid);
				SQL.set(pstmt, 2, e.get("id"));
				SQL.set(pstmt, 3, e.get("negated"));
				SQL.set(pstmt, 4, e.get("speculated"));
				
				pstmt.addBatch();
			}
			
			if (events.size() == 0){
				SQL.set(pstmt, 1, docid);
				SQL.set(pstmt, 2, (String)null);
				SQL.set(pstmt, 3, (String)null);
				SQL.set(pstmt, 4, (String)null);
				
				pstmt.addBatch();
			}
			
			pstmt.executeBatch();
		}
	}

	private static void populateBioNLPMcCloskyTable(File[] listFiles,
			PreparedStatement pstmt) throws Exception {

		for (File f : listFiles){ 
			int i = 0; 
			StreamIterator s = new StreamIterator(f);
			String docid = f.getName().split("\\.")[0];

			for (String l : s){
				SQL.set(pstmt, 1, docid);
				SQL.set(pstmt, 2, ""+(i++));
				SQL.set(pstmt, 3, l);
				pstmt.addBatch();
			}
			pstmt.executeBatch();
		}
	}

	private static void populateBioNLPEntitiesTable(File[] listFiles,
			PreparedStatement pstmt) throws Exception {

		for (File f : listFiles){ 
			StreamIterator s = new StreamIterator(f);
			String docid = f.getName().split("\\.")[0];

			int added = 0;
			
			for (String l : s){

				String[] fs = l.split("\t");
				String[] fss = fs[1].split(" ");

				SQL.set(pstmt, 1, docid);
				SQL.set(pstmt, 2, fs[0].substring(1));
				SQL.set(pstmt, 3, "0");
				SQL.set(pstmt, 4, fss[1]);
				SQL.set(pstmt, 5, fss[2]);
				SQL.set(pstmt, 6, fs[2]);
				SQL.set(pstmt, 7, (String)null);
				pstmt.addBatch();
				added++;
			}
			
			
			if (added == 0){
				SQL.set(pstmt, 1, docid);
				SQL.set(pstmt, 2, (String)null);
				SQL.set(pstmt, 3, (String)null);
				SQL.set(pstmt, 4, (String)null);
				SQL.set(pstmt, 5, (String)null);
				SQL.set(pstmt, 6, (String)null);
				SQL.set(pstmt, 7, (String)null);
				
				pstmt.addBatch();
			}
			

			pstmt.executeBatch();
		}
	}

	private static void populateBioNLPEventsTable(File[] listFiles,
			PreparedStatement pstmt) throws Exception {
		for (File f : listFiles){ 
			String docid = f.getName().split("\\.")[0];
			
			List<Map<String,String>> data = EventMineWrapper.parseResults(f, null);
			
			for (Map<String,String> m : data){
				SQL.set(pstmt, 1, docid);
				SQL.set(pstmt, 2, m.get("id"));
				SQL.set(pstmt, 3, m.get("trigger_start"));
				SQL.set(pstmt, 4, m.get("trigger_end"));
				SQL.set(pstmt, 5, m.get("trigger_text"));
				SQL.set(pstmt, 6, m.get("type"));
				SQL.set(pstmt, 7, m.get("participants"));
				pstmt.addBatch();
				
			}
			
			if (data.size() == 0){
				SQL.set(pstmt, 1, docid);
				SQL.set(pstmt, 2, (String)null);
				SQL.set(pstmt, 3, (String)null);
				SQL.set(pstmt, 4, (String)null);
				SQL.set(pstmt, 5, (String)null);
				SQL.set(pstmt, 6, (String)null);
				SQL.set(pstmt, 7, (String)null);
				
				pstmt.addBatch();
			}

			pstmt.executeBatch();
		}
	}
}
