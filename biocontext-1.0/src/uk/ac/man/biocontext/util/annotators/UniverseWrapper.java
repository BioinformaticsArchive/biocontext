package uk.ac.man.biocontext.util.annotators;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import martin.common.ArgParser;
import martin.common.Pair;

import uk.ac.man.biocontext.dataholders.BindingEvent;
import uk.ac.man.biocontext.dataholders.RegulationEvent;
import uk.ac.man.biocontext.dataholders.SimpleEvent;
import uk.ac.man.biocontext.evaluate.HTMLOutput;
import uk.ac.man.biocontext.evaluate.Highlight;
import uk.ac.man.biocontext.tools.anatomy.Anatomy;
import uk.ac.man.biocontext.util.Misc;
import uk.ac.man.biocontext.wrappers.LinnaeusWrapper;
import uk.ac.man.biocontext.wrappers.NegmoleWrapper;
import uk.ac.man.biocontext.wrappers.genener.GeneCombiner;
import uk.ac.man.textpipe.Annotator;
import uk.ac.man.textpipe.annotators.PrecomputedAnnotator;

public class UniverseWrapper extends Annotator{

	private Annotator ptcGenes;
	private Annotator ptcNegspec;
	private Annotator ptcAnatomyEntities;
	private String[] geneColumns;
	private String[] basicColumns;
	private String[] negspecColumns;
	private String[] anatomyColumns;
	private String[] outputFieldsWithIndices;
	private Annotator ptcAnatomyAss;
	private int pmcSubdocs;
	private Map<String, String> homologenes;

	@Override
	public void init(Map<String, String> data) {

		String db = ArgParser.getParser().get("inputDB","farzin");

		this.ptcGenes = new PrecomputedAnnotator(GeneCombiner.outputFields, db, "data_genes", ArgParser.getParser());
		this.ptcAnatomyAss = new PrecomputedAnnotator(Anatomy.outputFields, db, "data_anatomy", ArgParser.getParser());
		this.ptcNegspec = new PrecomputedAnnotator(NegmoleWrapper.outputFields, db, "data_negmole", ArgParser.getParser());
		this.ptcAnatomyEntities = new PrecomputedAnnotator(LinnaeusWrapper.outputFields, db, "data_l_anatomy", ArgParser.getParser());

		this.pmcSubdocs = Integer.parseInt(data.get("pmcsubdocs"));

		if (data.containsKey("homologene")){
			this.homologenes = martin.common.Misc.loadMap(new File(data.get("homologene")));
			System.out.println("Loaded " + homologenes.size() + " homologene mappings.");
		} else {
			System.out.println("No homologenes loaded.");
		}		
	}

	@Override
	public String[] getOutputFields(){
		String[] res = getOutputFieldsWithIndices().clone();
		for (int i = 0; i < res.length; i++){
			if (res[i].startsWith("@"))
				res[i] = res[i].substring(1);
		}
		return res;
	}

	public static String[] getHashedFieldsContradictions(){
		return new String[]{
				"type",
				"negated",
				"speculated",
				"anatomy_entity_id",
				"c_is_event",
				"c_type",
				"c_negated",
				"c_speculated",
				"c_anatomy_entity_id",
				"c_c_entity_id",
				"c_t_entity_id",
				"t_is_event",
				"t_type",
				"t_negated",
				"t_speculated",
				"t_anatomy_entity_id",
				"t_c_entity_id",
				"t_t_entity_id"
		};
	}

	public static String[] getHashedFieldsWeb(){
		return new String[]{
				"type",
				"anatomy_entity_id",
				"c_c_entity_id",
				"t_t_entity_id"
		};
	}

	public static String[] getHashedFieldsHomology(){
		return new String[]{
				"type",
				"anatomy_entity_id",
				"c_c_entity_homologene_id",
				"t_t_entity_homologene_id"
		};
	}

	private String getHashString(Map<String,String> data, String[] keys){
		String values = "";
		for (String k : keys){
			if (k.endsWith("_entity_id") && data.get(k) != null && data.get(k).equals("0")){

				String v = data.get(k.substring(0,k.length()-2) + "term");

				values += "\t" + v;

			} else if (k.endsWith("homologene_id") && data.get(k) != null && data.get(k).equals("0")) {
				return null;
			} else {
				//			if (data.get(k) != null)
				values += "\t" + (data.get(k) != null ? data.get(k) : "-");
			}
		}
		values = values.substring(1);

		return values;
	}

