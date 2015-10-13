package uk.ac.man.biocontext.evaluate;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import martin.common.ArgParser;
import martin.common.Loggers;
import martin.common.Misc;
import martin.common.SQL;
import uk.ac.man.biocontext.dataholders.Event;
import uk.ac.man.biocontext.evaluate.EvaluateResult.EvalType;
import uk.ac.man.biocontext.wrappers.NegmoleWrapper;
import uk.ac.man.biocontext.wrappers.events.EventCombiner;
import uk.ac.man.biocontext.wrappers.genener.GeneCombiner;
import uk.ac.man.biocontext.wrappers.genener.GeneNERWrapper;
import uk.ac.man.documentparser.DocumentParser;
import uk.ac.man.documentparser.dataholders.Document;
import uk.ac.man.documentparser.input.DocumentIterator;
import uk.ac.man.textpipe.Annotator;
import uk.ac.man.textpipe.TextPipeClient;
import uk.ac.man.textpipe.annotators.PrecomputedAnnotator;

public class Evaluate {
	private static Logger logger;

	private static Set<String> checkedIDs = null; 

	public static final String[] goldGeneColumns = new String[]{"id","entity_id","entity_start","entity_end","entity_term","entity_group"};
	public static final String[] goldEventColumns = new String[]{"id", "type", "trigger_start", "trigger_end", "trigger_text", "participants"};
	public static final String[] goldNegspecColumns = new String[]{"event_id","negated","speculated"};

	public static boolean turku = false;
	public static boolean separateEventTypes = true;

	private static PreparedStatement selectConfidencePstmt=null;

	public enum Type {
		GENES_GENETUKIT, 
		GENES_GNAT, 
		GENES_UNION, 
		GENES_INTERSECTION,
		EVENTS_TOKYO_SHALLOW, 
		EVENTS_TOKYO_DEEP,
		EVENTS_TURKU_SHALLOW,
		EVENTS_TURKU_DEEP,
		EVENTS_UNION_DEEP,
		EVENTS_UNION_SHALLOW,
		EVENTS_INTERSECTION_DEEP,
		EVENTS_INTERSECTION_SHALLOW,

		/** Do only a shallow evaluation for events: only consider trigger and type */
		OPTIONS_SHALLOW, 

		/** Do deep evaluation for events: consider trigger, type and participants */
		OPTIONS_DEEP,

		//NEGSPEC EVALUATION, USING DATA FROM NEGMOLE ("AFTER NEGMOLE")
		NEGMOLE_AFTER_UNION,
		NEGMOLE_AFTER_INTERSECTION,

		//NEGSPEC EVALUATION, ASSUMING AFFIRMATIVE ("BEFORE NEGMOLE")
		NEGMOLE_BEFORE_UNION,
		NEGMOLE_BEFORE_INTERSECTION,
	}

	public enum Approx {
		APPROX,
		STRICT
	}

	public enum Gold{
		//		GOLD, 
		//		GOLDPLATED,
		GENIA
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		ArgParser ap = ArgParser.getParser(args);

		Evaluate.logger = Loggers.getDefaultLogger(ap);

		if (ap.containsKey("checkedIDs")){
			checkedIDs = Misc.loadStringSetFromFile(ap.getFile("checkedIDs"));
		}

		turku = ap.containsKey("turku");
		separateEventTypes = ap.containsKey("separateEventTypes");

		try{
			if (ap.containsKey("main")){
				Type[] types = new Type[]{
						//					Type.GENES_GNAT, 
						//					Type.GENES_GENETUKIT, 
						//					Type.GENES_INTERSECTION, 
						//					Type.GENES_UNION,

						Type.EVENTS_TURKU_DEEP, 
						Type.EVENTS_TOKYO_DEEP, 
						Type.EVENTS_INTERSECTION_DEEP,
						Type.EVENTS_UNION_DEEP,

						//					Type.NEGMOLE_BEFORE_INTERSECTION,
						//					Type.NEGMOLE_BEFORE_UNION,
						Type.NEGMOLE_AFTER_INTERSECTION,
						Type.NEGMOLE_AFTER_UNION,
				};

				for (Type t : types){
					if (!turku || t.toString().contains("UNION")){
						for (Gold g : Gold.values()){
							System.out.println(t.toString() + "\t" + g.toString());
							run(ap, t, Approx.APPROX, g, true);
							System.out.println();
						}
					}
				}
			}

			if (ap.containsKey("genTables")){
				genTables(ap);
			}

			if (ap.containsKey("evalConfidence")){
				evalConfidence(ap);				
			}

		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}
	}

