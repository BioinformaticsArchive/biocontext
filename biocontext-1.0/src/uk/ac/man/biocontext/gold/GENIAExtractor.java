package uk.ac.man.biocontext.gold;

import java.io.File;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import uk.ac.man.biocontext.evaluate.Evaluate;

import uk.ac.man.textpipe.annotators.PrecomputedAnnotator;
import martin.common.ArgParser;
import martin.common.Loggers;
import martin.common.Pair;
import martin.common.SQL;
//import martin.common.xml.EntityResolver;
import martin.common.xml.*;

public class GENIAExtractor {
	private final static String delimiter = "€€€€€";

	private static final String[] sentenceXPaths = new String[]{
		"Annotation/PubmedArticleSet/PubmedArticle/MedlineCitation/Article/ArticleTitle/sentence",
		"Annotation/PubmedArticleSet/PubmedArticle/MedlineCitation/Article/Abstract/AbstractText/sentence",
	};

	private static Set<String> acceptableTermClasses = new HashSet<String>(Arrays.asList(
			"Protein_molecule",
			"Protein_complex",
			"DNA_domain_or_region",
			"RNA_molecule"
	));

	private static Set<String> acceptableEventClasses = new HashSet<String>(Arrays.asList(
			"Positive_regulation",
			"Negative_regulation",
			"Regulation",
			"Gene_expression",
			"Binding",
			"Transcription",
			"Localization",
			"Protein_catabolism",
			"Protein_amino_acid_phosphorylation",
			"Protein_amino_acid_dephosphorylation",
			"Phosphorylation"
	));

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		ArgParser ap = ArgParser.getParser(args);

		File[] xmls = ap.getFiles("xmls");
		File txtDir = ap.getFile("txtDir");
		File dtd = ap.getFile("dtd", new File("/var/f/farzin/data/eval/corpora/GENIA_event_annotation_0.9/GENIAtypes/GENIA_event_20.dtd"));
		int report = ap.getInt("report",-1);		

		String termsTable = ap.get("saveTerms");
		String eventsTable = ap.get("saveEvents");
		String negspecTable = ap.get("saveNegspec");
		Logger logger = Loggers.getDefaultLogger(ap);
		Connection conn = SQL.connectMySQL(ap, logger, "farzin");

