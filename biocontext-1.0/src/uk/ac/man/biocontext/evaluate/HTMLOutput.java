package uk.ac.man.biocontext.evaluate;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

import uk.ac.man.biocontext.tools.anatomy.Anatomy;
import uk.ac.man.biocontext.util.Misc;
import uk.ac.man.biocontext.wrappers.LinnaeusWrapper;
import uk.ac.man.biocontext.wrappers.events.EventCombiner;
import uk.ac.man.biocontext.wrappers.genener.GeneCombiner;
import uk.ac.man.documentparser.DocumentParser;
import uk.ac.man.documentparser.dataholders.Document;
import uk.ac.man.documentparser.input.DocumentIterator;
import uk.ac.man.textpipe.Annotator;
import uk.ac.man.textpipe.TextPipeClient;
import uk.ac.man.textpipe.annotators.PrecomputedAnnotator;
import martin.common.ArgParser;
import martin.common.Loggers;
import martin.common.Pair;

public class HTMLOutput {
	public static final String C_RED =  "#FF7777";
	public static final String C_BLUE = "#BBBBFF";
	public static final String C_GREEN = "#77FF77";
	public static final String C_PINK = "#FFAAAA";
	public static final String C_PURPLE = "#FF77FF";

	public static void main(String[] args){
		ArgParser ap = ArgParser.getParser(args);
		Logger logger = Loggers.getDefaultLogger(ap);
		DocumentIterator documents = DocumentParser.getDocuments(ap,logger);
		int report = ap.getInt("report",-1);

		//		Annotator genesClient = ap.containsKey("genes") ? TextPipeClient.get(null, "gene", "localhost", 59002, "farzin", "gold_bionlp_entities") : null;
		//		Annotator eventsClient = ap.containsKey("events") ? TextPipeClient.get(null, "event", "localhost", 59020, "farzin", "gold_bionlp_events") : null;
		//		Annotator negSpecClient = ap.containsKey("negspec") ? TextPipeClient.get(null, "negspec", "localhost", 59030, "farzin", "temp") : null;

		//		Annotator genesClient = TextPipeClient.get(null, null, "localhost", 59002);
		//		Annotator eventsClient = TextPipeClient.get(null, null, "localhost", 59020);
		//		Annotator negSpecClient = TextPipeClient.get(null, null, "localhost", 57030);


		Annotator genesClient = ap.containsKey("genes") ? new PrecomputedAnnotator(GeneCombiner.outputFields, "farzin", "data_genes", ap, false) : null;
		Annotator eventsClient = ap.containsKey("events") ? new PrecomputedAnnotator(EventCombiner.outputFields, "farzin", "data_events", ap) : null;
		//		Annotator eventsClient = ap.containsKey("events") ? TextPipeClient.get(null, "event", "db001", 57023, "farzin", "data_events") : null;
		//		Annotator genesClient = ap.containsKey("genes") ? TextPipeClient.get(null, "gene", "db001", 59004, "farzin", "gold_bionlp_entities") : null;
		//		Annotator eventsClient = ap.containsKey("events") ? TextPipeClient.get(null, "event", "db001", 59023, "farzin", "gold_bionlp_events") : null;
		Annotator negSpecClient = ap.containsKey("negspec") ? TextPipeClient.get(null, "negspec", "db001", 57030, "farzin", "data_negmole") : null;

		Annotator anatomyClient = ap.containsKey("anatomy") ? new PrecomputedAnnotator(LinnaeusWrapper.outputFields, "farzin", "data_l_anatomy", ap) : null;
		Annotator anatomyAssClient = ap.containsKey("anatomy") ? new PrecomputedAnnotator(Anatomy.outputFields, "farzin", "data_anatomy", ap) : null;

		boolean filterGenes = ap.containsKey("filterGenes");
		boolean filterEvents = ap.containsKey("filterEvents");
		boolean filterAnatomy = ap.containsKey("filterAnatomy");

		if (ap.containsKey("outHTML")){
			if (ap.containsKey("gold")){
				genesClient = new PrecomputedAnnotator(new String[]{"id","entity_id","entity_start","entity_end","entity_term","entity_group"},"farzin","gold_bionlp_entities", ap);
				eventsClient = new PrecomputedAnnotator(new String[]{"id", "type", "trigger_start", "trigger_end", "trigger_text", "participants"},"farzin","gold_bionlp_events", ap);
				//				genesClient = new PrecomputedAnnotator(new String[]{"id","entity_id","entity_start","entity_end","entity_term","entity_group"},"farzin","gold_bionlp_entities_diff", ap);
				//				eventsClient = new PrecomputedAnnotator(new String[]{"id", "type", "trigger_start", "trigger_end", "trigger_text", "participants"},"farzin","gold_bionlp_events_diff", ap);

				toHTML(documents, ap.getFile("outHTML"), genesClient, eventsClient, null,null,null, report, false,false,false);
			} else {
				toHTML(documents, ap.getFile("outHTML"), genesClient, eventsClient, negSpecClient, anatomyClient, anatomyAssClient, report, filterGenes, filterEvents, filterAnatomy);
			}
		}
	}