	private static void evalConfidence(ArgParser ap) throws IOException {
		try{
			Connection conn = SQL.connectMySQL(ap, null, "farzin");
			selectConfidencePstmt = conn.prepareStatement("select confidence from  data_universe_corpus where doc_id = ? and event_id = ?");
		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}

		System.out.println("min\tmax\tp\tr");

		DocumentIterator documents = DocumentParser.getDocuments(ap, null);
		Annotator predC = new PrecomputedAnnotator(EventCombiner.outputFields, "farzin", "data_events", ArgParser.getParser());
		Annotator goldC = new PrecomputedAnnotator(goldEventColumns, "farzin", "gold_genia_events", ArgParser.getParser(), false);
		Annotator geneGoldC = new PrecomputedAnnotator(goldGeneColumns, "farzin", "gold_genia_entities", ArgParser.getParser(), false);
		Annotator genePredC = TextPipeClient.get(null, null, "localhost", 57004, "farzin", "data_genes");

		Map<String,List<Map<String,String>>> predicted = new HashMap<String,List<Map<String,String>>>();
		Map<String,List<Map<String,String>>> gold = new HashMap<String,List<Map<String,String>>>();
		Map<String,List<Map<String,String>>> predictedGenes = new HashMap<String,List<Map<String,String>>>();
		Map<String,List<Map<String,String>>> goldGenes = new HashMap<String,List<Map<String,String>>>();

		for (Document d : documents){
			predicted.put(d.getID(), predC.processDoc(d));
			gold.put(d.getID(), goldC.processDoc(d));
			predictedGenes.put(d.getID(), genePredC.processDoc(d));
			goldGenes.put(d.getID(), geneGoldC.processDoc(d));
			for (Map<String,String> m : gold.get(d.getID())){
				m.put("doc_id", d.getID());
			}
			for (Map<String,String> m : predicted.get(d.getID())){
				m.put("doc_id", d.getID());
				Double c = getConfidenceLevel(m);
				if (c != null)
					m.put("confidence", ""+c);
			}
		}

		for (double d = 1.0; d >= 0.0; d -= 0.01){
			double min = d;
			double max = 1.0;
//			Map<EvalType, Integer> res = runEvents(Type.EVENTS_UNION_DEEP, Approx.APPROX, Gold.GENIA, null, null, ap, false, min, max);
			Map<EvalType, Integer> res = evalConfidence2(predicted, gold, predictedGenes, goldGenes, min, max, ap);
			System.out.print(Misc.round(min, 4));
			System.out.print("\t" + Misc.round(max, 4));
			System.out.print("\t" + getPrecision(res));
			System.out.print("\t" + getRecall(res));
			System.out.println();
		}

		System.out.println();
		System.out.println("min\tmax\tp\ttp+fp");
		for (double d = 0; d <= 1.0; d += 0.01){
			double min = d - 0.05;
			double max = d + 0.05;
//			Map<EvalType, Integer> res = runEvents(Type.EVENTS_UNION_DEEP, Approx.APPROX, Gold.GENIA, null, null, ap, false, min, max);
			Map<EvalType, Integer> res = evalConfidence2(predicted, gold, predictedGenes, goldGenes, min, max, ap);
			System.out.print(Misc.round(min, 4));
			System.out.print("\t" + Misc.round(max, 4));
			System.out.print("\t" + getPrecision(res));
			System.out.print("\t" + (res.get(EvalType.TP)+res.get(EvalType.FP)));
			//			System.out.println("," + getRecall(res));
			System.out.println();
		}
	}

