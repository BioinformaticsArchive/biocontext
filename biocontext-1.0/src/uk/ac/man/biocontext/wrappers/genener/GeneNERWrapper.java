package uk.ac.man.biocontext.wrappers.genener;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uk.ac.man.biocontext.evaluate.Evaluate;
import uk.ac.man.biocontext.evaluate.EvaluateResult;
import uk.ac.man.biocontext.evaluate.Evaluate.Approx;
import uk.ac.man.biocontext.evaluate.EvaluateResult.EvalType;
import uk.ac.man.entitytagger.Mention;
import uk.ac.man.entitytagger.matching.Matcher;
import uk.ac.man.entitytagger.matching.matchers.IntersectionMatcher;
import uk.ac.man.entitytagger.matching.matchers.UnionMatcher;
import uk.ac.man.entitytagger.networking.SimpleClientMatcher;
import uk.ac.man.textpipe.Annotator;
import uk.ac.man.textpipe.TextPipe;
import uk.ac.man.textpipe.TextPipe.VerbosityLevel;

public class GeneNERWrapper extends Annotator{

	private Matcher matcher = null;

	@Override
	public void init(Map<String, String> data) {
		super.init(data);

		List<Matcher> matchers = new ArrayList<Matcher>();

		Matcher gnatMatcher=null,bannerMatcher=null,LINNAEUSMatcher=null;

		if (data.containsKey("gnatDir") && data.containsKey("gnatProps")){
			System.out.println("Loading GNAT...");
			gnatMatcher = new GNATMatcher(new File(data.get("gnatDir")), new File(data.get("gnatProps")));
			System.out.println("Done, GNAT loaded.");
		}

//		if (data != null && data.containsKey("bannerModel") && data.containsKey("bannerProperties")){
//			System.out.println("Loading BANNER...");
//			bannerMatcher = new BannerMatcher(new File(data.get("bannerProperties")), new File(data.get("bannerModel")), null);
//			System.out.println("Done, BANNER loaded.");
//		}
//
//		if (data != null && data.containsKey("linnaeusHost") && data.containsKey("linnaeusPort")){
//			System.out.println("Enabling LINNAEUS GENE usage.");
//			LINNAEUSMatcher = new SimpleClientMatcher(data.get("linnaeusHost") + ":" + data.get("linnaeusPort"));
//		}


		if (gnatMatcher != null && bannerMatcher != null && LINNAEUSMatcher != null){
			List<Matcher> l = new ArrayList<Matcher>();
			l.add(gnatMatcher);
			l.add(LINNAEUSMatcher);
			Matcher m = new UnionMatcher(l, false);
			l = new ArrayList<Matcher>();
			l.add(m);
			l.add(bannerMatcher);
			matchers.add(new IntersectionMatcher(l));			
		} else if (gnatMatcher != null && bannerMatcher != null){
			List<Matcher> l = new ArrayList<Matcher>();
			l.add(gnatMatcher);
			l.add(bannerMatcher);
			matchers.add(new IntersectionMatcher(l));			
		} else if (LINNAEUSMatcher != null && bannerMatcher != null){
			List<Matcher> l = new ArrayList<Matcher>();
			l.add(LINNAEUSMatcher);
			l.add(bannerMatcher);
			matchers.add(new IntersectionMatcher(l));			
		} else if (gnatMatcher != null){
			matchers.add(gnatMatcher);
		}

		if (bannerMatcher != null)
			matchers.add(bannerMatcher);

		if (matchers.size() == 0)
			throw new IllegalStateException("You need to specify at least one gene NER tool!");
		else if (matchers.size() == 1)
			this.matcher = matchers.get(0);
		else
			this.matcher = new UnionMatcher(matchers, false);
	}

	@Override
	public String[] getOutputFields() {
		return new String[]{"id","entity_id","entity_start","entity_end","entity_term","entity_group"};
	}

