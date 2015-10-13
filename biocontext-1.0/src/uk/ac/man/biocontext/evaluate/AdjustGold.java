package uk.ac.man.biocontext.evaluate;

import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import uk.ac.man.biocontext.evaluate.Evaluate.Approx;
import uk.ac.man.biocontext.evaluate.Evaluate.Type;
import uk.ac.man.biocontext.util.Misc;
import uk.ac.man.biocontext.wrappers.events.EventCombiner;
import uk.ac.man.biocontext.wrappers.genener.GeneNERWrapper;
import uk.ac.man.documentparser.DocumentParser;
import uk.ac.man.documentparser.dataholders.Document;
import uk.ac.man.documentparser.input.DocumentIterator;
import uk.ac.man.textpipe.Annotator;
import uk.ac.man.textpipe.TextPipeClient;

import martin.common.ArgParser;
import martin.common.Loggers;
import martin.common.SQL;

/**
 * This class is used to help adjust the gold BioNLP '09 data, where it was found
 * to be incomplete (add additional records after manual verification of FPs)
 * @author Martin
 *
 */
public class AdjustGold {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		if (args.length == 0){
			printHelp();
			System.exit(0);			
		}

		ArgParser ap = new ArgParser(args);
		Logger logger = Loggers.getDefaultLogger(ap);
		Connection conn = SQL.connectMySQL(ap, logger, "farzin");

		DocumentIterator documentIterator = DocumentParser.getDocuments(ap,logger);
		List<Document> documents = new ArrayList<Document>();
		for (Document d : documentIterator)
			documents.add(d);


		//		String inTable = ap.get("inTable");
		Annotator inAnnotator = new TextPipeClient("localhost", ap.getInt("inPort"));
		Annotator outAnnotator = new TextPipeClient("localhost", ap.getInt("outPort"));

		String outTable = ap.get("outTable");

		String[] terms = ap.gets("terms");
		if (terms.length > 0){
			Set<String> termSet = new HashSet<String>();
			for (String term : terms){
				termSet.add(term);
			}
			doGeneTerms(conn,inAnnotator,outAnnotator,outTable,termSet, documents);
		}