	private static Map<EvalType, Integer> evalConfidence2(
			Map<String, List<Map<String, String>>> predicted,
			Map<String, List<Map<String, String>>> gold,
			Map<String, List<Map<String, String>>> predictedGenes,
			Map<String, List<Map<String, String>>> goldGenes, double min,
			double max, ArgParser ap) {
		
		DocumentIterator documents = DocumentParser.getDocuments(ap, null);
		Map<EvalType,Integer> res = new HashMap<EvalType,Integer>();
		res.put(EvalType.TP, 0);
		res.put(EvalType.FP, 0);
		res.put(EvalType.FN, 0);
		for (Document d : documents){
			List<Map<String,String>> pred = predicted.get(d.getID());
			for (Map<String,String> p : pred){
				Double c = p.get("confidence") != null ? Double.parseDouble(p.get("confidence")) : 0.0;
				if (c >= min && c <= max){
					p.remove("invalid");
				} else {
					p.put("invalid", "1");
				}
			}
			
			List<EvaluateResult> er = EventCombiner.evaluate(pred, gold.get(d.getID()), predictedGenes.get(d.getID()), goldGenes.get(d.getID()), Approx.APPROX, Type.EVENTS_UNION_DEEP);
			for (EvaluateResult e : er){
				res.put(e.getType(), res.get(e.getType())+1);
			}
		}
		return res;
	}

	private static void genTables(ArgParser ap) throws IOException {
		System.out.println(",BioNLP,,,GENIA,,");
		System.out.println(",p,r,F,p,r,F");
		genTables(ap, Type.GENES_GNAT);
		genTables(ap, Type.GENES_GENETUKIT);
		genTables(ap, Type.GENES_INTERSECTION);
		genTables(ap, Type.GENES_UNION);
		System.out.println();
		System.out.println(",BioNLP,,,GENIA,,");
		System.out.println(",p,r,F,p,r,F");
		genTables(ap, Type.EVENTS_TURKU_DEEP);
		genTables(ap, Type.EVENTS_TOKYO_DEEP);
		genTables(ap, Type.EVENTS_INTERSECTION_DEEP);
		genTables(ap, Type.EVENTS_UNION_DEEP);
		System.out.println();
		System.out.println(",BioNLP,,,GENIA,,");
		System.out.println(",p,r,F,p,r,F");
		genTables(ap, Type.NEGMOLE_AFTER_INTERSECTION);
		genTables(ap, Type.NEGMOLE_AFTER_UNION);
		System.out.println();
	}

	private static void genTables(ArgParser ap, Type type) throws IOException {
		//		Map<EvaluateResult.EvalType,Integer> gold = run(ap, type, Approx.APPROX, Gold.GOLD, false);
		Map<EvaluateResult.EvalType,Integer> genia = run(ap, type, Approx.APPROX, Gold.GENIA, false);

		//		System.out.print(type.toString() + "," + getPrecision(gold) + "%," + getRecall(gold) + "%," + getF(gold) + "%,");
		System.out.print(getPrecision(genia) + "%," + getRecall(genia) + "%," + getF(genia) + "%");
		System.out.println();		
	}

	private static double getPrecision(Map<EvaluateResult.EvalType,Integer> counts){
		int TP = counts.get(EvalType.TP);
		int FP = counts.get(EvalType.FP);
		double d = (double)TP/(double)(TP+FP);
		return Misc.round(d * 100.0, 1);
	}

	private static double getRecall(Map<EvaluateResult.EvalType,Integer> counts){
		int TP = counts.get(EvalType.TP);
		int FN = counts.get(EvalType.FN);
		double d = (double)TP/(double)(TP+FN);
		return Misc.round(d * 100.0, 1);
	}

	private static double getF(Map<EvaluateResult.EvalType,Integer> counts){
		int TP = counts.get(EvalType.TP);
		int FP = counts.get(EvalType.FP);
		int FN = counts.get(EvalType.FN);
		double r = (double)TP/(double)(TP+FN);
		double p = (double)TP/(double)(TP+FP);
		double d = 1.0 / ((1.0/p + 1.0/r) / 2.0);
		return Misc.round(d * 100.0, 1);
	}

