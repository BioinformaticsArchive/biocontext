package uk.ac.man.biocontext.wrappers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import martin.common.ComparableTuple;

import uk.ac.man.biocontext.wrappers.genener.TextPipeMatcher;
import uk.ac.man.entitytagger.Mention;
import uk.ac.man.entitytagger.generate.GenerateMatchers;
import uk.ac.man.entitytagger.matching.Matcher;
import uk.ac.man.entitytagger.matching.matchers.UnionMatcher;
import uk.ac.man.textpipe.Annotator;
import uk.ac.man.textpipe.TextPipe;
import uk.ac.man.textpipe.TextPipeClient;
import uk.ac.man.textpipe.TextPipe.VerbosityLevel;

public class EntityParserWrapper extends Annotator {
	private Matcher nerTools;
	private Annotator parser;
	private String[] parserKeys;

	@Override
	public void init(Map<String, String> data) {
		String[] nerHosts = data.get("nerHosts").split("\\|");
		String[] nerPorts = data.get("nerPorts").split("\\|");

		String[] nerTables = data.containsKey("nerTables") ? data.get("nerTables").split("\\|") : null;

		List<Matcher> matchers = new ArrayList<Matcher>();

		for (int i = 0; i < nerHosts.length; i++)
			if (nerTables == null)
				matchers.add(new TextPipeMatcher(new TextPipeClient(nerHosts[i], Integer.parseInt(nerPorts[i]))));
			else
				matchers.add(new TextPipeMatcher(TextPipeClient.get(null, null, nerHosts[i], Integer.parseInt(nerPorts[i]), "farzin", nerTables[i])));

		nerTools = new UnionMatcher(matchers, false);

		this.parserKeys = data.get("parserKey").split("\\|");

		if (data.containsKey("parserHost"))
			this.parser = new TextPipeClient(data.get("parserHost"),Integer.parseInt(data.get("parserPort")));
		else if (data.containsKey("parserAnnotator"))
			this.parser = TextPipe.getAnnotator(data.get("parserAnnotator"), data, null, null, null);
		else
			throw new IllegalStateException("When using EntityParserWrapper, you need to specify a parser: either using parserHost and parserPort, or parserAnnotator (with suitable initializatino parameters)");
	}

	@Override
	public String[] getOutputFields() {
		return parser.getOutputFields();
	}

	@Override
	public List<Map<String, String>> process(Map<String, String> data) {
		//		String doc_id = data.remove("doc_id");
		String doc_id = data.get("doc_id");
		
		if (TextPipe.verbosity.compareTo(VerbosityLevel.DEBUG) >= 0)
			System.out.println(doc_id);

		List<Mention> mentions = nerTools.match(data.get("doc_text"), data.get("doc_id"));

		StringBuffer sb = new StringBuffer(data.get("doc_text"));

		TreeSet<ComparableTuple<Integer,Integer>> e2 = new TreeSet<ComparableTuple<Integer,Integer>>();

		for (int i = 0; i < mentions.size(); i++)
			e2.add(new ComparableTuple<Integer, Integer>(-mentions.get(i).getStart(),i));

		int i = 0;

		Map<String,String> replacements = new HashMap<String,String>();

		for (ComparableTuple<Integer, Integer> ct : e2){
			Mention e = mentions.get(ct.getB());

			String newTerm = "Entity" + i++ + "x";
			String oldTerm = e.getText();

			if (parserKeys[0].startsWith("mcc"))
				oldTerm = oldTerm.replace("(", "-LRB-").replace(")", "-RRB-").replace("[", "-LSB-")
				.replace("]", "-RSB-").replace("{", "-LCB-").replace("}", "-RCB-");


			sb.replace(e.getStart(), e.getEnd(), newTerm);

			if (e.getText() == null)
				throw new IllegalStateException("No entity term? doc_id = " + doc_id + ", start: " + e.getStart());

			replacements.put(newTerm,oldTerm);
		}

		String text = sb.toString();

		for (String newTerm : replacements.keySet())
			if (text.contains(replacements.get(newTerm))){
				text = text.replaceAll("\\b" + GenerateMatchers.escapeRegexp(replacements.get(newTerm)) + "\\b", newTerm);
			}

		data.put("doc_text", text);
		List<Map<String,String>> parseRes = parser.process(data);

		if (parseRes == null)
			return new ArrayList<Map<String,String>>();

		for (Map<String,String> m : parseRes){
			for (String parserKey : parserKeys){
				String t = m.get(parserKey);

				if (t != null){
					if (t.contains("Entity"))
						for (String k : replacements.keySet())
							t = t.replace(k, replacements.get(k));

					m.put(parserKey,t);
				}
			}
		}

		//		if (doc_id != null)
		//			data.put("doc_id", doc_id);

		return parseRes;
	}

	@Override
	public void destroy() {
		//		for (Annotator a : this.nerTools)
		//			a.destroy();
		this.parser.destroy();
	}

	@Override
	public boolean isConcurrencySafe() {
		return true;
	}
}