	@Override
	public String[] getOutputFieldsWithIndices() {
		if (this.outputFieldsWithIndices != null)
			return this.outputFieldsWithIndices;

		this.basicColumns = new String[]{"@type", "trigger_start", "trigger_end", "trigger_text", "tokyo", "turku", "inferred_23", "inferred_31"};
		this.negspecColumns = new String[] {"@negated", "@speculated", "n_cue", "n_cue_start", "n_cue_end", "s_cue", "s_cue_start", "s_cue_end",};
		this.anatomyColumns = new String[]{"@anatomy_entity_id","anatomy_entity_start","anatomy_entity_end","anatomy_entity_term"};
		this.geneColumns = new String[] {"@entity_id","@entity_homologene_id","entity_start","entity_end","entity_term","entity_species","confidence","gnat","genetukit",};

		List<String> res = new ArrayList<String>();

		Collections.addAll(res, "@confidence");
		Collections.addAll(res, "@doc_year", "doc_issn", "doc_description", "doc_source");
		Collections.addAll(res, "sentence", "sentence_html", "sentence_offset", "@level");
		Collections.addAll(res, "@hash_contradictions", "hash_contradictions_string", "@hash_web", "hash_web_string", "@hash_homologene", "hash_homologene_string");

		Collections.addAll(res, basicColumns);
		Collections.addAll(res, negspecColumns);
		Collections.addAll(res, anatomyColumns);

		for (String i : new String[]{"c_", "t_"}){
			res.add(i + "is_event");
			for (String c : basicColumns){
				res.add(i + c);
			}
			for (String c : negspecColumns){
				res.add(i + c);
			}
			for (String c : anatomyColumns){
				res.add(i + c);
			}
			for (String j : new String[]{"c_", "t_"}){
				for (String a : geneColumns){
					res.add(i + j + a);
				}
			}
		}

		String[] resArray = res.toArray(new String[]{}); 

		for (int i = 0; i < resArray.length; i++){
			if (resArray[i].contains("_@")){
				String s = resArray[i];
				s = s.replace("_@", "_");
				s = "@" + s;
				resArray[i] = s;
			}
		}

		this.outputFieldsWithIndices = resArray;

		return resArray;
	}

	@Override
	public List<Map<String, String>> process(Map<String, String> data) {
		if (data.get("doc_source").equals("PMC") && !data.get("doc_id").contains(".")){
			return pmcProcess(data);
		} else {
			return uniProcess(data); 
		}
	}

	private List<Map<String, String>> pmcProcess(Map<String, String> data) {
		List<Map<String,String>> subdocs = Misc.doSubDocs(data, this.pmcSubdocs);
		//		System.out.println("# of Subdocs : " + subdocs.size());

		List<List<Map<String,String>>> subdocRes = new ArrayList<List<Map<String,String>>>(subdocs.size());
		for (Map<String,String> subdoc : subdocs){
			subdocRes.add(uniProcess(subdoc));
			//			System.out.println(subdocRes.get(subdocRes.size()-1).size());
		}


		List<Map<String,String>> res = new ArrayList<Map<String,String>>();

		for (int i = 0; i < subdocs.size(); i++){
			String docid = subdocs.get(i).get("doc_id");
			int s = Integer.parseInt(docid.split("\\.")[2]);
			//			System.out.println(docid + " " + s);
			for (Map<String,String> entry : subdocRes.get(i)){
				//				TextPipe.printData(entry);

				Misc.increase(entry,"sentence_offset",s);

				for (String x : new String[]{"","c_","t_"}){
					if (entry.get(x + "inferred_23") != null) entry.put(x + "inferred_23", "yes");
					if (entry.get(x + "inferred_31") != null) entry.put(x + "inferred_31", "yes");

					Misc.increase(entry,x+"trigger_start",s);
					Misc.increase(entry,x+"trigger_end",s);
					Misc.increase(entry,x+"n_cue_start",s);
					Misc.increase(entry,x+"n_cue_end",s);
					Misc.increase(entry,x+"s_cue_start",s);
					Misc.increase(entry,x+"s_cue_end",s);
					Misc.increase(entry,x+"anatomy_entity_start",s);
					Misc.increase(entry,x+"anatomy_entity_end",s);

					Misc.increase(entry,x+"c_entity_start",s);
					Misc.increase(entry,x+"c_entity_end",s);
					Misc.increase(entry,x+"t_entity_start",s);
					Misc.increase(entry,x+"t_entity_end",s);

					checkOffsets(entry, data);
				}

				res.add(entry);
			}
		}

		return res;
	}