		run(xmls,txtDir,dtd, termsTable, eventsTable, negspecTable, conn, report);
	}

	private static void run(File[] xmls, File txtDir, File dtd, String termsTable, String eventsTable, String negspecTable, Connection conn, int report) {
		try {
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder db = dbf.newDocumentBuilder();

			PreparedStatement pstmtTerm = termsTable != null ? prepareTermStatement(conn,termsTable) : null;
			PreparedStatement pstmtEvent = eventsTable != null ? prepareEventStatement(conn,eventsTable) : null;
			PreparedStatement pstmtNegspec = negspecTable != null ? prepareNegspecStatement(conn,negspecTable) : null;

			int c = 0;
			for (File xmlFile : xmls){
				String pmid = xmlFile.getName().substring(0,xmlFile.getName().length()-4);

				File txtFile = new File(txtDir,pmid + ".txt");

				String txt = txtFile.exists() ? martin.common.Misc.loadFile(txtFile) : null;
				if (txt == null){
					continue;
				}

				List<Term> geniaTerms = extractTerms(db, xmlFile, txt, pmid, dtd);
				List<Term> bionlpTerms = loadBionlpTerms(pmid);
				List<Term> mergedTerms = mergeTerms(geniaTerms, bionlpTerms);


				if (termsTable != null){
					saveDBTerms(mergedTerms, pstmtTerm, pmid);
				}

				List<Event> bionlpEvents = loadBionlpEvents(pmid);
				List<Event> geniaEvents = extractEvents(db, xmlFile, txt, pmid, dtd, geniaTerms);
				List<Event> mergedEvents = mergeEvents(geniaEvents, bionlpEvents, geniaTerms, mergedTerms);

				if (eventsTable != null){
					saveDBEvents(mergedEvents, pstmtEvent, pmid);
				}
				
				if (negspecTable != null){
					saveDBNegspec(mergedEvents, pstmtNegspec, pmid);
				}

				if (report != -1 && ++c % report == 0)
					System.out.println("Processed " + c + " documents.");
			}

		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(0);
		}		
	}

	private static void rename(Event e, String newID, List<Event> referencingEvents){
		String id = e.getId();
		e.setId(newID);

		for (Event e2 : referencingEvents){
			for (int i = 0; i < e2.getCauses().size(); i++)
				if (e2.getCauses().get(i).equals(id))
					e2.getCauses().set(i, newID);

			for (int i = 0; i < e2.getThemes().size(); i++)
				if (e2.getThemes().get(i).equals(id))
					e2.getThemes().set(i, newID);
		}
	}

	private static List<Event> mergeEvents(List<Event> geniaEvents,
			List<Event> bionlpEvents, List<Term> geniaTerms,
			List<Term> mergedTerms) {

		List<Event> res = new ArrayList<Event>();
		res.addAll(bionlpEvents);

		int c = 1000;
		for (Event e : geniaEvents){
			rename(e, "E" + c++, geniaEvents);
		}

		int prevSize = geniaEvents.size()+1;
		while (prevSize > geniaEvents.size()){
			prevSize = geniaEvents.size();

			for (int i = 0; i < geniaEvents.size(); i++){
				Event e = geniaEvents.get(i);
				Event equivalentEvent = getEquivalent(e, res, geniaTerms, mergedTerms, res);
				if (equivalentEvent != null){
					
//					if (equivalentEvent.getNegated() != e.getNegated() || equivalentEvent.getSpeculation() != e.getSpeculation())

					rename(e, equivalentEvent.getId(), geniaEvents);
					geniaEvents.remove(i--);
				} else {
					Event renamedEvent = tryToAdd(e, res, geniaTerms, mergedTerms);
					if (renamedEvent != null){
						res.add(renamedEvent);
						geniaEvents.remove(i--);
					}
				}				
			}
		}

		return res;
	}

	private static Event tryToAdd(Event e, List<Event> res,
			List<Term> geniaTerms, List<Term> mergedTerms) {

		List<String> themes = e.getThemes();
		List<String> causes = e.getCauses();
		
		for (int i = 0; i < themes.size(); i++){
			String t1 = themes.get(i);
			if (t1.startsWith("E")){
				Event e2 = getEvent(t1, res, null);
				if (e2 == null)
					return null;
			} else {
				Term gTerm = getTerm(t1, geniaTerms);
				if (gTerm == null)
					return null;
				Term match = null;
				for (Term t : mergedTerms){
					if (t.overlaps(gTerm)){
					match = t;
					break;
					}
				}
				if (match == null)
					return null;
				else
					themes.set(i,match.getId());
			}
		}

		for (int i = 0; i < causes.size(); i++){
			String c1 = causes.get(i);
			if (c1.startsWith("E")){
				Event e2 = getEvent(c1, res, null);
				if (e2 == null)
					return null;
			} else {
				Term gTerm = getTerm(c1, geniaTerms);
				if (gTerm == null)
					return null;
				Term match = null;
				for (Term t : mergedTerms){
					if (t.overlaps(gTerm)){
					match = t;
					break;
					}
				}
				if (match == null)
					return null;
				else
					causes.set(i,match.getId());
			}
		}
		
		return e;
	}

	private static Event getEquivalent(Event geniaEvent, List<Event> res,
			List<Term> geniaTerms, List<Term> mergedTerms, List<Event> mergedEvents) {

		//		System.out.println(geniaEvent.toString());

		for (Event e2 : res){
			if (e2.getEveType().equals(geniaEvent.getEveType())){
				List<String> t1 = geniaEvent.getThemes();
				List<String> t2 = e2.getThemes();
				List<String> c1 = geniaEvent.getCauses();
				List<String> c2 = e2.getCauses();

				//				System.out.println("\t" + e2.toString());

				if (t1.size() == t2.size() && c1.size() == c2.size()){

					boolean match = true;

					for (String t1s : t1){
//						if (!t1s.startsWith("E")){
							//							System.out.println("\t" + t1s);
							boolean e_match = false;
							for (String t2s : t2){
								if (!t1s.startsWith("E") && !t2s.startsWith("E") && getTerm(t1s, geniaTerms) != null && getTerm(t1s, geniaTerms).overlaps(getTerm(t2s,mergedTerms))){
									e_match = true;
									break;
								}
								if (t1s.startsWith("E") && t2s.startsWith("E") && t1s.equals(t2s)){
									e_match = true;
									break;
								}
								
								
								//								System.out.println("\t" + t2s);
//								if (!t2s.startsWith("E") && getTerm(t1s, geniaTerms).overlaps(getTerm(t2s,mergedTerms))){
//									e_match = true;
//									break;
//								}							
							}

							if (!e_match){
								match = false;
								break;
							}
//						}
//						else {
//							Event eid = getEvent(t1s, mergedEvents, null);
//							if (eid == null)
//								match = false;
//							else{
////								System.out.println(t1s);
//								//								for (Event e : mergedEvents)
//								//									System.out.println("* " + e.toString());
////								System.out.println(geniaEvent.toString()+"\n" + eid.toString()+"\n");
//							}
//						}
					}

					for (String c1s : c1){
//						if (!c1s.startsWith("E")){
							//							System.out.println("\t" + c1s);
							boolean e_match = false;
							for (String c2s : c2){
								if (!c1s.startsWith("E") && !c2s.startsWith("E") && getTerm(c1s, geniaTerms) != null && getTerm(c1s,geniaTerms).overlaps(getTerm(c2s,mergedTerms))){
									e_match = true;
									break;
								}
								if (c1s.startsWith("E") && c2s.startsWith("E") && c1s.equals(c2s)){
									e_match = true;
									break;
								}
							}

							if (!e_match){
								match = false;
								break;
							}		
//						} 
//						else {
//							Event eid = getEvent(c1s, mergedEvents, null);
//							if (eid == null)
//								match = false;
//							else {
////								System.out.println(c1s);
//								//									for (Event e : mergedEvents)
//								//										System.out.println("* " + e.toString());
////								System.out.println(geniaEvent.toString()+"\n" + eid.toString()+"\n");
//							}
//						}
					}

					if (match){
						return e2;
					}
				}
			}
		}	


		return null;
	}

	private static Term getTerm(String id, List<Term> list){
		if (list != null)
			for (Term t : list)
				if (t.getId().equals(id))
					return t;
		return null;
	}

	private static Event getEvent(String id, List<Event> list1, List<Event> list2){
		if (list1 != null)
			for (Event e : list1)
				if (e.getId().equals(id))
					return e;
		if (list2 != null)
			for (Event e : list2)
				if (e.getId().equals(id))
					return e;
		return null;		
	}

	private static List<Event> loadBionlpEvents(String pmid) {
		PrecomputedAnnotator pa = new PrecomputedAnnotator(Evaluate.goldEventColumns, "farzin", "gold_bionlp_events", ArgParser.getParser(),false);
		PrecomputedAnnotator pn = new PrecomputedAnnotator(Evaluate.goldNegspecColumns, "farzin", "gold_bionlp_negspec", ArgParser.getParser(),false);
		
		List<Map<String,String>> eventMaps = pa.processDocID(pmid);
		List<Map<String,String>> negspecMaps = pn.processDocID(pmid);
		
		List<Event> res = new ArrayList<Event>(eventMaps.size());
		
		for (Map<String,String> m : eventMaps){
			Pair<Integer> triggerOffset = new Pair<Integer>(Integer.parseInt(m.get("trigger_start")),Integer.parseInt(m.get("trigger_end")));
			List<String> causes = new ArrayList<String>();
			for (String c : m.get("participants").split("\\|")[0].split(","))
				if (c.length() > 0)
					causes.add(c);
			List<String> themes = new ArrayList<String>();
			for (String t : m.get("participants").split("\\|")[1].split(","))
				if (t.length() > 0)
					themes.add(t);

			Map<String,String> negspec = uk.ac.man.biocontext.util.Misc.getByID(negspecMaps, m.get("id"));
			
			String neg = negspec == null || negspec.get("negated").equals("0") ? "exist" : "non-exist";
			String spec = negspec == null || negspec.get("speculated").equals("0") ? "certain" : "speculated";

			Event e = new Event(pmid,m.get("id"), m.get("type"), triggerOffset, m.get("trigger_text"), themes, causes, neg, spec);

			res.add(e);
		}
		return res;		
	}

	private static List<Term> mergeTerms(List<Term> geniaTerms, List<Term> bionlpTerms) {
		List<Term> res = new ArrayList<Term>();
		res.addAll(bionlpTerms);

		for (Term t : geniaTerms){
			if (t.getType().startsWith("Protein")){
				boolean add = true;
				for (Term q : res){
					if (t.overlaps(q))
						add = false;
				}

				if (add){
					Term newTerm = t.clone();
					newTerm.setId("T" + (res.size() + 1));
					res.add(newTerm);
				}
			}
		}

		return res;
	}

	private static void saveDBEvents(List<Event> events,
			PreparedStatement pstmtEvent, String pmid) {
		try{
			for (Event e : events){
				String participants = e.getParticipants();
				participants = participants.replace("A", "T100");

				SQL.set(pstmtEvent, 1, pmid);
				SQL.set(pstmtEvent, 2, e.getId());
				SQL.set(pstmtEvent, 3, "" + e.getTriggerOffset().getX());
				SQL.set(pstmtEvent, 4, "" + e.getTriggerOffset().getY());
				SQL.set(pstmtEvent, 5, e.getTriggerText());
				SQL.set(pstmtEvent, 6, e.getEveType());
				SQL.set(pstmtEvent, 7, participants);
				pstmtEvent.addBatch();
			}
			pstmtEvent.executeBatch();
		}catch (SQLException e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}
	}

	private static void saveDBNegspec(List<Event> events,
			PreparedStatement pstmtEvent, String pmid) {
		try{
			for (Event e : events){
				String negated = e.isNegated() ? "1" : "0";
				String speculated = e.isSpeculated() ? "1" : "0";
				
				SQL.set(pstmtEvent, 1, pmid);
				SQL.set(pstmtEvent, 2, e.getId());
				SQL.set(pstmtEvent, 3, negated);
				SQL.set(pstmtEvent, 4, speculated);
				pstmtEvent.addBatch();
			}
			pstmtEvent.executeBatch();
		}catch (SQLException e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}
	}

	private static List<Term> loadBionlpTerms(String pmid){
		PrecomputedAnnotator pa = new PrecomputedAnnotator(Evaluate.goldGeneColumns, "farzin", "gold_bionlp_entities", ArgParser.getParser(),false);
		List<Map<String,String>> maps = pa.processDocID(pmid);
		List<Term> res = new ArrayList<Term>(maps.size());
		for (Map<String,String> m : maps){
			res.add(new Term(m.get("entity_term"), Integer.parseInt(m.get("entity_start")), "Protein_molecule", "T" + m.get("id")));
		}
		return res;		
	}

	private static PreparedStatement prepareEventStatement(Connection conn,
			String eventsTable) {

		try{
			Statement s = conn.createStatement();

			String q = "DROP TABLE IF EXISTS " + eventsTable;

			s.execute(q);

			q = "CREATE TABLE " + eventsTable + " ( " +
			"  `doc_id` varchar(64) character set utf8 NOT NULL," +
			"  `id` varchar(64) character set utf8 NOT NULL," +
			"  `trigger_start` text character set utf8," +
			"  `trigger_end` text character set utf8," +
			"  `trigger_text` text character set utf8," +
			"  `type` text character set utf8," +
			"  `participants` varchar(64) character set utf8 NOT NULL," +
			"  PRIMARY KEY  (`doc_id`,`id`,`participants`)" +
			") ENGINE=MyISAM DEFAULT CHARSET=latin1";
			s.execute(q);


			q = "INSERT INTO " + eventsTable + " (`doc_id`, `id`, `trigger_start`, `trigger_end`, `trigger_text`, `type`, `participants`) "+
			"VALUES (?, ?, ?, ?, ?, ?, ?)";

			return conn.prepareStatement(q);
		}catch (SQLException e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
			return null;
		}
	}

	private static PreparedStatement prepareNegspecStatement(Connection conn,
			String negspecTable) {

		try{
			Statement s = conn.createStatement();

			String q = "DROP TABLE IF EXISTS " + negspecTable;

			s.execute(q);

			q = "CREATE TABLE " + negspecTable + " ( " +
			"  `doc_id` varchar(64) character set utf8 NOT NULL," +
			"  `event_id` varchar(64) character set utf8 NOT NULL," +
			"  `negated` text character set utf8," +
			"  `speculated` text character set utf8," +
			"  PRIMARY KEY  (`doc_id`,`event_id`)" +
			") ENGINE=MyISAM DEFAULT CHARSET=latin1";
			s.execute(q);


			q = "INSERT INTO " + negspecTable + " (`doc_id`, `event_id`, `negated`, `speculated`) VALUES (?, ?, ?, ?)";

			return conn.prepareStatement(q);
		}catch (SQLException e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
			return null;
		}
	}

	private static List<Event> extractEvents(DocumentBuilder db, File xmlFile,
			String txt, String pmid, File dtd, List<Term> terms) {

		try{
			LinkedList<Event> eventsList = new LinkedList<Event>();
			Document doc = db.parse(xmlFile);

			List<Node> events = new ArrayList<Node>();

			getNamedNodes(doc,events, "event");

			extractEventMetadata(events, pmid, eventsList);
			filterEvents(eventsList,terms);
			getEventOffsets(xmlFile, txt, pmid, dtd, eventsList, db);

			return eventsList;
		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
			return null;
		}
	}

	private static void getEventOffsets(File xmlFile, String txt, String pmid,
			File dtd, LinkedList<Event> eventsList, DocumentBuilder db) {
		String rawXml = martin.common.Misc.loadFile(xmlFile);

		if (dtd != null)
			rawXml = rawXml.replace("../GENIAtypes/GENIA_event_20.dtd", dtd.getAbsolutePath());

		rawXml = rawXml.replaceAll("\\<clueType.*?\\>", delimiter);
		rawXml = rawXml.replaceAll("\\</clueType\\>", "");

		Document doc=null;
		try {
			doc = db.parse(new InputSource(new StringReader(rawXml)));
		} catch (Exception e1) {
			System.err.println(e1.toString());
			e1.printStackTrace();
			System.exit(-10);
		}

		List<Node> eventNodes = new ArrayList<Node>();
		getNamedNodes(doc, eventNodes, "event");

		for (Node en : eventNodes){
			Node clueNode = XPath.getNode("clue", en);
			String clueStr = clueNode.getTextContent();
			int triggerOffsetInSentence = clueStr.indexOf(delimiter);

			int sentOffsetInDoc = txt.indexOf(clueStr.replace(delimiter, ""));
			if (sentOffsetInDoc == -1){
				throw new IllegalStateException("Sentence not found in text! " + pmid + "\n" + clueStr.replace(delimiter, ""));
			}

			String id = en.getAttributes().getNamedItem("id").getTextContent();
			Event e = null;
			for (Event potentialE : eventsList){
				if (potentialE.getId().equals(id)){
					e = potentialE;
					break;
				}
			}
			if (e != null){
				String trigger = e.getTriggerText();

				int triggerOffsetInDoc = triggerOffsetInSentence + sentOffsetInDoc;
				int triggerEndOffsetInDoc = triggerOffsetInDoc + trigger.length();

				if (!txt.substring(triggerOffsetInDoc,triggerEndOffsetInDoc).equals(trigger))
					throw new IllegalStateException("Damn! " + trigger.toString() + " " + clueStr + " " + pmid);

				e.setTriggerOffset(new Pair<Integer>(triggerOffsetInDoc,triggerEndOffsetInDoc));
			}
		}
	}


	private static void filterEvents(LinkedList<Event> events,
			List<Term> terms) {
		Set<String> eventIDs = new HashSet<String>();
		Set<String> termIDs = new HashSet<String>();

		for (Event e : events)
			eventIDs.add(e.getId());
		for (Term t : terms)
			termIDs.add(t.getId());

		int removed=1;
		while (removed>0){
			removed=0;
			for (int i = 0; i < events.size(); i++){
				Event e = events.get(i);
				boolean remove=false;
				
				for (int j = 0; j < e.getCauses().size(); j++)
					if (!eventIDs.contains(e.getCauses().get(j)) && !termIDs.contains(e.getCauses().get(j)))
						e.getCauses().remove(j--);
//						remove=true;

				for (int j = 0; j < e.getThemes().size(); j++)
					if (!eventIDs.contains(e.getThemes().get(j)) && !termIDs.contains(e.getThemes().get(j)))
						e.getThemes().remove(j--);
//						remove=true;

				if (e.getCauses().size() > 1)
					remove=true;
				if (e.getThemes().size() == 0)
					remove=true;
				if (e.getThemes().size() > 1 && !e.getEveType().equals("Binding"))
					remove = true;
				if(!acceptableEventClasses.contains(e.getEveType()))
					remove = true;

				if (remove){
					events.remove(i--);
					removed++;
					eventIDs.remove(e.getId());
				}
			}
		}		
	}

	private static void saveDBTerms(List<Term> terms, PreparedStatement pstmt, String pmid) {
		try{
			for (Term t : terms){

				String id = t.getId();
				id = id.replace("A", "T100");

				SQL.set(pstmt, 1, pmid);
				SQL.set(pstmt, 2, id.substring(1));
				SQL.set(pstmt, 3, "" + t.getDocumentOffset());
				SQL.set(pstmt, 4, "" + t.getEndOffset());
				SQL.set(pstmt, 5, t.getText());

				pstmt.addBatch();
			}
			pstmt.executeBatch();
		}catch (SQLException e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}
	}

	private static PreparedStatement prepareTermStatement(Connection conn, String table) {
		try{
			Statement stmt = conn.createStatement();

			String q = "DROP TABLE IF EXISTS `" + table + "`";

			stmt.execute(q);

			q = "CREATE TABLE  `" + table + "` (" +
			"`doc_id` varchar(255) character set utf8 NOT NULL," +
			"`id` text character set utf8," +
			"`entity_id` text character set utf8," +
			"`entity_start` text character set utf8," +
			"`entity_end` text character set utf8," +
			"`entity_term` text character set utf8," +
			"`entity_group` text character set utf8" +
			") ENGINE=MyISAM DEFAULT CHARSET=latin1";

			stmt.execute(q);


			q = "INSERT INTO `" + table +
			"` (`doc_id`,`id`,`entity_id`,`entity_start`,`entity_end`,`entity_term`,`entity_group`)" +
			" VALUES (?,?,'0',?,?,?,NULL);";

			return conn.prepareStatement(q);

		} catch (SQLException e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(0);
			return null;
		}

	}

	private static List<Term> extractTerms(DocumentBuilder db, File f, String txt, String pmid, File dtd) {
		try{
			LinkedList<Term> termsList = new LinkedList<Term>();
			Document doc = db.parse(f);

			String creator="";

			String xml = martin.common.Misc.loadFile(f);
			int s1 = xml.indexOf("creator=\"");
			s1 += 9;
			int e1 = xml.indexOf('\"',s1+1);
			creator = xml.substring(s1,e1);

			List<Node> terms = new ArrayList<Node>();
			getNamedNodes(doc,terms, "term");
			extractTermMetadata(terms, pmid, creator, termsList);

			String rawXml = martin.common.Misc.loadFile(f);

			if (dtd != null)
				rawXml = rawXml.replace("../GENIAtypes/GENIA_event_20.dtd", dtd.getAbsolutePath());
			rawXml = rawXml.replaceAll("\\<term.*?\\>", delimiter);
			rawXml = rawXml.replaceAll("\\</term\\>", "");

			doc = db.parse(new InputSource(new StringReader(rawXml)));

			List<Term> allTerms = new ArrayList<Term>();

			for (String xpath : sentenceXPaths){
				MyNodeList sentences = XPath.getNodeList(xpath, doc);

				for (Node n : sentences){
					String sentence = n.getTextContent();
					int sentOffset = txt.indexOf(sentence.replace(delimiter, ""));
					if (sentOffset == -1){
						throw new IllegalStateException("Sentence not found in text! " + pmid + " " + sentence.replace(delimiter, ""));
					}
					int numFound = 0;
					int offset = sentence.indexOf(delimiter);
					while(offset != -1){
						int docOffset = sentOffset + offset - numFound*delimiter.length();

						Term term = termsList.removeFirst();
						term.setDocumentOffset(docOffset);


						if (!txt.substring(docOffset,docOffset+term.getLength()).equals(term.getText()))
							throw new IllegalStateException("Damn! " + term.toString() + " " + sentence + " " + pmid);


						offset = sentence.indexOf(delimiter,offset+delimiter.length());
						numFound++;
						if (acceptableTermClasses.isEmpty() || acceptableTermClasses.contains(term.getType())) {
							allTerms.add(term);
						}
					}
				}
			}

			filterTerms(allTerms);

			return allTerms;
		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}
		return null;
	}

	private static void filterTerms(List<Term> allTerms) {
		for (int i = 0; i < allTerms.size(); i++){
			for (int j = i+1; j < allTerms.size(); j++){
				if (allTerms.get(i).overlaps(allTerms.get(j))){
					if (allTerms.get(i).getLength() > allTerms.get(j).getLength()){
						allTerms.remove(i--);
					} else {
						allTerms.remove(j);
					}
					break;
				}
			}
		}

	}

	private static void getNamedNodes(Node doc, List<Node> list, String nodeType) {
		NodeList nl = doc.getChildNodes();

		if (doc.getNodeName().equals(nodeType)){
			list.add(doc);
		}

		for (int i = 0; i < nl.getLength(); i++){
			getNamedNodes(nl.item(i),list, nodeType);
		}
	}


	private static void extractTermMetadata(List<Node> terms, String pmid, String creator, LinkedList<Term> termsList) {
		for (int i = 0; i < terms.size(); i++){
			Node term = terms.get(i);
			String sem = term.getAttributes().getNamedItem("sem") != null ? term.getAttributes().getNamedItem("sem").getTextContent() : "NULL";

			String id = term.getAttributes().getNamedItem("id").getTextContent();
			termsList.add(new Term(term.getTextContent(), -1, sem, id));
		}				
	}

	private static void extractEventMetadata(List<Node> events, String pmid, LinkedList<Event> eventsList) {

		for (int i = 0; i < events.size(); i++){
			Node event = events.get(i);

			String id = event.getAttributes().getNamedItem("id").getTextContent();
			//= event.getAttributes().getNamedItem("sem") != null ? term.getAttributes().getNamedItem("sem").getTextContent() : "NULL";

			//			System.out.println(pmid + "\t" + id);


			if (XPath.getNode("clue/clueType", event) == null)
				continue;
			String eveType = XPath.getNode("type/", event).getAttributes().getNamedItem("class").getTextContent();

			String triggerText = XPath.getNode("clue/clueType", event).getTextContent();


			MyNodeList themeNodes = XPath.getNodeList("theme", event);
			List<String> themes = new ArrayList<String>(themeNodes.getLength());
			for (int j = 0; j < themeNodes.getLength(); j++){
				themes.add(themeNodes.item(j).getAttributes().getNamedItem("idref").getTextContent());
			}

			MyNodeList causeNodes = XPath.getNodeList("cause", event);
			List<String> causes = new ArrayList<String>(causeNodes.getLength());
			for (int j = 0; j < causeNodes.getLength(); j++){
				causes.add(causeNodes.item(j).getAttributes().getNamedItem("idref").getTextContent());
			}

			String speculation = event.getAttributes().getNamedItem("uncertainty") == null ? null : event.getAttributes().getNamedItem("uncertainty").getTextContent();
			String negated = event.getAttributes().getNamedItem("assertion") == null ? null : event.getAttributes().getNamedItem("assertion").getTextContent();

			eventsList.add(new Event(pmid, id, eveType, null, triggerText, themes, causes, negated, speculation));
		}				
	}
}