	@Override
	public List<Map<String, String>> process(Map<String, String> data) {
		List<Map<String,String>> res = new ArrayList<Map<String,String>>();

		String docid = data.containsKey("doc_id") ? data.get("doc_id") : "none";
		if (TextPipe.verbosity.compareTo(VerbosityLevel.DEBUG) >= 0)
			System.out.println("Processing " + docid);

		List<Mention> mentions = matcher.match(data.get("doc_text"),docid);
		if (TextPipe.verbosity.compareTo(VerbosityLevel.DEBUG) >= 0)
			System.out.println("\tMatcher done");

		if (mentions == null)
			return res;

//		for (int i = 0; i < mentions.size(); i++)
//			if (!isOk(mentions.get(i)))
//				mentions.remove(i--);

		mentions = Matcher.combineMatches(mentions);
		Matcher.detectEnumerations(mentions, data.get("doc_text"));

		int c = 0;
		for (Mention m : mentions){
			Map<String,String> map = new HashMap<String, String>();

			if (m.getIdsToString().contains("?"))
				m.setIds(new String[]{m.getMostProbableID()});

			map.put("entity_id", m.getIdsToString().replace("gene:ncbi:", ""));
			map.put("entity_start", ""+m.getStart());
			map.put("entity_end", ""+m.getEnd());
			map.put("entity_term", ""+m.getText());

			String group = null;
			if (m.getComment().indexOf("group: ") != -1){
				int s = m.getComment().indexOf("group: ") + 7;
				group = m.getComment().substring(s,m.getComment().indexOf(",",s));
			}

			map.put("entity_group", group);
			map.put("id",""+c++);

			res.add(map);
		}

		return res;
	}

	private boolean isOk(Mention m) {
		if (m.getText().length() < 2)
			return false;
		if (m.getText().equals("Fig"))
			return false;
		if (m.getText().startsWith("Fig."))
			return false;
		return true;
	}

	public static boolean equalsIgnoreID(Map<String,String> m1, Map<String,String> m2, Approx a){
		int s1 = Integer.parseInt(m1.get("entity_start"));
		int e1 = Integer.parseInt(m1.get("entity_end"));
		int s2 = Integer.parseInt(m2.get("entity_start"));
		int e2 = Integer.parseInt(m2.get("entity_end"));
		return Evaluate.overlap(s1, e1, s2, e2, a);
	}

	public static List<EvaluateResult> evaluate (List<Map<String,String>> predicted, List<Map<String,String>> gold, Approx a){
		List<EvaluateResult> res = new ArrayList<EvaluateResult>();

		for (Map<String,String> p : predicted){
			if (!p.containsKey("invalid")){
				EvalType type = EvalType.FP;

				for (Map<String,String> g : gold){
					int ps = Integer.parseInt(p.get("entity_start"));
					int pe = Integer.parseInt(p.get("entity_end"));
					int gs = Integer.parseInt(g.get("entity_start"));
					int ge = Integer.parseInt(g.get("entity_end"));
					if (Evaluate.overlap(ps, pe, gs, ge, a))
						type = EvalType.TP;
				}
				EvaluateResult e = new EvaluateResult(type, p, p.get("entity_start"), p.get("entity_end"));
				e.getInfo().put("id", p.get("id"));
				e.getInfo().put("t", p.get("entity_term"));
				res.add(e);
			}
		}

		for (Map<String,String> g : gold){
			EvalType type = EvalType.FN;

			for (Map<String,String> p : predicted){
				if (!p.containsKey("invalid")){
					int ps = Integer.parseInt(p.get("entity_start"));
					int pe = Integer.parseInt(p.get("entity_end"));
					int gs = Integer.parseInt(g.get("entity_start"));
					int ge = Integer.parseInt(g.get("entity_end"));
					if (Evaluate.overlap(ps, pe, gs, ge, a))
						type = EvalType.TP;
				}
			}

			if (type == EvalType.FN){
				EvaluateResult e = new EvaluateResult(type, g, g.get("entity_start"), g.get("entity_end"));
				e.getInfo().put("id", g.get("id"));
				e.getInfo().put("t", g.get("entity_term"));
				res.add(e);
			}
		}

		return res;
	}
}