	private List<Map<String, String>> uniProcess(Map<String, String> data) {
		//System.out.println("starting process for " + data.get("doc_id"));

		List<Map<String, String>> res = new ArrayList<Map<String, String>>();
		List<Map<String, String>> events = ptcAnatomyAss.process(data);

		if (events.size() == 0){
			return res;
		}

		List<Map<String, String>> genes = ptcGenes.process(data);
		List<Map<String, String>> negspecs = ptcNegspec.process(data);

		//List<Map<String, String>> linnaeus = ptcLinnaeus.process(data);
		//List<Map<String, String>> anatomy = ptcAnatomyAss.process(data);
		List<Map<String, String>> anatomyEntities = ptcAnatomyEntities.process(data);

		disambiguateEntities(genes);
		disambiguateEntities(anatomyEntities);

		String text = data.get("doc_text");
		List<Pair<Integer>> sentenceSplits = Misc.getSentenceSplits(text);

		for (Map<String, String> event : events){
			if (event.get("level") == null){
				throw new IllegalStateException(event.toString());
			}
			if (Integer.parseInt(event.get("level")) <= 1){
				Map<String, String> item = new HashMap<String, String>();

				setSentenceData(event, item, sentenceSplits, text);
				item.put("level", event.get("level"));

				setEventData(item, event, "", negspecs, anatomyEntities, genes, events, data.get("doc_id"));

				if (item.get("inferred_23") != null) {
					item.put("inferred_23", "1");
				}
				if (item.get("inferred_31") != null) {
					item.put("inferred_31", "1");
				}

				setHomologs(item);

				item.put("hash_contradictions", ""+getHashString(item, getHashedFieldsContradictions()).hashCode());
				item.put("hash_contradictions_string", getHashString(item, getHashedFieldsContradictions()));
				item.put("hash_web", ""+getHashString(item, getHashedFieldsWeb()).hashCode());
				item.put("hash_web_string", getHashString(item, getHashedFieldsWeb()));
				String homology_hash_string = getHashString(item, getHashedFieldsHomology());
				item.put("hash_homologene", homology_hash_string != null ? ""+homology_hash_string.hashCode() : null);
				item.put("hash_homologene_string", homology_hash_string);

				item.put("doc_year", data.get("doc_year"));
				item.put("doc_issn", data.get("doc_issn"));
				item.put("doc_description", data.get("doc_description"));
				item.put("doc_source", data.get("doc_source"));
				item.put("sentence_html", getMarkedUpSentence(item));

				try{
					Misc.checkColumnLengths(item, getOutputFieldsWithIndices(), data.get("doc_id"));
					checkOffsets(item, data);
					res.add(item);
				}catch(IllegalStateException e){
					System.err.println(e);
				}
			}
		}
		
		for (Map<String,String> item : res){
			item.put("confidence", "" + getConfidence(item, res));
		}
		
		return res;
	}

	private void setHomologs(Map<String, String> item) {
		for (String t : new String[]{"c_c_", "c_t_", "t_c_", "t_t_"}){
			if (item.get(t + "entity_id") != null){
				if (homologenes.get(item.get(t+"entity_id")) != null){
					item.put(t + "entity_homologene_id", homologenes.get(item.get(t+"entity_id")));
				} else {
					item.put(t + "entity_homologene_id", "0");
				}
			}
		}		
	}

	private String getMarkedUpSentence(Map<String, String> item) {
		if (item.get("sentence") == null)
			return null;

		StringBuffer sb = new StringBuffer(item.get("sentence"));

		List<Highlight> highlights = new ArrayList<Highlight>();

		addHighlight(highlights, "trigger", item);
		addHighlight(highlights, "n_cue", item);
		addHighlight(highlights, "s_cue", item);
		addHighlight(highlights, "c_c_entity", item);
		addHighlight(highlights, "c_t_entity", item);
		addHighlight(highlights, "t_c_entity", item);
		addHighlight(highlights, "t_t_entity", item);
		addHighlight(highlights, "anatomy_entity", item);
		addHighlight(highlights, "c_anatomy_entity", item);
		addHighlight(highlights, "t_anatomy_entity", item);

		Collections.sort(highlights);
		highlights = HTMLOutput.combineHighlights(highlights);
		Collections.sort(highlights);

		for (Highlight h : highlights){
			Pair<String> tags = getTags(h);
			sb = sb.insert(Math.min(Math.max(h.getEnd()  , 0), sb.length()-1), tags.getY());
			sb = sb.insert(Math.min(Math.max(h.getStart(), 0), sb.length()-1), tags.getX());
		}

		return sb.toString();
	}