	private static Map<EvaluateResult.EvalType,Integer>  run(ArgParser ap, uk.ac.man.biocontext.evaluate.Evaluate.Type t, Approx a, Gold g, boolean print) throws IOException {
		BufferedWriter log = ap.containsKey("log") ? new BufferedWriter(new FileWriter(new File(ap.getFile("log"), t.toString().toLowerCase() + "_" + g.toString().toLowerCase() + "_" + a.toString().toLowerCase()) + ".log")) : null;
		BufferedWriter HTMLLog = ap.containsKey("log") ? new BufferedWriter(new FileWriter(new File(ap.getFile("log"), t.toString().toLowerCase() + "_" + g.toString().toLowerCase() + "_" + a.toString().toLowerCase()) + ".html")) : null;

		Map<EvaluateResult.EvalType,Integer> res=null;

		if (t.toString().startsWith("GENES"))
			res = runGenes(t, a, g, log, HTMLLog, ap, print);
		if (t.toString().startsWith("EVENTS"))
			res = runEvents(t, a, g, log, HTMLLog, ap, print, null, null);
		if (t.toString().startsWith("NEGMOLE"))
			res = runNegspec(t, a, g, log, HTMLLog, ap, print);

		if (log != null)
			log.close();
		if (HTMLLog != null)
			HTMLLog.close();

		return res;
	}

	private static Map<EvaluateResult.EvalType,Integer> runNegspec(Type t, Approx a, Gold g, BufferedWriter log,
			BufferedWriter HTMLLog, ArgParser ap, boolean print) throws IOException {

		//Annotator predC = TextPipeClient.get(null, null, "localhost", 57030, null, null);
		//Annotator predEventsC = TextPipeClient.get(null, null, "localhost", 59020, null, null);
		//Annotator goldC = TextPipeClient.get(null, null, "localhost", 59030, null, null);
		//Annotator goldEventsC = TextPipeClient.get(null, null, "localhost", 59020, null, null);

		Annotator predGenesC = new PrecomputedAnnotator(GeneCombiner.outputFields, "farzin", "data_genes", ap);
		Annotator predEventsC = new PrecomputedAnnotator(EventCombiner.outputFields, "farzin", "data_events", ap, true);
		Annotator predC = new PrecomputedAnnotator(NegmoleWrapper.outputFields, "farzin", "data_negmole", ap, false);

		Annotator goldC=null;
		Annotator goldGenesC=null;
		Annotator goldEventsC=null;

		if (g.toString().equals("GOLD")){
			goldGenesC = new PrecomputedAnnotator(goldGeneColumns, "farzin", "gold_bionlp_entities", ap, false);
			goldEventsC = new PrecomputedAnnotator(goldEventColumns, "farzin", "gold_bionlp_events", ap, false);
			goldC = new PrecomputedAnnotator(goldNegspecColumns, "farzin", "gold_bionlp_negspec", ap, false);
		} else if (g.toString().equals("GOLDPLATED")) {
			goldGenesC = new PrecomputedAnnotator(goldGeneColumns, "farzin", "gold_goldplated_entities", ap, false);
			goldEventsC = new PrecomputedAnnotator(goldEventColumns, "farzin", "gold_goldplated_events", ap, false);
			goldC = new PrecomputedAnnotator(goldNegspecColumns, "farzin", "gold_bionlp_negspec", ap, false);
		} else if (g.toString().equals("GENIA")) {
			goldGenesC = new PrecomputedAnnotator(goldGeneColumns, "farzin", "gold_genia_entities", ap, false);
			goldEventsC = new PrecomputedAnnotator(goldEventColumns, "farzin", "gold_genia_events", ap, false);
			goldC = new PrecomputedAnnotator(goldNegspecColumns, "farzin", "gold_genia_negspec", ap, false);
		} 

		Map<EvaluateResult.EvalType,Integer> counters = getCountMap();
		DocumentIterator documents = DocumentParser.getDocuments(ap, logger);

		for (Document d : documents){
			List<Map<String,String>> predNegspec = predC.processDoc(d);
			List<Map<String,String>> predEvents = predEventsC.processDoc(d);
			List<Map<String,String>> predGenes = predGenesC.processDoc(d);

			List<Map<String,String>> goldNegspec = goldC.processDoc(d);
			List<Map<String,String>> goldEvents = goldEventsC.processDoc(d);
			List<Map<String,String>> goldGenes = goldGenesC.processDoc(d);

			for (Map<String, String> ge : goldEvents){
				for (Map<String, String> gn : goldNegspec){
					if (ge.get("id").equals(gn.get("event_id"))){
						ge.put("negated",gn.get("negated"));
						ge.put("speculated",gn.get("speculated"));
					}
				}
			}

			for (Map<String, String> pe : predEvents){
				for (Map<String, String> pn : predNegspec){
					if (pe.get("id").equals(pn.get("event_id"))){
						pe.put("negated",pn.get("negated"));
						pe.put("speculated",pn.get("speculated"));
					}
				}
			}

			for (Map<String,String> m : predEvents){
				if (t.toString().contains("INTERSECTION") && (m.get("turku") == null || (m.get("turku").equals("0")) || m.get("tokyo").equals("0")))
					m.put("invalid", null);
				if (t.toString().contains("INTERSECTION") && !m.containsKey("turku") && m.get("tokyo").equals("1"))
					m.remove("invalid");
			}

			List<EvaluateResult> res = NegmoleWrapper.evaluate(predEvents, predGenes, goldEvents, goldGenes, a, t);

			if (HTMLLog != null && res.size() > 0)
				saveHTML(HTMLLog, res, d);

			for (EvaluateResult e : res){
				if (log != null)
					log.write("log" + "\t" + e.getType() + "\t" + e.getEntry().get("doc_id") + "\t" + e.getEntry().get("event_id") + "\t" + e.getInfo().get("type") + "\t" + e.getInfo().get("et") + "\t" + e.getInfo().get("cue") + "\t" + e.getInfo().get("etr") + "\n");

				counters.put(e.getType(),counters.get(e.getType())+1);
			}
		}

		if (print){
			print(counters, log, null);
		}

		return counters;
	}

