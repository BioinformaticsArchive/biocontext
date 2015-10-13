package uk.ac.man.biocontext.wrappers;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import martin.common.ArgParser;
import martin.common.Pair;

import uk.ac.man.biocontext.dataholders.Event;
import uk.ac.man.biocontext.evaluate.EvaluateResult;
import uk.ac.man.biocontext.evaluate.Evaluate.Approx;
import uk.ac.man.biocontext.evaluate.Evaluate.Type;
import uk.ac.man.biocontext.evaluate.EvaluateResult.EvalType;
import uk.ac.man.biocontext.tools.anatomy.Anatomy;
import uk.ac.man.biocontext.tools.negmole.NegmoleBuild;
import uk.ac.man.biocontext.tools.negmole.NegmoleFeaturedEvent;
import uk.ac.man.biocontext.tools.negmole.Features.NegOrSpec;
import uk.ac.man.biocontext.tools.negmole.svmWrapper.SvmPerfWrapper;
import uk.ac.man.biocontext.util.Misc;
import uk.ac.man.biocontext.wrappers.events.EventCombiner;
import uk.ac.man.biocontext.wrappers.genener.GeneCombiner;
import uk.ac.man.documentparser.dataholders.Document;
import uk.ac.man.textpipe.Annotator;
import uk.ac.man.textpipe.TextPipe;
import uk.ac.man.textpipe.annotators.PrecomputedAnnotator;

public class NegmoleWrapper extends Annotator{
	private Annotator ptcMcClosky, ptcGnat, ptcEvents;
	private File modelDir;
	private File perfPath;
	public static final String[] outputFields = new String[]{"event_id", "negated", "speculated", "n_cue", "n_cue_start", "n_cue_end", "s_cue", "s_cue_start", "s_cue_end"};

	public void init(java.util.Map<String,String> data){
		this.ptcEvents = new PrecomputedAnnotator(Anatomy.outputFields, "farzin", "data_anatomy", ArgParser.getParser(), false);
		this.ptcGnat = new PrecomputedAnnotator(GeneCombiner.outputFields, "farzin", "data_genes", ArgParser.getParser(), false);
		this.ptcMcClosky = new PrecomputedAnnotator(MccloskyWrapper.outputFields, "farzin", "data_p_mcc", ArgParser.getParser(), false);
		
		modelDir = (data != null && data.containsKey("perfModelDir")) ? new File(data.get("perfModelDir")) : new File("/tmp/farzin-models/");
		perfPath = (data != null && data.containsKey("perfPath")) ? new File(data.get("perfPath")) : new File("svm_perf/");

		System.out.println("Using " + modelDir + " and " + perfPath);
	}

	@Override
	public String[] getOutputFields() {
		return outputFields;
	}