	private static void toHTML(DocumentIterator documents, 
			File file, Annotator genesClient, Annotator eventsClient, Annotator negSpecClient, Annotator anatomyClient, Annotator anatomyAssClient, int report, boolean filterGenes, boolean filterEvents, boolean filterAnatomy) {

		int c = 0;

		try{
			BufferedWriter outStream = new BufferedWriter(new FileWriter(file));

			for (Document d : documents){
				List<Highlight> highlights = getHighlights(d, genesClient, eventsClient, negSpecClient, anatomyClient, anatomyAssClient, filterGenes, filterEvents, filterAnatomy);

				if (highlights.size() > 0){
					outStream.write("<b>" + d.getID() + "</b><br>");
					outStream.write(htmlify(d.toString(), highlights).replace("\n", "<br>"));
					outStream.write("<br><hr><br><br>");
					outStream.flush();
				}

				if (report != -1 && ++c % report == 0)
					System.out.println(c);
			}

			outStream.close();

		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}
	}

	public static String htmlify(String text, List<Highlight> highlights) {
		StringBuffer sb = new StringBuffer(text);

		Collections.sort(highlights);

		for (Highlight h : highlights){
			Pair<String> tags = getTags(h);
			sb = sb.insert(Math.min(Math.max(h.getEnd()  , 0), sb.length()-1), tags.getY());
			sb = sb.insert(Math.min(Math.max(h.getStart(), 0), sb.length()-1), tags.getX());
		}

		return sb.toString();
	}

	private static Pair<String> getTags(Highlight h) {
		String a = "<font style=\"background-color: " + h.getColor() + "\">";
		String b = " [" + h.getText() + "]</font>";
		return new Pair<String>(a,b);
	}