	private static Map<EvaluateResult.EvalType,Integer> runEvents(Type t, Approx a, Gold g, BufferedWriter log, BufferedWriter HTMLLog, ArgParser ap, boolean print, Double confidenceWindowMin, Double confidenceWindowMax) throws IOException {
		Annotator predC = null;

		if (t.toString().contains("TURKU"))
			predC = new PrecomputedAnnotator(goldEventColumns, "farzin", "data_e_turku", ArgParser.getParser());
		if (t.toString().contains("TOKYO"))
			predC = new PrecomputedAnnotator(goldEventColumns, "farzin", "data_e_tokyo", ArgParser.getParser());
		if (t.toString().contains("UNION") || t.toString().contains("INTERSECTION"))
			predC = new PrecomputedAnnotator(EventCombiner.outputFields, "farzin", "data_events", ArgParser.getParser());
		if (predC == null)
			throw new IllegalStateException(t.toString());

		Annotator goldC=null, geneGoldC=null;

		Annotator genePredC = t.toString().contains("DEEP") ? TextPipeClient.get(null, null, "localhost", 57004, "farzin", "data_genes") : null;

		if (g.toString().equals("GOLD")){
			goldC = TextPipeClient.get(null, null, "localhost", 59023, "farzin", "gold_bionlp_events");
			geneGoldC = t.toString().contains("DEEP") ? TextPipeClient.get(null, null, "localhost", 59004, "farzin", "gold_bionlp_entities") : null;
		}
		if (g.toString().equals("GOLDPLATED")){
			goldC = TextPipeClient.get(null, null, "localhost", 59023, "farzin", "gold_goldplated_events");
			geneGoldC = t.toString().contains("DEEP") ? TextPipeClient.get(null, null, "localhost", 59004, "farzin", "gold_goldplated_entities") : null;
		} 
		if (g.toString().equals("GENIA")){
			goldC = new PrecomputedAnnotator(goldEventColumns, "farzin", "gold_genia_events", ArgParser.getParser(), false);
			geneGoldC = t.toString().contains("DEEP") ? new PrecomputedAnnotator(goldGeneColumns, "farzin", "gold_genia_entities", ArgParser.getParser(), false) : null;
		}

		if (turku){
			genePredC = new PrecomputedAnnotator(goldGeneColumns, "farzin", "data_turku_entities", ArgParser.getParser());
			predC = new PrecomputedAnnotator(goldEventColumns, "farzin", "data_turku_events", ArgParser.getParser(), false);
		}

		Map<EvaluateResult.EvalType,Integer> counters = getCountMap();
		Map<String,Map<EvaluateResult.EvalType,Integer>> separatedCounters = getSeparatedCountMap();
		DocumentIterator documents = DocumentParser.getDocuments(ap, logger);

		for (Document d : documents){
			//						System.out.println(d.getID());
			List<Map<String,String>> pred = predC.processDoc(d);
			List<Map<String,String>> gold = goldC.processDoc(d);

			for (Map<String,String> m : pred){
				m.put("doc_id", d.getID());

				if (t.toString().contains("INTERSECTION") && (m.get("turku") == null || (m.get("turku").equals("0")) || m.get("tokyo").equals("0")))
					m.put("invalid", null);
				if (t.toString().contains("INTERSECTION") && !m.containsKey("turku") && m.get("tokyo").equals("1"))
					m.remove("invalid");
				if (confidenceWindowMin != null && confidenceWindowMax != null && !isInsideConfidenceWindow(m, confidenceWindowMin, confidenceWindowMax))
					m.put("invalid",null);
			}

			for (Map<String,String> m : gold){
				m.put("doc_id", d.getID());
			}

			List<Map<String,String>> genesGold = geneGoldC != null ? geneGoldC.processDoc(d) : null;
			List<Map<String,String>> genesPred = genePredC != null ? genePredC.processDoc(d) : null;

			List<EvaluateResult> res = EventCombiner.evaluate(pred, gold, genesPred, genesGold, a, t);

			if (HTMLLog != null && res.size() > 0)
				saveHTML(HTMLLog, res, d);

			for (EvaluateResult e : res){
				if (log != null)
					log.write("log" + "\t" + e.getType() + "\t" + e.getEntry().get("doc_id") + "\t" + e.getEntry().get("id") + "\t" + e.getEntry().get("trigger_text") + "\t" + e.getEntry().get("type") + "\t" + e.getInfo().get("info") + "\n");

				counters.put(e.getType(),counters.get(e.getType())+1);
				separatedCounters.get(e.getEntry().get("type")).put(e.getType(),separatedCounters.get(e.getEntry().get("type")).get(e.getType())+1);
			}
		}

		if (print){
			if (separateEventTypes){
				//				for (String type : Event.EVENT_TYPES){
				//					System.out.println(type);
				//					print(separatedCounters.get(type),null,null);
				//				}
				for (String type : Event.EVENT_TYPES){
					System.out.print(type + ",");
					System.out.print(separatedCounters.get(type).get(EvalType.TP) + ",");
					System.out.print(separatedCounters.get(type).get(EvalType.FP) + ",");
					System.out.print(separatedCounters.get(type).get(EvalType.FN) + ",");
					System.out.print(getPrecision(separatedCounters.get(type)) + ",");
					System.out.print(getRecall(separatedCounters.get(type)) + ",");
					System.out.println(getF(separatedCounters.get(type)));
				}
			} else {
				print(counters, log, null);
			}
		}

		return counters;
	}