	private Pair<String> getTags(Highlight h) {
		String type = h.getColor();

		if (type.contains("cue")){
			return new Pair<String>("<b>","</b>");
		}
		if (type.contains("trigger")){
			return new Pair<String>("<i>","</i>");
		}
		return new Pair<String>("<u>","</u>");
	}

	private void addHighlight(List<Highlight> highlights, String prefix, Map<String, String> item) {
		if (item.get(prefix + "_start") != null && item.get(prefix + "_end") != null && !item.get(prefix + "_start").equals("null")){
			int so = Integer.parseInt(item.get("sentence_offset"));
			int s = Integer.parseInt(item.get(prefix + "_start")) - so;
			int e = Integer.parseInt(item.get(prefix + "_end")) - so;
			Highlight h = new Highlight(prefix,s,e,null,null);
			highlights.add(h);
		}		
	}

	private double getConfidence(Map<String, String> item, List<Map<String,String>> events) {
		double d = 1;

		//TODO consider confidence levels of nested events		

		Map<String,Double> coefficients = getConfidenceCoefficients(); //enables easier editing of confidences

		{ //turku/tokyo/intersection and event type
			boolean turku = item.get("turku") != null && item.get("turku").equals("1");
			boolean tokyo = item.get("tokyo") != null && item.get("tokyo").equals("1");
			String k=null;

			if (tokyo && turku)
				k = "event_intersection_";
			else if (tokyo && !turku)
				k = "event_tokyo_";
			else if (!tokyo && turku)
				k = "event_turku_";
			else
				throw new IllegalStateException("event with neither turku nor tokyo");

			d *= coefficients.get(k + item.get("type"));
		}

		String[] participantPrefixes = new String[]{"t_t_", "t_c_", "c_t_", "c_c_"};

/*
		//the first dimension is for gnat/genetukit, and the second for the participant prefixes

		boolean[][] participantConfidences = new boolean[2][4];
		for (int c = 0; c < 4; c++){
			if (item.get(participantPrefixes[c] + "gnat") == null || 
					item.get(participantPrefixes[c] + "gnat") != null && item.get(participantPrefixes[c] + "gnat").equals("1")){
				participantConfidences[0][c] = true;
			}
			if (item.get(participantPrefixes[c] + "genetukit") == null ||
					item.get(participantPrefixes[c] + "genetukit") != null && item.get(participantPrefixes[c] + "genetukit").equals("1")){
				participantConfidences[1][c] = true;
			}

			//TODO null should probably mean 0 rather than 1 (this is how it works for events, e.g. turku crashes)
		}

		boolean debugFlag = false;		
		for (int c = 0; c < 4; c++){
			if (participantConfidences[0][c] && participantConfidences[1][c]){

				d *= (0.828/0.828);
			} else if (!participantConfidences[0][c] && participantConfidences[1][c]){
				d *= (0.722/0.828);
			} else if (participantConfidences[0][c] && !participantConfidences[1][c]){
				d *= (0.798/0.828);
			} else {
				debugFlag = true;
			}
		}
		if (debugFlag){
			throw new IllegalStateException("Neither Gnat nor GeneTUkit.");
		}
		for (int c = 0; c < 4; c++){
			if (item.get(participantPrefixes[c] + "confidence") != null){
				d *= Double.parseDouble(item.get(participantPrefixes[c] + "confidence"));
			}
		}
*/
		
		for (String prefix : participantPrefixes){
			boolean gnat = item.get(prefix + "gnat") != null && item.get(prefix + "gnat").equals("1"); 
			boolean genetukit = item.get(prefix + "genetukit") != null && item.get(prefix + "genetukit").equals("1");
			
			if (gnat && genetukit){
				d *= coefficients.get("gene_intersection");
			} else if (gnat){
				d *= coefficients.get("gene_gnat");
			} else if (genetukit){
				d *= coefficients.get("gene_genetukit");
			}
			
			if (item.get(prefix + "confidence") != null){
				d *= Double.parseDouble(item.get(prefix + "confidence"));
			}
		}

		if (item.get("inferred_23") != null && item.get("inferred_23") == "1"){
			d *= coefficients.get("inferred_23");
		}

		if (item.get("inferred_31") != null && item.get("inferred_31") == "1"){
			d *= coefficients.get("inferred_31");
		}

		return d;
	}