	@Override
	public List<Map<String, String>> process(Map<String, String> data) {
		Document d = Misc.toDoc(data);
		List<Map<String, String>> res = new ArrayList<Map<String, String>>();

		if (TextPipe.verbosity.compareTo(TextPipe.VerbosityLevel.DEBUG) >= 0)
			System.out.println(data.get("doc_id"));
		//		System.out.println(data.get("doc_text"));

		try{
			List<Event> events = NegmoleBuild.processDocument(d, ptcGnat, ptcMcClosky, ptcEvents, null);

			if (events.size() == 0)
				return new ArrayList<Map<String,String>>();

			List<NegmoleFeaturedEvent> negFeaturedEvents = NegmoleFeaturedEvent.getNegmoleFeaturedEventList(events, NegOrSpec.N);
			List<NegmoleFeaturedEvent> specFeaturedEvents = NegmoleFeaturedEvent.getNegmoleFeaturedEventList(events, NegOrSpec.S);

			if (negFeaturedEvents.size() != specFeaturedEvents.size()){
				throw new IllegalStateException("The sizes of neg event list and spec event list are different!");
			}

			SvmPerfWrapper spwn = new SvmPerfWrapper(negFeaturedEvents, NegOrSpec.N, perfPath, modelDir);
			spwn.test(null);

			SvmPerfWrapper spws = new SvmPerfWrapper(specFeaturedEvents, NegOrSpec.S, perfPath, modelDir);
			spws.test(null);

			for (int c = 0; c < negFeaturedEvents.size(); c++){
				NegmoleFeaturedEvent nfe = negFeaturedEvents.get(c);
				NegmoleFeaturedEvent sfe = specFeaturedEvents.get(c);

				Map<String, String> m = new HashMap<String, String>();
				m.put("event_id", nfe.getEvent().getId());

				if (nfe.getEvent().getNegated()){
					if (nfe.getMainCue() != null){
						m.put("n_cue", nfe.getMainCue().getData().get("text"));
						Pair<Integer> nIndex = nfe.getMainCueOffset(data.get("doc_text"));
						m.put("n_cue_start", nIndex.getX().toString());
						m.put("n_cue_end", nIndex.getY().toString());
					}

					m.put("negated", "1");
				} else {
					m.put("negated", "0");
				}

				if (sfe.getEvent().getSpeculated()){
					if (sfe.getMainCue() != null){
						m.put("s_cue", sfe.getMainCue().getData().get("text"));
						Pair<Integer> sIndex = sfe.getMainCueOffset(data.get("doc_text"));
						m.put("s_cue_start", sIndex.getX().toString());
						m.put("s_cue_end", sIndex.getY().toString());
					}

					m.put("speculated", "1");					
				} else {
					m.put("speculated", "0");
				}


				res.add(m);
			}			
		} catch(IllegalStateException e){
			System.err.println("Error\t" + data.get("doc_id") + "\t" + e.toString());
			return res;
		}

		catch(Exception e){
			e.printStackTrace();
			return res;
		}

		//TODO compare res to events.size, add events that might be missing.
		
		return res;
	}

/*	private static EvaluateResult evalSearch(Map<String,String> entry, List<Map<String,String>> list, String eventID, String eventType, String eventTrigger, boolean neg){
		EvalType type = EvalType.FP;

		for (Map<String,String> g : list){
			if (g.get(neg ? "negated" : "speculated").equals("1") && g.get("event_id").equals(eventID)){
				type = EvalType.TP;
				break;
			}
		}

		String st = entry.get((neg ? "n" : "s") + "_cue_start");
		String en = entry.get((neg ? "n" : "s") + "_cue_end");

		EvaluateResult e = new EvaluateResult(type, entry, st, en);

		e.getInfo().put("et", eventType);
		e.getInfo().put("etr", eventTrigger);
		e.getInfo().put("type", neg ? "neg" : "spec");
		e.getInfo().put("cue", entry.get(neg ? "n_cue" : "s_cue"));

		return e;
	}*/

	private static boolean equals(Map<String,String> e1, Map<String,String> e2, List<Map<String,String>> e1genes, List<Map<String,String>> e2genes, List<Map<String,String>> e1events, List<Map<String,String>> e2events, Approx a, Type t){
		if (!EventCombiner.matches(e1,e2,e1genes,e2genes, e1events, e2events, a, t))
			return false;

		boolean n1 = e1.containsKey("negated") ? e1.get("negated").equals("1") : false;
		boolean n2 = e2.containsKey("negated") ? e2.get("negated").equals("1") : false;
		boolean s1 = e1.containsKey("speculated") ? e1.get("speculated").equals("1") : false;
		boolean s2 = e2.containsKey("speculated") ? e2.get("speculated").equals("1") : false;

		if (t.toString().contains("BEFORE")){
			n1=false;
			s1=false;
		}
		
		return n1==n2 && s1==s2;
	}

	public static List<EvaluateResult> evaluate(List<Map<String, String>> predEvents, List<Map<String, String>> predGenes, List<Map<String, String>> goldEvents, List<Map<String, String>> goldGenes, Approx a, Type t) {
		List<EvaluateResult> res = new ArrayList<EvaluateResult>();

		for (Map<String,String> p : predEvents){
			if (!p.containsKey("invalid")){
				EvalType type = EvalType.FP;

				for (Map<String,String> g : goldEvents){
					if (!g.containsKey("invalid") && equals(p,g,predGenes,goldGenes,predEvents,goldEvents,a,t)){
						type = EvalType.TP;
						break;
					}
				}

				EvaluateResult e = new EvaluateResult(type, p, p.get("trigger_start"), p.get("trigger_end"));
				e.getInfo().put("id", p.get("id"));
				e.getInfo().put("type", p.get("type"));
				e.getInfo().put("participants", p.get("participants"));
				res.add(e);
			}
		}

		for (Map<String,String> g : goldEvents){
			if (!g.containsKey("invalid")){

				EvalType type = EvalType.FN;

				for (Map<String,String> p : predEvents){
					if (!p.containsKey("invalid") && equals(p,g,predGenes,goldGenes,predEvents,goldEvents,a,t)){
						type = EvalType.TP;
						break;
					}
				}

				if (type == EvalType.FN){
					EvaluateResult e = new EvaluateResult(type, g, g.get("trigger_start"), g.get("trigger_end"));
					e.getInfo().put("id", g.get("id"));
					e.getInfo().put("type", g.get("type"));
					e.getInfo().put("participants", g.get("participants"));
					res.add(e);
				}
			}
		}

		return res;
	}
}