	private static List<Highlight> getHighlights(Document d,
			Annotator genesClient, Annotator eventsClient,
			Annotator negSpecClient, Annotator anatomyClient, Annotator anatomyAssClient, boolean filterGenes, boolean filterEvents, boolean filterAnatomy) {

		List<Map<String,String>> genes = genesClient != null ? genesClient.processDoc(d) : new ArrayList<Map<String,String>>();
		List<Map<String,String>> events = eventsClient != null ? eventsClient.processDoc(d) : new ArrayList<Map<String,String>>();
		List<Map<String,String>> negspec = negSpecClient != null ? negSpecClient.processDoc(d) : new ArrayList<Map<String,String>>();
		List<Map<String,String>> anatomy = anatomyClient != null ? anatomyClient.processDoc(d) : new ArrayList<Map<String,String>>();
		List<Map<String,String>> anatomyAss = anatomyAssClient != null ? anatomyAssClient.processDoc(d) : new ArrayList<Map<String,String>>();

		if (filterEvents && events != null && (anatomyAss != null || negspec != null))
			events = filterEvents(events, anatomyAss, negspec);
		if (filterGenes && genes != null && events != null && events.size() == 0)
			genes.clear();
		if (filterAnatomy && anatomy != null && events != null && events.size() == 0)
			anatomy.clear();

		List<Highlight> hs = new ArrayList<Highlight>();

		if (genesClient != null && genes.size() == 0)
			return hs;
		if (eventsClient != null && events.size() == 0)
			return hs;
		if (negSpecClient != null && negspec.size() == 0)
			return hs;
		if (anatomyClient != null && anatomy.size() == 0)
			return hs;
		if (anatomyAssClient != null && anatomyAss.size() == 0)
			return hs;

		for (Map<String,String> m : genes){
			int s = Integer.parseInt(m.get("entity_start"));
			int e = Integer.parseInt(m.get("entity_end"));

			String c = m.get("entity_id").equals("0") ? C_RED : C_BLUE;
			String t = m.get("id");

			String url = m.get("entity_id").equals("0") ? null : "gene:ncbi:" + m.get("entity_id");

			if (m.get("confidence") != null){
				t += ", c: " + m.get("confidence").substring(0,5);
				if (Double.parseDouble(m.get("confidence")) <= 0.01)
					c = C_RED;
			}

			hs.add(new Highlight(c, s, e, t, url));
		}

		List<Map<String,String>> eventsWithAnatomy = new ArrayList<Map<String,String>>();
		for (Map<String,String> m : events){
			if(m.get("inferred_23") != null)
				eventsWithAnatomy.add(m); 
		}


		if (eventsWithAnatomy.size() > 0){
			Map<String,String> e = eventsWithAnatomy.get(new Random().nextInt(eventsWithAnatomy.size()));
			Map<String,String> e2 = Misc.getByID(events, e.get("inferred_23"));
			eventsWithAnatomy.clear();
			eventsWithAnatomy.add(e);
			if (e2 != null) 
				eventsWithAnatomy.add(e2);
		}
		
//		if (eventsWithAnatomy.size() > 0){
//		Map<String,String> randomEvent =eventsWithAnatomy.get(new Random().nextInt(eventsWithAnatomy.size()));  

		for (Map<String,String> randomEvent : eventsWithAnatomy){
//		for (Map<String,String> randomEvent : events){
			int s = Integer.parseInt(randomEvent.get("trigger_start"));
			int e = Integer.parseInt(randomEvent.get("trigger_end"));

			String t = randomEvent.get("id") + ", " + randomEvent.get("type");

			String[] p = randomEvent.get("participants").split("\\|");
			if (p[0].length() > 0)
				t += ", C: " + p[0];
			if (p[1].length() > 0)
				t += ", T: " + p[1];

			if (negspec != null && negspec.size() > 0)
				for (Map<String,String> n : negspec)
					if (n.get("event_id").equals(randomEvent.get("id")))
						t += ", N: " + n.get("negated") + ", S: " + n.get("speculated"); 

			if (anatomyAss != null && anatomyAss.size() > 0)
				for (Map<String,String> a : anatomyAss)
					if (a.get("id").equals(randomEvent.get("id")) && a.get("anatomy_id") != null)
						t += ", A: " + a.get("anatomy_id"); 

			if(randomEvent.get("inferred_23") != null){
				t += ", inf23: " + randomEvent.get("inferred_23");
			}

			hs.add(new Highlight(C_GREEN, s, e, t, null));
		}

		for (Map<String,String> m : negspec){
			if (m.get("negated").equals("1") && m.get("n_cue_start") != null){
				int s = Integer.parseInt(m.get("n_cue_start"));
				int e = Integer.parseInt(m.get("n_cue_end"));

				String t = m.get("event_id");

				hs.add(new Highlight(C_BLUE, s, e, t, null));
			}

			if (m.get("speculated").equals("1") && m.get("s_cue_start") != null){
				int s = Integer.parseInt(m.get("s_cue_start"));
				int e = Integer.parseInt(m.get("s_cue_end"));

				String t = m.get("event_id");

				hs.add(new Highlight(C_PURPLE, s, e, t, null));
			}
		}

		for (Map<String,String> m : anatomy){
			int s = Integer.parseInt(m.get("entity_start"));
			int e = Integer.parseInt(m.get("entity_end"));

			String c = C_PINK;
			String t = m.get("id");

			hs.add(new Highlight(c, s, e, t, null));
		}

		Collections.sort(hs);

		hs = combineHighlights(hs);

		return hs;		
	}

	private static List<Map<String, String>> filterEvents(List<Map<String, String>> events,
			List<Map<String, String>> anatomyAss,
			List<Map<String, String>> negspec) {

		Set<String> enabled = new HashSet<String>();

		if (anatomyAss != null)
			for (Map<String,String> m : anatomyAss)
				enabled.add(m.get("id"));

		if (negspec != null)
			for (Map<String,String> m : negspec)
				if (m.get("negated").equals("1") || m.get("speculated").equals("1")) 
					enabled.add(m.get("event_id"));

		List<Map<String, String>> res = new ArrayList<Map<String, String>>();

		for (Map<String,String> m : events)
			if (enabled.contains(m.get("id")))
				res.add(m);

		return res;
	}

	public static List<Highlight> combineHighlights(List<Highlight> hs) {
		List<Highlight> res = new ArrayList<Highlight>();


		while (hs.size() > 0){
			if (hs.size() == 1){ 
				res.add(hs.remove(0));
			} else if (hs.get(0).overlaps(hs.get(1))){
				Highlight a = hs.remove(0);
				Highlight b = hs.remove(0);
				hs.add(0, combine(a,b));
			} else {
				res.add(hs.remove(0));
			}
		}

		return res;		
	}

	private static Highlight combine(Highlight a, Highlight b) {
		String c = "#CCFF33";
		int s = Math.min(a.getStart(), b.getStart());
		int e = Math.max(a.getEnd(), b.getEnd());
		String t = a.getText() + "; " + b.getText();
		return new Highlight(c, s, e, t, null);

	}
}