	private static Double getConfidenceLevel(Map<String, String> m){
		Double d = null;
		try{
			SQL.set(selectConfidencePstmt, 1, m.get("doc_id"));
			SQL.set(selectConfidencePstmt, 2, m.get("id"));
			ResultSet rs = selectConfidencePstmt.executeQuery();

			while (rs.next()){
				d = Double.parseDouble(rs.getString(1));
			}
		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}
		return d;
	}

	private static boolean isInsideConfidenceWindow(Map<String, String> m,
			Double confidenceWindowMin, Double confidenceWindowMax) {

		Double d = null;
		try{
			SQL.set(selectConfidencePstmt, 1, m.get("doc_id"));
			SQL.set(selectConfidencePstmt, 2, m.get("id"));
			ResultSet rs = selectConfidencePstmt.executeQuery();

			while (rs.next()){
				d = Double.parseDouble(rs.getString(1));
			}
		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}

		if (d == null)
			//			throw new IllegalStateException("d == null, doc_id = " + m.get("doc_id") + ", id = " + m.get("id"));
			return false;

		return (d >= confidenceWindowMin && d <= confidenceWindowMax);
	}

	private static Map<EvaluateResult.EvalType,Integer> runGenes(Type t, Approx a, Gold g, BufferedWriter log, BufferedWriter HTMLLog, ArgParser ap, boolean print) throws IOException {
		Annotator predC = new PrecomputedAnnotator(GeneCombiner.outputFields, "farzin", "data_genes", ArgParser.getParser());
		Annotator goldC = null;
		if (g.toString().equals("GOLD"))
			goldC = new PrecomputedAnnotator(goldGeneColumns, "farzin", "gold_bionlp_entities", ArgParser.getParser(), false);
		if (g.toString().contains("GOLDPLATED"))
			goldC = new PrecomputedAnnotator(goldGeneColumns, "farzin", "gold_goldplated_entities", ArgParser.getParser(), false);
		if (g.toString().contains("GENIA"))
			goldC = new PrecomputedAnnotator(goldGeneColumns, "farzin", "gold_genia_entities", ArgParser.getParser(), false);

		if (turku)
			predC = new PrecomputedAnnotator(goldGeneColumns, "farzin", "data_turku_entities", ArgParser.getParser());

		//				predC = new PrecomputedAnnotator(GeneCombiner.outputFields, "farzin", "data_genes", ArgParser.getParser());

		double c = ap.getDouble("cutoff",0.001);

		Map<EvaluateResult.EvalType,Integer> counters = getCountMap();

		int total = 0;
		int normalized = 0;
		DocumentIterator documents = DocumentParser.getDocuments(ap, logger);

		for (Document d : documents){
			List<Map<String,String>> pred = predC.processDoc(d);
			List<Map<String,String>> gold = goldC.processDoc(d);

			removeDuplicateGenes(pred);
			removeDuplicateGenes(gold);

			for (Map<String,String> m : pred){
				m.put("doc_id", d.getID());

				if (c != 0 && m.get("confidence") != null && Double.parseDouble(m.get("confidence")) < c)
					m.put("invalid", null);

				if ((t.toString().contains("INTERSECTION") || t.toString().contains("GENETUKIT")) && (m.get("genetukit").equals("0")))
					m.put("invalid", null);

				if ((t.toString().contains("INTERSECTION") || t.toString().contains("GNAT")) && (m.get("gnat").equals("0")))
					m.put("invalid", null);

				if (!m.containsKey("invalid") && !m.get("entity_id").equals("0"))
					normalized++;
				if (!m.containsKey("invalid"))
					total++;
			}

			for (Map<String,String> m : gold){
				m.put("doc_id", d.getID());
			}

			List<EvaluateResult> res = GeneNERWrapper.evaluate(pred, gold, a);

			if (HTMLLog != null && res.size() > 0)
				saveHTML(HTMLLog, res, d);

			for (EvaluateResult e : res){
				if (log != null)
					log.write("log" + "\t" + e.getType() + "\t" + e.getEntry().get("doc_id") + "\t" + e.getEntry().get("id") + "\t" + e.getEntry().get("entity_term") + "\n");

				counters.put(e.getType(),counters.get(e.getType())+1);
			}
		}

		double k = (double)normalized/(double)total;

		if (print){
			print(counters, log, k);
			System.out.println("Normalized: " + normalized + " / " + total + " = " + k);
			if (log != null)
				log.write("#Normalized: " + normalized + " / " + total + " = " + k + "\n");
		}
		return counters;
	}