		String[] ids = ap.gets("ids");
		for (String id : ids){
			doIDs(id, conn,inAnnotator,outAnnotator,outTable);
		}
	}

	private static void doIDs(String id, Connection conn, Annotator inAnnotator,
			Annotator outAnnotator, String outTable) {

		id = id.replace("@@@@@","").replace(" ","").replace(".","-");
		String[] fs = id.split("-");

		//		System.out.println(id);

		String docid = fs[1];
		id = fs[2];

		Document d = new Document(docid,null,null,null,null,null,null,null,null,null,null,null,null,null,null);
		//		
		List<Map<String,String>> predicted = inAnnotator.processDoc(d);
		List<Map<String,String>> gold = outAnnotator.processDoc(d);

		Map<String,String> p = uk.ac.man.biocontext.util.Misc.getByID(predicted, id);

		if (fs[0].equals("g")){
			addGene(conn, outTable, p, "" + (gold.size() + 1), docid);
		}
		if (fs[0].equals("e")){
			System.out.println(id);
			addEvent(conn, outTable, p, "E" + (gold.size() + 1), predicted, gold, d);
		}

	}

	private static void addEvent(Connection conn, String outTable, Map<String, String> eventToAdd,
			String id, List<Map<String, String>> predictedEvents, List<Map<String, String>> goldEvents, Document d) {

		Annotator predictedGeneAnnotator = new TextPipeClient("localhost",57004);
		Annotator goldGeneAnnotator = new TextPipeClient("localhost",59004);

		List<Map<String,String>> predictedGenes = predictedGeneAnnotator.processDoc(d);
		List<Map<String,String>> goldGenes = goldGeneAnnotator.processDoc(d);

		String newParticipants = "";

		String participants = eventToAdd.get("participants");
		int c1 = 0;
		for (String a : participants.split("\\|")){
			int c2 = 0;
			for (String b : a.split(",")){
				if (b.length() > 0){
					System.out.println("Locating " + b);
					if (c2++ > 0)
						newParticipants += ",";
					if (b.startsWith("T"))
						newParticipants += "T" + findGeneID(Misc.getByID(predictedGenes, b.substring(1)), goldGenes);
					if (b.startsWith("E"))
						newParticipants += findEventID(Misc.getByID(predictedEvents, b), goldEvents, predictedGenes, goldGenes, predictedEvents);
				}
			}
			if (c1++ == 0){
				newParticipants += "|";
			}
		}

		System.out.println("Modified event participants: " + participants + "\t" + newParticipants);
		
		try {
			Statement stmt = conn.createStatement();


			String doc_id = d.getID();
			String trigger_start = eventToAdd.get("trigger_start");
			String trigger_end = eventToAdd.get("trigger_end");
			String trigger_text = eventToAdd.get("trigger_text");
			participants = newParticipants;
			String type = eventToAdd.get("type");
			

			String q = "insert into " + outTable + " (doc_id,id,trigger_start,trigger_end,trigger_text,type,participants) values" +
			" ('" + doc_id + "','" + id + "','" + trigger_start + "','" + trigger_end + "','" + trigger_text + "','" + type + "','" + participants + "');";

			System.out.println(q);
//
			stmt.execute(q);

		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(0);
		}		

		
	}

	private static String findEventID(Map<String, String> predictedEvent,
			List<Map<String, String>> goldEvents, List<Map<String, String>> predictedGenes, List<Map<String, String>> goldGenes, List<Map<String,String>> predictedEvents) {
		if (predictedEvent == null)
			throw new IllegalStateException();

		for (Map<String,String> m : goldEvents){
			if (EventCombiner.matches(predictedEvent, m, predictedGenes, goldGenes, predictedEvents, goldEvents, Approx.APPROX, Type.EVENTS_TOKYO_DEEP)){
				return m.get("id");
			}			
		}

		throw new IllegalStateException("Could not find the required event " + predictedEvent.get("id") + " among the gold events!");
	}

	private static String findGeneID(Map<String, String> predictedGene, List<Map<String, String>> goldGenes) {
		if (predictedGene == null)
			throw new IllegalStateException();

		for (Map<String,String> m : goldGenes){
			if (GeneNERWrapper.equalsIgnoreID(predictedGene, m, Approx.APPROX)){
				return m.get("id");
			}			
		}

		throw new IllegalStateException("Could not find the required gene " + predictedGene.get("id") + " among the gold genes!");
	}

	private static void doGeneTerms(Connection conn, Annotator inAnnotator, Annotator outAnnotator, String outTable, Set<String> termSet, List<Document> documents) {
		for (Document d : documents){
			List<Map<String,String>> predicted = inAnnotator.processDoc(d);
			List<Map<String,String>> gold = outAnnotator.processDoc(d);

			int c = gold.size() + 1;

			for (Map<String,String> p : predicted){
				if (termSet.contains(p.get("entity_term"))){
					boolean match = false;

					for (Map<String,String> g : gold){
						if (GeneNERWrapper.equalsIgnoreID(p,g,Evaluate.Approx.APPROX)){
							match = true;
							break;
						}
					}

					if (!match)
						addGene(conn,outTable,p,""+ c++,d.getID());

					System.out.println(d.getID() + "\t" + p.get("entity_term") + "\t" + p.get("entity_start") + "\t" + match);
				}
			}
		}



		//			Statement stmt = conn.createStatement();
		//
		//			stmt.executeQuery("SELECT * FROM " + inTable + " where doc_id='" + doc_id + "' and entity_term = '" + entity_term + "'");
	}

	private static void addGene(Connection conn, String outTable,
			Map<String, String> p, String id, String docid) {
		try {
			Statement stmt = conn.createStatement();

			String entity_id = p.get("entity_id");
			String entity_start = p.get("entity_start");
			String entity_end = p.get("entity_end");
			String entity_term = p.get("entity_term");

			String q = "insert into " + outTable + " (doc_id,id,entity_id,entity_start,entity_end,entity_term) values ('"+
			docid + "','" + id + "','" + entity_id + "','" + entity_start + "','" + entity_end + "','" + entity_term + "');";

			System.out.println(q);

			stmt.execute(q);

		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(0);
		}		
	}

	private static void printHelp() {
		System.out.println("java -cp farzin.jar uk.ac.man.farzin.evaluate.AdjustGold" +
				" --properties ~/data/db/db001.conf" +
				" --inPort 57004" +
				" --outPort 59004" +
				" --outTable gold_adjusted_entities" +
				" [--terms [x] [y] [z]]" +
		" [--ids [x] [y] [z]");
	}
}
