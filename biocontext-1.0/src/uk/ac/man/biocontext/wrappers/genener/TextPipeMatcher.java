package uk.ac.man.biocontext.wrappers.genener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import uk.ac.man.documentparser.dataholders.Document;
import uk.ac.man.entitytagger.Mention;
import uk.ac.man.entitytagger.matching.Matcher;
import uk.ac.man.textpipe.Annotator;

public class TextPipeMatcher extends Matcher {
	private List<Map<String, String>> storedData;
	private Annotator annotator;

	public TextPipeMatcher(List<Map<String,String>> data){
		this.storedData = data;
	}

	public TextPipeMatcher(Annotator a){
		this.annotator = a;
	}

	@Override
	public List<Mention> match(String text, Document doc) {
		List<Map<String, String>> data = (storedData == null && annotator != null) ? annotator.processDoc(doc) : storedData;
		
		List<Mention> mentions = new ArrayList<Mention>();

		for (Map<String,String> d : data){
			Mention m = new Mention(d.get("entity_id"),Integer.parseInt(d.get("entity_start")), Integer.parseInt(d.get("entity_end")), d.get("entity_term"));
			if (d.get("confidence") != null)
				m.setProbabilities(new Double[]{Double.parseDouble(d.get("confidence"))});
			m.setDocid(d.get("doc_id"));
			mentions.add(m);
		}

		return mentions;
	}		
}