	private static void removeDuplicateGenes(List<Map<String, String>> pred) {
		Set<String> s = new HashSet<String>();
		for (Map<String,String> m : pred){
			if (s.contains(m.get("entity_start"))){
				m.put("invalid", "1");
			} else {
				s.add(m.get("entity_start"));				
			}
		}
	}

	private static void saveHTML(BufferedWriter log, List<EvaluateResult> res,
			Document d) {
		List<Highlight> highlights = new ArrayList<Highlight>(res.size());

		for (EvaluateResult e : res){
			if (e.getS() != -1 && e.getE() != -1){
				String c = null;
				if (e.getType() == EvalType.TP)
					c = HTMLOutput.C_GREEN;
				if (e.getType() == EvalType.FP)
					c = HTMLOutput.C_BLUE;
				if (e.getType() == EvalType.FN)
					c = HTMLOutput.C_RED;

				if (e.getType() == EvalType.FP && e.getInfo().containsKey("id")){
					if (e.getInfo().get("id").startsWith("E")){
						e.getInfo().put("id"," @@@@@e." + d.getID() + "." + e.getInfo().get("id") + "@@@@@ "); 
					} else {
						e.getInfo().put("id"," @@@@@g." + d.getID() + "." + e.getInfo().get("id") + "@@@@@ "); 
					}
				}

				Highlight h = new Highlight(c, e.getS()+1, e.getE()+1, e.getInfo().toString(), null);

				//				if ((!e.getInfo().get("id").contains("E") && e.getType() != EvalType.FN) || ((e.getInfo().get("id").contains("E") && e.getType() == EvalType.FP))){
				if ((!e.getInfo().get("id").contains("E")) || e.getType() == EvalType.FP){
					//				if (e.getType() == EvalType.FN && e.getInfo().get("id").contains("E") && e.getInfo().get("type").equals("Gene_expression")){
					//if (e.getType() == EvalType.FP){
					//				if (e.getType() != EvalType.FN){
					highlights.add(h);
				}
			}
		}

		Collections.sort(highlights);

		filterIfChecked(highlights);

		highlights = HTMLOutput.combineHighlights(highlights);

		for (Highlight h : highlights){
			String text = h.getText();
			while (text.contains("type=")){
				Matcher matcher = Pattern.compile("type=.*?,").matcher(text);
				if (matcher.find()){
					String type = text.substring(matcher.start()+5,matcher.end());

					String newType = "</font><font style=\"background-color:#FF9999\">" + type + "</font><font style=\"background-color:" + h.getColor() + "\">";

					StringBuffer sb = new StringBuffer(text);
					sb.replace(matcher.start(), matcher.end(), newType);
					text = sb.toString();

				} else {
					throw new IllegalStateException(text);
				}

			}

			h.setText(text);
		}

		try{
			log.write("<b>" + d.getID() + "</b><br>");
			log.write(HTMLOutput.htmlify(d.toString(), highlights).replace("\n", "<br>"));
			log.write("<br><hr><br><br>");
			log.flush();
		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}
	}