	private Map<String, Double> getConfidenceCoefficients() {
		Map<String,Double> coefficients = new HashMap<String,Double>();

		coefficients.put("event_turku_Binding", 0.3560);
		coefficients.put("event_turku_Gene_expression", 0.6440);
		coefficients.put("event_turku_Localization", 0.7168);
		coefficients.put("event_turku_Negative_regulation", 0.4078);
		coefficients.put("event_turku_Phosphorylation", 0.7777);
		coefficients.put("event_turku_Positive_regulation", 0.4798);
		coefficients.put("event_turku_Protein_catabolism", 0.8461);
		coefficients.put("event_turku_Regulation", 0.4317);
		coefficients.put("event_turku_Transcription", 0.6593);

		coefficients.put("event_tokyo_Binding", 0.3422);
		coefficients.put("event_tokyo_Gene_expression", 0.5759);
		coefficients.put("event_tokyo_Localization", 0.6739);
		coefficients.put("event_tokyo_Negative_regulation", 0.4126);
		coefficients.put("event_tokyo_Phosphorylation", 0.6025);
		coefficients.put("event_tokyo_Positive_regulation", 0.4457);
		coefficients.put("event_tokyo_Protein_catabolism", 0.6285);
		coefficients.put("event_tokyo_Regulation", 0.2592);
		coefficients.put("event_tokyo_Transcription", 0.5094);

		coefficients.put("event_intersection_Binding", 0.4602);
		coefficients.put("event_intersection_Gene_expression", 0.7424);
		coefficients.put("event_intersection_Localization", 0.7681);
		coefficients.put("event_intersection_Negative_regulation", 0.5600);
		coefficients.put("event_intersection_Phosphorylation", 0.7857);
		coefficients.put("event_intersection_Positive_regulation", 0.6644);
		coefficients.put("event_intersection_Protein_catabolism", 0.9523);
		coefficients.put("event_intersection_Regulation", 0.5890);
		coefficients.put("event_intersection_Transcription", 0.6857);

		coefficients.put("gene_gnat", 0.798);
		coefficients.put("gene_genetukit", 0.722);
		coefficients.put("gene_intersection", 0.828);

		//TODO normalization
		coefficients.put("inferred_23", 0.44);
		coefficients.put("inferred_31", 0.34);

		//normalization of event and gene confidences
		Set<String> keys = coefficients.keySet();
		for (String k : keys){
			if (k.startsWith("event_")){
				coefficients.put(k, coefficients.get(k) / 0.9523);
			} else if (k.startsWith("gene_")) {
				coefficients.put(k, coefficients.get(k) / 0.7147); //normalize by gene union precision, since that is used for events (and thus assumed for the event type confidences)
			}
		}
		
		return coefficients;
	}

	private static void checkOffsets(Map<String, String> item, Map<String, String> data) {
		String docText = data.get("doc_text");
		String sentence = item.get("sentence");

		if (sentence != null){
			if (!docText.substring(Integer.parseInt(item.get("sentence_offset"))).startsWith(sentence)){
				//			if (docText.indexOf(sentence) != Integer.parseInt(item.get("sentence_offset"))){
				throw new IllegalStateException("Sentence offset is wrong " + data.get("doc_id"));
			}
		}

		int triggerStart = Integer.parseInt(item.get("trigger_start"));
		int triggerEnd = Integer.parseInt(item.get("trigger_end"));

		if (!(triggerEnd <= docText.length() && docText.substring(triggerStart, triggerEnd).equals(item.get("trigger_text")))){
			throw new IllegalStateException("Event trigger offsets are wrong " + data.get("doc_id"));
		}
	}

	private void disambiguateEntities(List<Map<String, String>> entities) {
		for (Map<String,String> m : entities){
			String id = m.get("entity_id");
			if (id.contains("|")){
				id = id.substring(0,id.indexOf('|'));
			}			
			if (id.contains("?")){
				id = id.substring(0,id.indexOf('?'));
			}
			m.put("entity_id", id);
		}		
	}

