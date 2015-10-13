package uk.ac.man.biocontext.util.annotators;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import martin.common.ArgParser;

import uk.ac.man.textpipe.Annotator;
import uk.ac.man.textpipe.annotators.PrecomputedAnnotator;

/**
 * An annotator for returning the difference between two other annotators.
 * @author Martin
 *
 */
public class DifferenceAnnotator extends Annotator{

	private Annotator a;
	private Annotator b;
	private String[] criticalFields;

	@Override
	public void init(Map<String, String> data) {
		String[] g = new String[]{"id","entity_id","entity_start","entity_end","entity_term","entity_group"};

		this.a = new PrecomputedAnnotator(g, "farzin", "gold_bionlp_entities", ArgParser.getParser());
		this.b = new PrecomputedAnnotator(g, "farzin", "gold_bionlp_org_entities", ArgParser.getParser());
		this.criticalFields = new String[]{"entity_id","entity_start","entity_end","entity_term","entity_group"};
	}

	@Override
	public String[] getOutputFields() {
		return a.getOutputFields();
	}

	@Override
	public List<Map<String, String>> process(Map<String, String> data) {
		List<Map<String,String>> aRes = a.process(data);
		List<Map<String,String>> bRes = b.process(data);

		Set<Map<String,String>> bResSet = new HashSet<Map<String,String>>();
		bResSet.addAll(bRes);

		List<Map<String,String>> res = new ArrayList<Map<String,String>>();

		Set<String> added = new HashSet<String>();

		for (Map<String,String> m : aRes)
			if (!bResSet.contains(m)){
				if (!added.contains(get(m,this.criticalFields))){
					added.add(get(m,this.criticalFields));
					res.add(m);
				}
			}

		return res;
	}

	private String get(Map<String, String> m, String[] criticalFields) {
		String s = "";
		for (String c : criticalFields)
			s += m.get(c) + "\t";
		return s;
	}
}