	private static void filterIfChecked(List<Highlight> highlights) {
		if (checkedIDs != null){
			for (int i = 0; i < highlights.size(); i++){
				for (String checked : checkedIDs){

					if (checked.startsWith("@") && highlights.get(i).getText().contains(checked)){
						highlights.remove(i--);
						break;
					}
				}
			}
		}
	}

	public static void print(Map<EvalType, Integer> counters, BufferedWriter log, Double k) throws IOException {
		for (EvalType t : EvalType.values()){
			if (log != null)
				log.write("#" + t + "\t" + counters.get(t) + "\n");
			System.err.println("\t" + t + "\t" + counters.get(t));
		}

		double p = (double)counters.get(EvalType.TP) / ((double)counters.get(EvalType.TP) + (double)counters.get(EvalType.FP));
		double r = (double)counters.get(EvalType.TP) / ((double)counters.get(EvalType.TP) + (double)counters.get(EvalType.FN));
		double F = 1.0 / ((1.0/p + 1.0/r) / 2.0);

		if (log != null){
			log.write("#p\t" + p + "\n");
			log.write("#r\t" + r + "\n");
			log.write("#F\t" + F + "\n");
		}

		System.out.println("\tp\t" + p);
		System.out.println("\tr\t" + r);
		System.out.println("\tF\t" + F);
	}

	private static Map<EvalType, Integer> getCountMap() {
		Map<EvalType, Integer> res = new HashMap<EvalType,Integer>();
		res.put(EvalType.TP, 0);
		res.put(EvalType.FP, 0);
		res.put(EvalType.FN, 0);
		return res;
	}

	private static Map<String,Map<EvalType, Integer>> getSeparatedCountMap() {
		Map<String,Map<EvalType,Integer>> aux = new HashMap<String, Map<EvalType,Integer>>();
		for (String type : Event.EVENT_TYPES){
			Map<EvalType, Integer> typeMap = new HashMap<EvalType,Integer>();
			typeMap.put(EvalType.TP, 0);
			typeMap.put(EvalType.FP, 0);
			typeMap.put(EvalType.FN, 0);
			aux.put(type,typeMap);
		}
		return aux;
	}

	public static boolean overlap(int ps, int pe, int gs, int ge, Approx a){
		if (a.toString().contains("APPROX"))
			return (ps >= gs && ps < ge) || (gs >= ps && gs < pe);
		else
			return (Math.abs(ps-gs) <= 1) && (Math.abs(pe-ge) <= 1);
	}
}