	private void setEventData(Map<String, String> item,
			Map<String, String> event, String prefix, List<Map<String, String>> negspecs, List<Map<String, String>> anatomyEntities, List<Map<String, String>> genes, List<Map<String, String>> events, String docid) {

		if (prefix.length() > 0)
			item.put(prefix + "is_event", "1");

		for (String k : basicColumns){
			if (k.startsWith("@"))
				k = k.substring(1);
			item.put(prefix + k, event.get(k));
		}

		Map<String, String> ns = Misc.getByID(negspecs, event.get("id"), "event_id");
		if (ns != null){
			for (String k : negspecColumns){
				if (k.startsWith("@"))
					k = k.substring(1);
				item.put(prefix + k, ns.get(k));
			}
		}

		if (event.get("anatomy_id") != null ){
			Map<String, String> anatomyEntityMap = Misc.getByID(anatomyEntities, event.get("anatomy_id"), "id");
			if (anatomyEntityMap != null){
				item.put(prefix + "anatomy_entity_id", anatomyEntityMap.get("entity_id"));
				item.put(prefix + "anatomy_entity_start", anatomyEntityMap.get("entity_start"));
				item.put(prefix + "anatomy_entity_end", anatomyEntityMap.get("entity_end"));
				item.put(prefix + "anatomy_entity_term", anatomyEntityMap.get("entity_term"));
			}
		}

		String type = event.get("type");

		if (SimpleEvent.EVENT_TYPES.contains(type)){
			item.put(prefix + "t_is_event", "0");
			String geneID = event.get("participants").substring(2);
			Map<String,String> gene = Misc.getByID(genes, geneID);
			String prefix2 = prefix.length() == 0 ? "t_t_" : prefix + "t_";
			setGeneData(item, gene, prefix2);
		}

		if (BindingEvent.EVENT_TYPES.contains(type)){

			List<Map<String,String>> participants = new ArrayList<Map<String,String>>();
			if (participants.size() <= 2){
				for (String tc : event.get("participants").split("\\|")){
					for (String p : tc.split(",")){
						if (p.length() > 0){
							participants.add(Misc.getByID(genes, p.substring(1)));
						}
					}
				}
				if (participants.size() >= 1){
					item.put(prefix + "c_is_event", "0");
					String prefix2 = prefix.length() == 0 ? "c_c_" : prefix + "c_";
					setGeneData(item, participants.get(0), prefix2);
				}
				if (participants.size() >= 2){
					item.put(prefix + "t_is_event", "0");
					String prefix2 = prefix.length() == 0 ? "t_t_" : prefix + "t_";
					setGeneData(item, participants.get(1), prefix2);
				}				
			}
		}

		if (RegulationEvent.EVENT_TYPES.contains(type)){
			String c = event.get("participants").split("\\|")[0];
			String t = event.get("participants").split("\\|")[1];

			if (c.length() > 0 && c.startsWith("T")){
				String prefix2 = prefix.length() == 0 ? "c_c_" : prefix + "c_";

				if (prefix.length() == 0){
					item.put("c_is_event","0");
				}

				setGeneData(item, Misc.getByID(genes, c.substring(1)), prefix2);
			}
			if (t.length() > 0 && t.startsWith("T")){
				String prefix2 = prefix.length() == 0 ? "t_t_" : prefix + "t_";

				if (prefix.length() == 0){
					item.put("t_is_event","0");
				}

				setGeneData(item, Misc.getByID(genes, t.substring(1)), prefix2);
			}
			if (c.length() > 0 && c.startsWith("E")){
				setEventData(item, Misc.getByID(events, c), prefix + "c_", negspecs, anatomyEntities, genes, events, docid);
			}
			if (t.length() > 0 && t.startsWith("E")){
				setEventData(item, Misc.getByID(events, t), prefix + "t_", negspecs, anatomyEntities, genes, events, docid);
			}
		}
	}

	private void setGeneData(Map<String, String> item, Map<String, String> gene, String prefix) {
		for (String k : geneColumns){
			if (k.startsWith("@")){
				k = k.substring(1);
			}
			item.put(prefix + k, gene.get(k));
		}
	}

	private void setSentenceData(Map<String, String> event,
			Map<String, String> item, List<Pair<Integer>> sentenceSplits,
			String text) {

		int triggerStart = Integer.parseInt(event.get("trigger_start"));
		int triggerEnd = Integer.parseInt(event.get("trigger_end"));

		for (Pair<Integer> p : sentenceSplits){
			if (triggerStart >= p.getX() && triggerEnd <= p.getY()){
				item.put("sentence", text.substring(p.getX(), p.getY()));
				item.put("sentence_offset", ""+p.getX());
				return;
			}			
		}
	}
}